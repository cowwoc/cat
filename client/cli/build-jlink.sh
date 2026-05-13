#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# build-jlink.sh - Create self-contained runtime-specific jlink images
#
# Pipeline:
#   1. Build the client JAR (if needed)
#   2. Stage runtime dependency JARs
#   3. Patch automatic modules with generated module-info.class
#   4. Create runtime-specific jlink images
#   5. Generate per-handler launcher scripts
#   6. Generate runtime-specific Leyden AOT startup archives
#   7. Verify jlink images
#
# Usage:
#   ./build-jlink.sh
#
# Output:
#   target/jlink/{claude,codex}/ - Complete runtime-specific jlink images with launchers

set -euo pipefail

# --- Configuration ---
# Note: variables used by generate_launchers (OUTPUT_DIR, MODULE_NAME, HANDLERS, ENABLE_ASSERTIONS)
# are intentionally not declared readonly so tests can source this file and override them.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
REACTOR_DIR="$(cd "${PROJECT_DIR}/.." && pwd)"
WORKSPACE_DIR="$(cd "${REACTOR_DIR}/.." && pwd)"
MVN="${REACTOR_DIR}/mvnw"
# Force Maven Wrapper to resolve .mvn from client/ even when invoked by external launchers.
export MAVEN_PROJECTBASEDIR="$REACTOR_DIR"
TARGET_DIR="${PROJECT_DIR}/target"
STAGING_DIR="${TARGET_DIR}/jlink-staging"
PATCH_DIR="${TARGET_DIR}/module-patches"
OUTPUT_ROOT="${TARGET_DIR}/jlink"
OUTPUT_DIR="${OUTPUT_ROOT}/claude"
CLIENT_JAR="${TARGET_DIR}/cat-cli-2.1.jar"
MODULE_NAME="io.github.cowwoc.cat.client"
ENABLE_ASSERTIONS=false
declare -a PATCH_MODULE_PATH_JARS=()
declare -a AUTOMATIC_MODULE_JARS=()

# Runtime-neutral handler registry: launcher-name:fully.qualified.ClassName
# Each entry is included in every runtime image.
declare -a COMMON_HANDLERS=(
  "token-counter:io.github.cowwoc.cat.claude.hook.TokenCounter"
  "get-checkpoint-box:io.github.cowwoc.cat.claude.hook.skills.GetCheckpointOutput"
  "get-issue-complete-box:io.github.cowwoc.cat.claude.hook.skills.GetIssueCompleteOutput"
  "get-next-issue-box:io.github.cowwoc.cat.claude.hook.skills.GetNextIssueOutput"
  "get-config-output:io.github.cowwoc.cat.claude.hook.skills.GetConfigOutput"
  "update-config:io.github.cowwoc.cat.claude.hook.util.UpdateConfig"
  "get-output:io.github.cowwoc.cat.claude.hook.skills.GetOutput"
  "get-status-output:io.github.cowwoc.cat.claude.hook.skills.GetStatusOutput"
  "get-cleanup-output:io.github.cowwoc.cat.claude.hook.skills.GetCleanupOutput"
  "create-issue:io.github.cowwoc.cat.claude.hook.util.IssueCreator"
  "session-analyzer:io.github.cowwoc.cat.claude.hook.util.SessionAnalyzer"
  "extract-investigation-context:io.github.cowwoc.cat.claude.hook.util.InvestigationContextExtractor"
  "grade-json-transformer:io.github.cowwoc.cat.claude.hook.util.GradeJsonTransformer"
  "progress-banner:io.github.cowwoc.cat.claude.hook.skills.ProgressBanner"
  "get-stakeholder-selection-box:io.github.cowwoc.cat.claude.hook.skills.GetStakeholderSelectionBox"
  "get-stakeholder-review-box:io.github.cowwoc.cat.claude.hook.skills.GetStakeholderReviewBox"
  "get-stakeholder-concern-box:io.github.cowwoc.cat.claude.hook.skills.GetStakeholderConcernBox"
  "verify-audit:io.github.cowwoc.cat.claude.hook.skills.VerifyAudit"
  "empirical-test-runner:io.github.cowwoc.cat.claude.hook.skills.EmpiricalTestRunner"
  "merge-and-cleanup:io.github.cowwoc.cat.claude.hook.util.MergeAndCleanup"
  "git-squash:io.github.cowwoc.cat.claude.hook.util.GitSquash"
  "git-merge-linear:io.github.cowwoc.cat.claude.hook.util.GitMergeLinear"
  "git-amend:io.github.cowwoc.cat.claude.hook.util.GitAmend"
  "git-rebase:io.github.cowwoc.cat.claude.hook.util.GitRebase"
  "work-prepare:io.github.cowwoc.cat.claude.hook.util.WorkPrepare"
  "issue-lock:io.github.cowwoc.cat.claude.hook.util.IssueLock"
  "check-existing-work:io.github.cowwoc.cat.claude.hook.util.ExistingWorkChecker"
  "wrap-markdown:io.github.cowwoc.cat.claude.hook.util.MarkdownWrapper"
  "batch-read:io.github.cowwoc.cat.claude.hook.util.BatchReader"
  "root-cause-analyzer:io.github.cowwoc.cat.claude.hook.util.RootCauseAnalyzer"
  "validate-status-alignment:io.github.cowwoc.cat.claude.hook.util.StatusAlignmentValidator"
  "feedback:io.github.cowwoc.cat.claude.hook.util.Feedback"
  "get-add-output:io.github.cowwoc.cat.claude.hook.skills.GetAddOutput"
  "record-learning:io.github.cowwoc.cat.claude.hook.util.RecordLearning"
  "write-session-marker:io.github.cowwoc.cat.claude.hook.util.WriteSessionMarker"
  "read-session-marker:io.github.cowwoc.cat.claude.hook.util.ReadSessionMarker"
  "auto-close-index:io.github.cowwoc.cat.claude.hook.util.AutoCloseIndexJson"
  "instruction-test-runner:io.github.cowwoc.cat.claude.hook.skills.InstructionTestRunner"
  "verify-defer-plan-generation:io.github.cowwoc.cat.claude.hook.util.VerifyDeferPlanGeneration"
  "write-and-commit:io.github.cowwoc.cat.claude.hook.util.WriteAndCommit"
  "extract-turns:io.github.cowwoc.cat.claude.hook.skills.ExtractTurnsContent"
  "update-skill-description:io.github.cowwoc.cat.claude.hook.skills.UpdateSkillDescription"
  "build-runtime-artifacts:io.github.cowwoc.cat.agent.PluginArtifactBuilder"
)

# Claude-only handlers.
declare -a CLAUDE_HANDLERS=(
  "claude-runner:io.github.cowwoc.cat.claude.hook.skills.ClaudeRunner"
  "register-hook:io.github.cowwoc.cat.claude.hook.util.HookRegistrar"
  "statusline-command:io.github.cowwoc.cat.claude.hook.util.StatuslineCommand"
  "statusline-install:io.github.cowwoc.cat.claude.hook.util.StatuslineInstall"
  "pre-bash:io.github.cowwoc.cat.claude.hook.PreToolUseHook"
  "post-bash:io.github.cowwoc.cat.claude.hook.PostBashHook"
  "pre-read:io.github.cowwoc.cat.claude.hook.PreReadHook"
  "post-read:io.github.cowwoc.cat.claude.hook.PostReadHook"
  "post-tool-use:io.github.cowwoc.cat.claude.hook.PostToolUseHook"
  "user-prompt-submit:io.github.cowwoc.cat.claude.hook.UserPromptSubmitHook"
  "enforce-status:io.github.cowwoc.cat.claude.hook.EnforceStatusOutput"
  "pre-ask:io.github.cowwoc.cat.claude.hook.PreAskHook"
  "pre-write:io.github.cowwoc.cat.claude.hook.PreWriteHook"
  "subagent-start:io.github.cowwoc.cat.claude.hook.SubagentStartHook"
  "pre-issue:io.github.cowwoc.cat.claude.hook.PreIssueHook"
  "session-end:io.github.cowwoc.cat.claude.hook.SessionEndHook"
  "post-tool-use-failure:io.github.cowwoc.cat.claude.hook.PostToolUseFailureHook"
)

# Codex-only handlers.
declare -a CODEX_HANDLERS=(
  "codex-runner:io.github.cowwoc.cat.codex.hook.skills.CodexRunner"
  "session-start:io.github.cowwoc.cat.codex.hook.SessionStartHook"
  "pre-bash:io.github.cowwoc.cat.codex.hook.PreBashHook"
)

# Active handler registry used by generate_launchers. Tests source this file and override HANDLERS.
declare -a HANDLERS=("${COMMON_HANDLERS[@]}" "${CLAUDE_HANDLERS[@]}")

# --- Logging ---

log() { echo "[build-jlink] $*"; }
error() { echo "[build-jlink] ERROR: $*" >&2; exit 1; }

# --- Helpers ---

# Fully qualified main class for a handler class name
handler_main() {
  echo "${MODULE_NAME}/$1"
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

# Selects the handler registry for one runtime image.
# Usage: set_runtime_handlers <claude|codex>
set_runtime_handlers() {
  local runtime="$1"
  HANDLERS=("${COMMON_HANDLERS[@]}")
  case "$runtime" in
    claude) HANDLERS+=("${CLAUDE_HANDLERS[@]}") ;;
    codex) HANDLERS+=("${CODEX_HANDLERS[@]}") ;;
    *) error "Unknown runtime: $runtime" ;;
  esac
}

# --- Phase 1: Build client JAR ---

ensure_client_jar() {
  log "Building client JAR..."
  "$MVN" -f "$PROJECT_DIR/pom.xml" package -DskipTests -q

  [[ -f "$CLIENT_JAR" ]] || error "Failed to build client JAR"
  log "Engine JAR built successfully"
}

# --- Phase 2: Stage dependencies ---

stage_dependencies() {
  log "Staging runtime dependencies..."
  rm -rf "$STAGING_DIR"
  mkdir -p "$STAGING_DIR"

  "$MVN" -f "$PROJECT_DIR/pom.xml" dependency:copy-dependencies \
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

stage_module_patch_inputs() {
  PATCH_MODULE_PATH_JARS=()
  AUTOMATIC_MODULE_JARS=()

  for jar in "$STAGING_DIR"/*.jar; do
    [[ -f "$jar" ]] || continue

    local desc=""
    local has_descriptor=false
    if desc=$(jar --describe-module --file="$jar" --release=17 2>&1); then
      has_descriptor=true
      PATCH_MODULE_PATH_JARS+=("$jar")
    fi

    if jar --list --file="$jar" 2>/dev/null | grep -q "module-info\.class"; then
      continue
    fi
    if [[ "$has_descriptor" == "false" ]] || echo "$desc" | grep -q "automatic"; then
      AUTOMATIC_MODULE_JARS+=("$jar")
    fi
  done
}

build_patch_module_path() {
  local current_jar="$1"
  local module_path=""

  for dep_jar in "${PATCH_MODULE_PATH_JARS[@]}"; do
    [[ "$dep_jar" != "$current_jar" ]] || continue
    [[ -n "$module_path" ]] && module_path+=":"
    module_path+="$dep_jar"
  done

  echo "$module_path"
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
  local module_path
  module_path="$(build_patch_module_path "$jar")"

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
  stage_module_patch_inputs

  local patched=0 failed=0
  for jar in "${AUTOMATIC_MODULE_JARS[@]}"; do
    if patch_automatic_module "$jar"; then
      ((patched++)) || true
    else
      ((failed++)) || true
    fi
  done

  log "Patched $patched automatic modules ($failed failed/skipped)"
}

# --- Phase 4: Build jlink image ---

build_jlink_image() {
  local runtime="$1"
  log "Building ${runtime} jlink image..."

  local module_path="${CLIENT_JAR}:${STAGING_DIR}"

  rm -rf "$OUTPUT_DIR"
  mkdir -p "$(dirname "$OUTPUT_DIR")"

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

  log "${runtime} jlink image created at: $OUTPUT_DIR"
}

# --- Phase 6: Generate startup optimization archives ---
#
# Leyden AOT cache with pre-linked classes and method profiles:
#   Eliminates class initialization overhead

generate_startup_archives() {
  local runtime="$1"
  local java_bin="${OUTPUT_DIR}/bin/java"
  local aot_config="${OUTPUT_DIR}/lib/server/aot-config.aotconf"
  local aot_cache="${OUTPUT_DIR}/lib/server/aot-cache.aot"
  local training_class
  local create_class
  case "$runtime" in
    claude)
      training_class="io.github.cowwoc.cat.claude.hook.AotTraining"
      create_class="io.github.cowwoc.cat.claude.hook.PreToolUseHook"
      ;;
    codex)
      training_class="io.github.cowwoc.cat.codex.hook.CodexAotTraining"
      create_class="io.github.cowwoc.cat.codex.hook.PreBashHook"
      ;;
    *) error "Unknown runtime: $runtime" ;;
  esac

  # JVM AOT messages (warnings, informational) go to stdout. The five lines below are
  # known-harmless: Jackson's SQL extension classes reference java.sql types not included
  # in the jlink image. All other AOT output is forwarded to stderr.
  local suppress_pattern
  suppress_pattern='Preload Warning: Verification failed for tools\.jackson\.databind\.ext\.sql\.JavaSqlBlobSerializer'
  suppress_pattern+='|Preload Warning: Verification failed for tools\.jackson\.databind\.ext\.sql\.JavaSqlDateSerializer'
  suppress_pattern+='|Skipping tools/jackson/databind/ext/sql/JavaSqlBlobSerializer'
  suppress_pattern+='|Skipping tools/jackson/databind/ext/sql/JavaSqlDateSerializer'
  suppress_pattern+='|Skipping tools/jackson/databind/ext/beans/JavaBeansAnnotationsImpl'

  # Leyden AOT: record runtime-specific training data, then create a pre-linked cache.
  log "Recording ${runtime} AOT training data..."
  # Set environment variables required by MainJvmScope so handlers can initialize.
  # Capture stdout+stderr: filter known-harmless Jackson SQL warnings on success, show all on failure.
  local aot_output
  aot_output=$(mktemp)
  local aot_plugin_data="${TARGET_DIR}/aot-plugin-data"
  local aot_config_dir="${TARGET_DIR}/aot-config-home"
  mkdir -p "$aot_plugin_data" "$aot_config_dir"
  # shellcheck disable=SC2064
  trap "rm -f '$aot_output'" RETURN
  if ! CLAUDE_PROJECT_DIR="$WORKSPACE_DIR" CLAUDE_PLUGIN_ROOT="${REACTOR_DIR}/plugin" \
    CLAUDE_PLUGIN_DATA="$aot_plugin_data" CLAUDE_CONFIG_DIR="$aot_config_dir" \
    CAT_PROJECT_DIR="$WORKSPACE_DIR" CAT_PLUGIN_ROOT="${REACTOR_DIR}/plugin" \
    CAT_PLUGIN_DATA="$aot_plugin_data" CODEX_HOME="$aot_config_dir" TZ="${TZ:-UTC}" \
    "$java_bin" \
      -XX:AOTMode=record \
      -XX:AOTConfiguration="$aot_config" \
      -m "$(handler_main "$training_class")" \
      >"$aot_output" 2>&1; then
    cat "$aot_output" >&2
    error "Failed to record ${runtime} AOT training data"
  fi
  grep -Ev "$suppress_pattern" "$aot_output" >&2 || true
  rm -f "$aot_output"
  trap - RETURN

  [[ -f "$aot_config" ]] || error "AOT configuration file not created: $aot_config"

  local create_output
  create_output=$(mktemp)
  # shellcheck disable=SC2064
  trap "rm -f '$create_output'" RETURN
  if ! printf '{}\n' | CLAUDE_PROJECT_DIR="$WORKSPACE_DIR" CLAUDE_PLUGIN_ROOT="${REACTOR_DIR}/plugin" \
    CLAUDE_PLUGIN_DATA="$aot_plugin_data" CLAUDE_CONFIG_DIR="$aot_config_dir" \
    CAT_PROJECT_DIR="$WORKSPACE_DIR" CAT_PLUGIN_ROOT="${REACTOR_DIR}/plugin" \
    CAT_PLUGIN_DATA="$aot_plugin_data" CODEX_HOME="$aot_config_dir" TZ="${TZ:-UTC}" \
    "$java_bin" \
    -XX:AOTMode=create \
    -XX:AOTConfiguration="$aot_config" \
    -XX:AOTCache="$aot_cache" \
    -XX:+AOTClassLinking \
    -m "$(handler_main "$create_class")" \
    >"$create_output" 2>&1; then
    cat "$create_output" >&2
    error "Failed to create ${runtime} AOT cache"
  fi
  grep -Ev "$suppress_pattern" "$create_output" >&2 || true
  rm -f "$create_output"
  trap - RETURN

  rm -f "$aot_config"
  log "  ${runtime} AOT cache: $(du -h "$aot_cache" | cut -f1)"
  log "${runtime} startup archives complete"
}

# --- Phase 5: Generate launcher scripts ---

generate_launchers() {
  log "Generating launcher scripts..."

  local bin_dir="${OUTPUT_DIR}/bin"

  for handler in "${HANDLERS[@]}"; do
    local name="${handler%%:*}"
    local class="${handler##*:}"
    local launcher="${bin_dir}/${name}"
    local main_class="$(handler_main "$class")"
    local launcher_dir
    local java_path
    local aot_path
    launcher_dir="$(dirname "$launcher")"
    mkdir -p "$launcher_dir"
    if [[ "$name" == */* ]]; then
      java_path='$DIR/../java'
      aot_path='$DIR/../../lib/server/aot-cache.aot'
    else
      java_path='$DIR/java'
      aot_path='$DIR/../lib/server/aot-cache.aot'
    fi

    log "  Creating launcher: $name -> $main_class"

    cat > "$launcher" <<'EOF'
#!/bin/sh
DIR=`dirname $0`
exec "JAVA_PATH" \
  ${CAT_JVM_OPTS:-} \
  ASSERTIONS_FLAG \
  -Xms16m -Xmx96m \
  -Dstdin.encoding=UTF-8 \
  -Dstdout.encoding=UTF-8 \
  -Dstderr.encoding=UTF-8 \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -XX:AOTCache="AOT_PATH" \
  -m MODULE_CLASS "$@"
EOF

    # Replace MODULE_CLASS and handle ASSERTIONS_FLAG
    if [[ "$ENABLE_ASSERTIONS" == "true" ]]; then
      sed -e "s|MODULE_CLASS|$main_class|g" -e "s|JAVA_PATH|$java_path|g" \
        -e "s|AOT_PATH|$aot_path|g" -e "s|ASSERTIONS_FLAG|-ea|g" \
        "$launcher" > "${launcher}.tmp"
    else
      sed -e "s|MODULE_CLASS|$main_class|g" -e "s|JAVA_PATH|$java_path|g" \
        -e "s|AOT_PATH|$aot_path|g" -e "/ASSERTIONS_FLAG/d" \
        "$launcher" > "${launcher}.tmp"
    fi
    mv "${launcher}.tmp" "$launcher"

    # Validation
    [[ -s "$launcher" ]] || error "Failed to generate launcher: $name (empty file)"
    ! grep -q "MODULE_CLASS" "$launcher" || error "Failed to generate launcher: $name (placeholder not removed)"
    grep -q "$main_class" "$launcher" || error "Failed to generate launcher: $name (main class not found)"
    ! grep -q "ASSERTIONS_FLAG" "$launcher" || \
      error "Failed to generate launcher: $name (assertions placeholder not removed)"
    grep -q "CAT_JVM_OPTS" "$launcher" || \
      error "Failed to generate launcher: $name (CAT_JVM_OPTS not found)"
    if [[ "$ENABLE_ASSERTIONS" == "true" ]]; then
      grep -q "\-ea" "$launcher" || error "Failed to generate launcher: $name (-ea flag not found)"
    fi
    chmod +x "$launcher"
  done

  log "Generated ${#HANDLERS[@]} launcher scripts"
}

# --- Phase 7: Verify ---

verify_pre_bash_launcher() {
  local runtime="$1"
  local launcher="${OUTPUT_DIR}/bin/pre-bash"
  log "  Testing ${runtime} pre-bash launcher..."
  if echo '{}' | "$launcher" &>/dev/null; then
    log "  ${runtime} pre-bash launcher works"
  else
    log "  Warning: ${runtime} pre-bash launcher test failed"
  fi
}

verify_codex_session_start_launcher() {
  local smoke_dir
  smoke_dir=$(mktemp -d)
  # shellcheck disable=SC2064
  trap "rm -rf '$smoke_dir'" RETURN
  local smoke_project="${smoke_dir}/project"
  local smoke_plugin="${smoke_dir}/plugin"
  local smoke_data="${smoke_dir}/plugin-data"
  mkdir -p "$smoke_project/.cat/rules/common" "$smoke_project/.cat/rules/codex" \
    "$smoke_plugin/.codex-plugin" "$smoke_plugin/rules/common" "$smoke_plugin/rules/codex" \
    "$smoke_data"
  printf '{"version":"2.1"}\n' > "$smoke_plugin/.codex-plugin/plugin.json"

  log "  Testing codex session-start launcher..."
  local session_output
  session_output=$(printf '{"cwd":"%s","plugin_root":"%s"}\n' "$smoke_project" "$smoke_plugin" | \
    CAT_PLUGIN_DATA="$smoke_data" CODEX_HOME="${smoke_dir}/codex-home" TZ="${TZ:-UTC}" \
    "${OUTPUT_DIR}/bin/session-start") || error "codex session-start launcher failed"
  if [[ "$session_output" != *'"hookSpecificOutput"'* ]]; then
    error "codex session-start launcher did not emit hookSpecificOutput"
  fi
  log "  codex session-start launcher works"
  rm -rf "$smoke_dir"
  trap - RETURN
}

verify_status_launcher() {
  local status_project_dir="${TARGET_DIR}/status-verify-project"
  local status_plugin_data="${TARGET_DIR}/status-verify-plugin-data"
  local status_config_dir="${TARGET_DIR}/status-verify-config-home"
  rm -rf "$status_project_dir" "$status_plugin_data" "$status_config_dir"
  mkdir -p "$status_project_dir" "$status_plugin_data" "$status_config_dir"

  log "  Testing get-status-output launcher..."
  local status_output
  if ! status_output=$(CLAUDE_PROJECT_DIR="$status_project_dir" \
    CLAUDE_PLUGIN_ROOT="${REACTOR_DIR}/plugin" \
    CLAUDE_PLUGIN_DATA="$status_plugin_data" \
    CLAUDE_CONFIG_DIR="$status_config_dir" \
    CLAUDE_SESSION_ID="jlink-status-verify-session" \
    TZ="${TZ:-UTC}" \
    "${OUTPUT_DIR}/bin/get-status-output"); then
    error "get-status-output launcher failed"
  fi
  if [[ "$status_output" != "No CAT project found. Initialize one first." ]]; then
    error "get-status-output launcher returned unexpected output: $status_output"
  fi
  log "  get-status-output launcher works"
}

verify_image() {
  local runtime="$1"
  log "Verifying ${runtime} jlink image..."

  if ! "${OUTPUT_DIR}/bin/java" -version &>/dev/null; then
    error "java -version failed"
  fi

  case "$runtime" in
    claude)
      verify_pre_bash_launcher "$runtime"
      ;;
    codex)
      verify_pre_bash_launcher "$runtime"
      verify_codex_session_start_launcher
      ;;
    *)
      error "Unknown runtime: $runtime"
      ;;
  esac
  verify_status_launcher

  log "${runtime} verification complete"
}

# --- Main ---

main() {
  log "Starting jlink build process..."

  ENABLE_ASSERTIONS=false
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --enable-assertions) ENABLE_ASSERTIONS=true; shift ;;
      *) error "Unknown argument: $1" ;;
    esac
  done

  ensure_client_jar
  stage_dependencies
  patch_automatic_modules
  rm -rf "$OUTPUT_ROOT"
  for runtime in claude codex; do
    OUTPUT_DIR="${OUTPUT_ROOT}/${runtime}"
    set_runtime_handlers "$runtime"
    build_jlink_image "$runtime"
    generate_launchers
    generate_startup_archives "$runtime"
    verify_image "$runtime"
  done

  log "Build complete!"
  log "Output: $OUTPUT_ROOT"
  for runtime in claude codex; do
    OUTPUT_DIR="${OUTPUT_ROOT}/${runtime}"
    set_runtime_handlers "$runtime"
    log "${runtime} launchers:"
    for handler in "${HANDLERS[@]}"; do
      log "  - ${OUTPUT_DIR}/bin/${handler%%:*}"
    done
  done
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
