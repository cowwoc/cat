#!/usr/bin/env bash
# build-jlink.sh - Create a self-contained jlink image with hooks application and dependencies
#
# Pipeline:
#   1. Build the hooks JAR (if needed)
#   2. Stage runtime dependency JARs
#   3. Patch automatic modules with generated module-info.class
#   4. Create jlink runtime image with per-handler launchers
#   5. Generate Leyden AOT startup archive
#
# Usage:
#   ./build-jlink.sh
#
# Output:
#   target/jlink/ - Complete jlink runtime image with launchers

set -euo pipefail

# --- Configuration ---

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$SCRIPT_DIR"
readonly TARGET_DIR="${PROJECT_DIR}/target"
readonly STAGING_DIR="${TARGET_DIR}/jlink-staging"
readonly PATCH_DIR="${TARGET_DIR}/module-patches"
readonly OUTPUT_DIR="${TARGET_DIR}/jlink"
readonly HOOKS_JAR="${TARGET_DIR}/cat-hooks-2.1.jar"
readonly MODULE_NAME="io.github.cowwoc.cat.hooks"

# Handler registry: launcher-name:ClassName
# Each entry generates a bin/<launcher-name> script in the jlink image.
readonly -a HANDLERS=(
  "get-bash-output:GetBashOutput"
  "get-bash-post-output:GetBashPostOutput"
  "get-read-output:GetReadOutput"
  "get-read-post-output:GetReadPostOutput"
  "get-post-output:GetPostOutput"
  "get-skill-output:GetSkillOutput"
  "token-counter:TokenCounter"
  "enforce-status:EnforceStatusOutput"
  "get-ask-output:GetAskOutput"
  "get-edit-output:GetEditOutput"
  "get-write-edit-output:GetWriteEditOutput"
  "get-task-output:GetTaskOutput"
  "get-session-end-output:GetSessionEndOutput"
  "get-checkpoint-box:skills.GetCheckpointOutput"
  "get-issue-complete-box:skills.GetIssueCompleteOutput"
  "get-next-task-box:skills.GetNextTaskOutput"
  "get-status-output:skills.GetStatusOutput"
  "get-render-diff-output:skills.GetRenderDiffOutput"
  "session-analyzer:util.SessionAnalyzer"
)

# --- Logging ---

log() { echo "[build-jlink] $*"; }
error() { echo "[build-jlink] ERROR: $*" >&2; exit 1; }

# --- Helpers ---

# Fully qualified main class for a handler class name
handler_main() {
  echo "${MODULE_NAME}/${MODULE_NAME}.$1"
}

# Run a Java command against every handler, feeding '{}' on stdin.
# Usage: run_all_handlers <java_args...>
# The placeholder {} in the args is replaced with each handler's main class.
run_all_handlers() {
  local label="$1"; shift
  local java_bin="$1"; shift

  for handler in "${HANDLERS[@]}"; do
    local class_name="${handler##*:}"
    log "  ${label}: $class_name"
    echo '{}' | "$java_bin" "$@" -m "$(handler_main "$class_name")" 2>/dev/null || true
  done
}

# --- Phase 1: Build hooks JAR ---

ensure_hooks_jar() {
  if [[ -f "$HOOKS_JAR" ]]; then
    log "Hooks JAR already exists: $HOOKS_JAR"
    return 0
  fi

  log "Building hooks JAR..."
  cd "$PROJECT_DIR"
  ./mvnw package -DskipTests -q

  [[ -f "$HOOKS_JAR" ]] || error "Failed to build hooks JAR"
  log "Hooks JAR built successfully"
}

# --- Phase 2: Stage dependencies ---

stage_dependencies() {
  log "Staging runtime dependencies..."
  rm -rf "$STAGING_DIR"
  mkdir -p "$STAGING_DIR"

  cd "$PROJECT_DIR"
  ./mvnw dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="$STAGING_DIR" \
    -q

  log "Staged $(find "$STAGING_DIR" -name "*.jar" | wc -l) dependency JARs"
}

# --- Phase 3: Patch automatic modules ---
#
# jlink requires all JARs to be named modules (have module-info.class).
# Some dependencies are "automatic modules" — they have no module-info.
# For each automatic module: jdeps generates module-info.java, javac compiles it,
# and jar injects the resulting module-info.class back into the JAR.

is_automatic_module() {
  local jar="$1"

  # If JAR already contains module-info.class, it's a named module (not automatic)
  # Match module-info.class at any depth (root or META-INF/versions/N/ for multi-release JARs)
  if jar --list --file="$jar" 2>/dev/null | grep -q "module-info\.class"; then
    return 1
  fi

  local desc
  # --release=17 ensures multi-release JARs expose their module descriptor
  desc=$(jar --describe-module --file="$jar" --release=17 2>&1) || return 0
  # Real modules don't contain "automatic" in their descriptor
  echo "$desc" | grep -q "automatic"
}

patch_automatic_module() {
  local jar="$1"
  local jar_name
  jar_name="$(basename "$jar")"
  local temp_dir="${PATCH_DIR}/${jar_name%.jar}"

  log "Patching automatic module: $jar_name"
  mkdir -p "$temp_dir"

  # Ensure cleanup on any exit path
  trap "rm -rf '$temp_dir'" RETURN

  # Build module-path from other staged JARs (for dependency resolution)
  local module_path=""
  for dep_jar in "$STAGING_DIR"/*.jar; do
    [[ -f "$dep_jar" && "$dep_jar" != "$jar" ]] || continue
    if jar --describe-module --file="$dep_jar" --release=17 &>/dev/null; then
      [[ -n "$module_path" ]] && module_path+=":"
      module_path+="$dep_jar"
    fi
  done

  # Step 1: Generate module-info.java via jdeps
  local jdeps_args=("--generate-module-info" "$temp_dir" "--ignore-missing-deps")
  [[ -n "$module_path" ]] && jdeps_args+=("--module-path" "$module_path")
  jdeps_args+=("$jar")

  if ! jdeps "${jdeps_args[@]}" 2>/dev/null; then
    log "  Warning: jdeps failed for $jar_name"
    return 1
  fi

  # jdeps creates a subdirectory named after the module
  local module_dir
  module_dir=$(find "$temp_dir" -maxdepth 1 -type d ! -path "$temp_dir" | head -1)
  [[ -d "$module_dir" ]] || { log "  Warning: No module directory generated"; return 1; }

  local module_info_java
  module_info_java=$(find "$module_dir" -name "module-info.java" -type f | head -1)
  [[ -f "$module_info_java" ]] || { log "  Warning: No module-info.java generated"; return 1; }

  local module_name
  module_name=$(grep -E "^module " "$module_info_java" | sed 's/module //;s/ {//')
  [[ -n "$module_name" ]] || { log "  Warning: Could not extract module name"; return 1; }

  log "  Module name: $module_name"

  # Step 2: Compile module-info.java
  local classes_dir="${module_dir}/classes"
  mkdir -p "$classes_dir"

  local javac_args=("--patch-module" "$module_name=$jar" "-d" "$classes_dir" "$module_info_java")
  [[ -n "$module_path" ]] && javac_args=("--module-path" "$module_path" "${javac_args[@]}")

  if ! javac "${javac_args[@]}" 2>/dev/null; then
    log "  Warning: Failed to compile module-info for $jar_name"
    return 1
  fi

  # Step 3: Inject module-info.class into the JAR
  if ! jar --update --file="$jar" -C "$classes_dir" module-info.class; then
    log "  Warning: Failed to update JAR with module-info: $jar_name"
    return 1
  fi

  log "  Successfully patched $jar_name"
}

patch_automatic_modules() {
  log "Identifying and patching automatic modules..."
  rm -rf "$PATCH_DIR"
  mkdir -p "$PATCH_DIR"

  local patched=0 failed=0
  for jar in "$STAGING_DIR"/*.jar; do
    [[ -f "$jar" ]] || continue
    if is_automatic_module "$jar"; then
      if patch_automatic_module "$jar"; then
        ((patched++)) || true
      else
        ((failed++)) || true
      fi
    fi
  done

  log "Patched $patched automatic modules ($failed failed/skipped)"
}

# --- Phase 4: Build jlink image ---

build_jlink_image() {
  log "Building jlink image..."

  local module_path="${HOOKS_JAR}:${STAGING_DIR}"

  rm -rf "$OUTPUT_DIR"

  jlink \
    --module-path "$module_path" \
    --add-modules "$MODULE_NAME" \
    --output "$OUTPUT_DIR" \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --generate-cds-archive

  # Remove nocoops CDS archive (only needed for heaps >32GB)
  rm -f "${OUTPUT_DIR}/lib/server/classes_nocoops.jsa"

  log "jlink image created at: $OUTPUT_DIR"
}

# --- Phase 5: Generate startup optimization archives ---
#
# Leyden AOT cache with pre-linked classes and method profiles:
#   Eliminates class initialization overhead

generate_startup_archives() {
  local java_bin="${OUTPUT_DIR}/bin/java"
  local aot_config="${OUTPUT_DIR}/lib/server/aot-config.aotconf"
  local aot_cache="${OUTPUT_DIR}/lib/server/aot-cache.aot"

  # Leyden AOT: record training data, then create pre-linked cache.
  log "Generating Leyden AOT cache..."
  run_all_handlers "Recording AOT" "$java_bin" -XX:AOTMode=record -XX:AOTConfiguration="$aot_config"

  if [[ ! -f "$aot_config" ]]; then
    log "Warning: Failed to record AOT configuration"
    return 0
  fi

  if ! "$java_bin" \
    -XX:AOTMode=create \
    -XX:AOTConfiguration="$aot_config" \
    -XX:AOTCache="$aot_cache" \
    -XX:+AOTClassLinking \
    -m "$(handler_main GetBashOutput)" \
    2>/dev/null; then
    log "Warning: Failed to create AOT cache"
    return 0
  fi

  rm -f "$aot_config"
  log "  AOT cache: $(du -h "$aot_cache" | cut -f1)"
  log "Startup archives complete"
}

# --- Phase 6: Generate launcher scripts ---

generate_launchers() {
  log "Generating launcher scripts..."

  local bin_dir="${OUTPUT_DIR}/bin"
  local aot_cache="../lib/server/aot-cache.aot"

  for handler in "${HANDLERS[@]}"; do
    local name="${handler%%:*}"
    local class="${handler##*:}"
    local launcher="${bin_dir}/${name}"
    local main_class="$(handler_main "$class")"

    log "  Creating launcher: $name -> $main_class"

    cat > "$launcher" <<'EOF'
#!/bin/sh
DIR=`dirname $0`
exec "$DIR/java" \
  -Xms16m -Xmx96m \
  -Dstdin.encoding=UTF-8 \
  -Dstdout.encoding=UTF-8 \
  -Dstderr.encoding=UTF-8 \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -XX:AOTCache="$DIR/../lib/server/aot-cache.aot" \
  -m MODULE_CLASS "$@"
EOF

    # Replace MODULE_CLASS placeholder
    sed -i "s|MODULE_CLASS|$main_class|g" "$launcher"
    grep -q "$main_class" "$launcher" || error "Failed to generate launcher: $name"
    chmod +x "$launcher"
  done

  log "Generated ${#HANDLERS[@]} launcher scripts"
}

# --- Phase 7: Verify ---

verify_image() {
  log "Verifying jlink image..."

  if ! "${OUTPUT_DIR}/bin/java" -version &>/dev/null; then
    error "java -version failed"
  fi

  log "  Testing get-bash-output launcher..."
  if echo '{}' | "${OUTPUT_DIR}/bin/get-bash-output" &>/dev/null; then
    log "  get-bash-output launcher works"
  else
    log "  Warning: get-bash-output launcher test failed"
  fi

  log "Verification complete"
}

# --- Main ---

main() {
  log "Starting jlink build process..."

  ensure_hooks_jar
  stage_dependencies
  patch_automatic_modules
  build_jlink_image
  generate_startup_archives
  generate_launchers
  verify_image

  log "Build complete!"
  log "Output: $OUTPUT_DIR"
  log "Launchers:"
  for handler in "${HANDLERS[@]}"; do
    log "  - ${OUTPUT_DIR}/bin/${handler%%:*}"
  done
}

main "$@"
