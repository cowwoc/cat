#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# build-jlink-images.sh - Create self-contained engine-specific jlink images
#
# Pipeline:
#   1. Build the client JAR (if needed)
#   2. Stage engine dependency JARs
#   3. Patch automatic modules with generated module-info.class
#   4. Create engine-specific jlink images
#   5. Generate per-handler launcher scripts
#   6. Generate engine-specific Leyden AOT startup archives
#   7. Verify jlink images
#
# Usage:
#   ./scripts/build-jlink-images.sh
#
# Output:
#   target/jlink/{claude,codex}/ - Complete engine-specific jlink images with launchers

set -euo pipefail

# --- Configuration ---
# Note: variables used by generate_launchers (OUTPUT_DIR, HANDLERS, ENABLE_ASSERTIONS)
# are intentionally not declared readonly so tests can source this file and override them.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
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
COMMON_JAR="${REACTOR_DIR}/common-cli/target/client-common-cli-2.1.jar"
CLAUDE_JAR="${REACTOR_DIR}/claude-cli/target/client-claude-cli-2.1.jar"
CODEX_JAR="${REACTOR_DIR}/codex-cli/target/client-codex-cli-2.1.jar"
COMMON_MODULE_NAME="io.github.cowwoc.cat.common.cli"
CLAUDE_MODULE_NAME="io.github.cowwoc.cat.claude.cli"
CODEX_MODULE_NAME="io.github.cowwoc.cat.codex.cli"
ENABLE_ASSERTIONS=false
declare -a PATCH_MODULE_PATH_JARS=()
declare -a AUTOMATIC_MODULE_JARS=()

# Engine-neutral handler registry: launcher-name:fully.qualified.ClassName
# Each entry is included in every engine image.
declare -a COMMON_HANDLERS=(
  "token-counter:io.github.cowwoc.cat.tool.TokenCounter"
  "get-checkpoint-box:io.github.cowwoc.cat.tool.skills.GetCheckpointOutput"
  "get-issue-complete-box:io.github.cowwoc.cat.tool.skills.GetIssueCompleteOutput"
  "get-next-issue-box:io.github.cowwoc.cat.tool.skills.GetNextIssueOutput"
  "get-config-output:io.github.cowwoc.cat.tool.skills.GetConfigOutput"
  "update-config:io.github.cowwoc.cat.tool.util.UpdateConfig"
  "update-branch:io.github.cowwoc.cat.tool.util.UpdateBranch"
  "get-output:io.github.cowwoc.cat.tool.skills.GetOutput"
  "get-status-output:io.github.cowwoc.cat.tool.skills.GetStatusOutput"
  "get-cleanup-output:io.github.cowwoc.cat.tool.skills.GetCleanupOutput"
  "create-issue:io.github.cowwoc.cat.tool.util.IssueCreator"
  "session-analyzer:io.github.cowwoc.cat.tool.util.SessionAnalyzer"
  "extract-investigation-context:io.github.cowwoc.cat.tool.util.InvestigationContextExtractor"
  "grade-json-transformer:io.github.cowwoc.cat.tool.util.GradeJsonTransformer"
  "progress-banner:io.github.cowwoc.cat.tool.skills.ProgressBanner"
  "get-stakeholder-selection-box:io.github.cowwoc.cat.tool.skills.GetStakeholderSelectionBox"
  "get-stakeholder-review-box:io.github.cowwoc.cat.tool.skills.GetStakeholderReviewBox"
  "get-stakeholder-concern-box:io.github.cowwoc.cat.tool.skills.GetStakeholderConcernBox"
  "verify-audit:io.github.cowwoc.cat.tool.skills.VerifyAudit"
  "merge-and-cleanup:io.github.cowwoc.cat.tool.util.MergeAndCleanup"
  "git-squash:io.github.cowwoc.cat.tool.util.GitSquash"
  "git-merge-linear:io.github.cowwoc.cat.tool.util.GitMergeLinear"
  "git-amend:io.github.cowwoc.cat.tool.util.GitAmend"
  "git-rebase:io.github.cowwoc.cat.tool.util.GitRebase"
  "work-prepare:io.github.cowwoc.cat.tool.util.WorkPrepare"
  "issue-lock:io.github.cowwoc.cat.tool.util.IssueLock"
  "check-existing-work:io.github.cowwoc.cat.tool.util.ExistingWorkChecker"
  "wrap-markdown:io.github.cowwoc.cat.tool.util.MarkdownWrapper"
  "batch-read:io.github.cowwoc.cat.tool.util.BatchReader"
  "root-cause-analyzer:io.github.cowwoc.cat.tool.util.RootCauseAnalyzer"
  "validate-status-alignment:io.github.cowwoc.cat.tool.util.StatusAlignmentValidator"
  "feedback:io.github.cowwoc.cat.tool.util.Feedback"
  "get-add-output:io.github.cowwoc.cat.tool.skills.GetAddOutput"
  "record-learning:io.github.cowwoc.cat.tool.util.RecordLearning"
  "write-session-marker:io.github.cowwoc.cat.tool.util.WriteSessionMarker"
  "read-session-marker:io.github.cowwoc.cat.tool.util.ReadSessionMarker"
  "auto-close-index:io.github.cowwoc.cat.tool.util.AutoCloseIndexJson"
  "verify-defer-plan-generation:io.github.cowwoc.cat.tool.util.VerifyDeferPlanGeneration"
  "write-and-commit:io.github.cowwoc.cat.tool.util.WriteAndCommit"
  "extract-turns:io.github.cowwoc.cat.tool.skills.ExtractTurnsContent"
  "update-skill-description:io.github.cowwoc.cat.tool.skills.UpdateSkillDescription"
  "build-engine-artifacts:io.github.cowwoc.cat.agent.PluginArtifactBuilder"
)

# Claude-only handlers.
declare -a CLAUDE_HANDLERS=(
  "claude-runner:io.github.cowwoc.cat.claude.engine.ClaudeRunner"
  "sprt-runner:io.github.cowwoc.cat.claude.engine.ClaudeSprtRunner"
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
  "codex-runner:io.github.cowwoc.cat.codex.engine.CodexRunner"
  "sprt-runner:io.github.cowwoc.cat.codex.engine.CodexSprtRunner"
  "session-start:io.github.cowwoc.cat.codex.hook.SessionStartHook"
  "subagent-start:io.github.cowwoc.cat.codex.hook.SubagentStartHook"
  "pre-bash:io.github.cowwoc.cat.codex.hook.PreBashHook"
)

# Active handler registry used by generate_launchers. Tests source this file and override HANDLERS.
declare -a HANDLERS=("${COMMON_HANDLERS[@]}" "${CLAUDE_HANDLERS[@]}")

# --- Logging ---

log() { echo "[build-jlink] $*"; }
error() { echo "[build-jlink] ERROR: $*" >&2; exit 1; }

# --- Helpers ---

# Fully qualified main class for a handler class name
handler_module() {
  case "$1" in
    io.github.cowwoc.cat.codex.*) echo "$CODEX_MODULE_NAME" ;;
    io.github.cowwoc.cat.claude.*) echo "$CLAUDE_MODULE_NAME" ;;
    *) echo "$COMMON_MODULE_NAME" ;;
  esac
}

handler_main() {
  echo "$(handler_module "$1")/$1"
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

# Selects the handler registry for one engine image.
# Usage: set_engine_handlers <claude|codex>
set_engine_handlers() {
  local engine="$1"
  HANDLERS=("${COMMON_HANDLERS[@]}")
  case "$engine" in
    claude) HANDLERS+=("${CLAUDE_HANDLERS[@]}") ;;
    codex) HANDLERS+=("${CODEX_HANDLERS[@]}") ;;
    *) error "Unknown engine: $engine" ;;
  esac
}

# --- Phase 1: Build client JAR ---

ensure_client_jar() {
  if [[ -f "$COMMON_JAR" && -f "$CLAUDE_JAR" && -f "$CODEX_JAR" ]]; then
    log "CLI module JARs already exist"
    return
  fi

  log "Building CLI module JARs..."
  "$MVN" -f "$REACTOR_DIR/pom.xml" -pl claude-cli,codex-cli -am package -Dmaven.test.skip=true -q

  [[ -f "$COMMON_JAR" ]] || error "Failed to build common CLI JAR"
  [[ -f "$CLAUDE_JAR" ]] || error "Failed to build Claude CLI JAR"
  [[ -f "$CODEX_JAR" ]] || error "Failed to build Codex CLI JAR"
  log "CLI module JARs built successfully"
}

# --- Phase 2: Stage dependencies ---

stage_dependencies() {
  log "Staging engine dependencies..."
  rm -rf "$STAGING_DIR"
  mkdir -p "$STAGING_DIR"

  "$MVN" -f "${REACTOR_DIR}/common-cli/pom.xml" dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="$STAGING_DIR" \
    -q
  local jtokkit_jar="${HOME}/.m2/repository/com/knuddels/jtokkit/1.1.0/jtokkit-1.1.0.jar"
  [[ -f "$jtokkit_jar" ]] || error "Missing dependency JAR: $jtokkit_jar"
  cp "$jtokkit_jar" "$STAGING_DIR/"

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
  local engine="$1"
  log "Building ${engine} jlink image..."

  local module_path="${COMMON_JAR}:${CLAUDE_JAR}:${CODEX_JAR}:${STAGING_DIR}"
  local root_modules
  case "$engine" in
    claude) root_modules="${COMMON_MODULE_NAME},${CLAUDE_MODULE_NAME}" ;;
    codex) root_modules="${COMMON_MODULE_NAME},${CODEX_MODULE_NAME}" ;;
    *) error "Unknown engine: $engine" ;;
  esac

  rm -rf "$OUTPUT_DIR"
  mkdir -p "$(dirname "$OUTPUT_DIR")"

  jlink \
    --module-path "$module_path" \
    --add-modules "$root_modules" \
    --output "$OUTPUT_DIR" \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --generate-cds-archive

  # Remove nocoops CDS archive (only needed for heaps >32GB)
  rm -f "${OUTPUT_DIR}/lib/server/classes_nocoops.jsa"

  log "${engine} jlink image created at: $OUTPUT_DIR"
}

copy_legal_notices() {
  log "Copying legal notices..."
  cp "$WORKSPACE_DIR/LICENSE.md" "$OUTPUT_DIR/LICENSE.md"
  cp "$REACTOR_DIR/common-cli/target/generated-resources/licenses/THIRD-PARTY-NOTICES.txt" \
    "$OUTPUT_DIR/THIRD-PARTY-NOTICES.txt"
  mkdir -p "$OUTPUT_DIR/licenses"
  cp "$REACTOR_DIR"/legal/licenses/*.txt "$OUTPUT_DIR/licenses/"
}

plugin_version() {
  local jar_name
  jar_name="$(basename "$COMMON_JAR")"
  local version="${jar_name#client-common-cli-}"
  version="${version%.jar}"
  if [[ ! "$version" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]]; then
    error "Unable to derive plugin version from JAR name: $jar_name"
  fi
  echo "$version"
}

write_engine_plugin_descriptors() {
  local version="$1"
  local claude_descriptor_dir="${OUTPUT_ROOT}/claude/.claude-plugin"
  local codex_descriptor_dir="${OUTPUT_ROOT}/codex/.codex-plugin"
  mkdir -p "$claude_descriptor_dir" "$codex_descriptor_dir"
  printf '{"version":"%s"}\n' "$version" > "${claude_descriptor_dir}/plugin.json"
  printf '{"version":"%s"}\n' "$version" > "${codex_descriptor_dir}/plugin.json"
}

# --- Phase 6: Generate startup optimization archives ---
#
# Leyden AOT cache with pre-linked classes and method profiles:
#   Eliminates class initialization overhead

generate_startup_archives() {
  local engine="$1"
  local java_bin="${OUTPUT_DIR}/bin/java"
  local aot_config="${OUTPUT_DIR}/lib/server/aot-config.aotconf"
  local aot_cache="${OUTPUT_DIR}/lib/server/aot-cache.aot"
  local training_class
  local create_class
  case "$engine" in
    claude)
      training_class="io.github.cowwoc.cat.claude.hook.AotTraining"
      create_class="io.github.cowwoc.cat.claude.hook.PreToolUseHook"
      ;;
    codex)
      training_class="io.github.cowwoc.cat.codex.hook.CodexAotTraining"
      create_class="io.github.cowwoc.cat.codex.hook.PreBashHook"
      ;;
    *) error "Unknown engine: $engine" ;;
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

  # Leyden AOT: record engine-specific training data, then create a pre-linked cache.
  log "Recording ${engine} AOT training data..."
  # Set environment variables required by the engine scopes so handlers can initialize.
  # Capture stdout+stderr: filter known-harmless Jackson SQL warnings on success, show all on failure.
  local aot_output
  aot_output=$(mktemp)
  local aot_plugin_data="${TARGET_DIR}/aot-plugin-data"
  local aot_config_dir="${TARGET_DIR}/aot-config-home"
  mkdir -p "$aot_plugin_data" "$aot_config_dir"

  run_aot_command() {
    if [[ "$engine" == "claude" ]]; then
      env -u CAT_PROJECT_DIR -u CAT_PLUGIN_ROOT -u CAT_PLUGIN_DATA -u CAT_SESSION_ID \
        -u CAT_ENGINE -u CAT_CONFIG_DIR -u CODEX_THREAD_ID -u CODEX_HOME \
        CLAUDE_PROJECT_DIR="$WORKSPACE_DIR" CLAUDE_PLUGIN_ROOT="${REACTOR_DIR}/plugin" \
        CLAUDE_PLUGIN_DATA="$aot_plugin_data" CLAUDE_SESSION_ID="aot-training-session" \
        CLAUDE_CONFIG_DIR="$aot_config_dir" TZ="${TZ:-UTC}" "$@"
    else
      env -u CAT_PROJECT_DIR -u CAT_PLUGIN_ROOT -u CAT_PLUGIN_DATA -u CAT_SESSION_ID \
        -u CAT_ENGINE -u CAT_CONFIG_DIR -u CLAUDE_PROJECT_DIR -u CLAUDE_PLUGIN_ROOT \
        -u CLAUDE_PLUGIN_DATA -u CLAUDE_SESSION_ID -u CLAUDE_CONFIG_DIR \
        CODEX_THREAD_ID="aot-training-session" CODEX_HOME="$aot_config_dir" TZ="${TZ:-UTC}" "$@"
    fi
  }

  # shellcheck disable=SC2064
  trap "rm -f '$aot_output'" RETURN
  if ! run_aot_command "$java_bin" \
      -XX:AOTMode=record \
      -XX:AOTConfiguration="$aot_config" \
      -m "$(handler_main "$training_class")" \
      >"$aot_output" 2>&1; then
    cat "$aot_output" >&2
    error "Failed to record ${engine} AOT training data"
  fi
  grep -Ev "$suppress_pattern" "$aot_output" >&2 || true
  rm -f "$aot_output"
  trap - RETURN

  [[ -f "$aot_config" ]] || error "AOT configuration file not created: $aot_config"

  local create_output
  create_output=$(mktemp)
  # shellcheck disable=SC2064
  trap "rm -f '$create_output'" RETURN
  if ! printf '{}\n' | run_aot_command "$java_bin" \
    -XX:AOTMode=create \
    -XX:AOTConfiguration="$aot_config" \
    -XX:AOTCache="$aot_cache" \
    -XX:+AOTClassLinking \
    -m "$(handler_main "$create_class")" \
    >"$create_output" 2>&1; then
    cat "$create_output" >&2
    error "Failed to create ${engine} AOT cache"
  fi
  grep -Ev "$suppress_pattern" "$create_output" >&2 || true
  rm -f "$create_output"
  trap - RETURN

  rm -f "$aot_config"
  log "  ${engine} AOT cache: $(du -h "$aot_cache" | cut -f1)"
  log "${engine} startup archives complete"
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
    local main_module
    local launcher_dir
    local java_path
    local aot_path
    local use_aot=true
    main_module="$(handler_module "$class")"
    if [[ "$main_module" == "$COMMON_MODULE_NAME" ]]; then
      use_aot=false
    fi
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
  -Dcat.launcher.dir="$DIR" \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -XX:AOTCache="AOT_PATH" \
  -m 'MODULE_CLASS' "$@"
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
    if [[ "$use_aot" == "false" ]]; then
      sed '/-XX:AOTCache=/d' "${launcher}.tmp" > "${launcher}.tmp.no-aot"
      mv "${launcher}.tmp.no-aot" "${launcher}.tmp"
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
  local engine="$1"
  local launcher="${OUTPUT_DIR}/bin/pre-bash"
  log "  Testing ${engine} pre-bash launcher..."
  if echo '{}' | "$launcher" &>/dev/null; then
    log "  ${engine} pre-bash launcher works"
  else
    log "  Warning: ${engine} pre-bash launcher test failed"
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
  session_output=$(printf '{"cwd":"%s","plugin_root":"%s","plugin_data":"%s"}\n' \
    "$smoke_project" "$smoke_plugin" "$smoke_data" | \
    env -u CAT_PROJECT_DIR -u CAT_PLUGIN_ROOT -u CAT_PLUGIN_DATA -u CAT_SESSION_ID \
      -u CAT_ENGINE -u CAT_CONFIG_DIR -u CLAUDE_PROJECT_DIR -u CLAUDE_PLUGIN_ROOT \
      -u CLAUDE_PLUGIN_DATA -u CLAUDE_SESSION_ID -u CLAUDE_CONFIG_DIR \
      CODEX_HOME="${smoke_dir}/codex-home" TZ="${TZ:-UTC}" \
    "${OUTPUT_DIR}/bin/session-start") || error "codex session-start launcher failed"
  if [[ "$session_output" != *'"hookSpecificOutput"'* ]]; then
    error "codex session-start launcher did not emit hookSpecificOutput"
  fi
  log "  codex session-start launcher works"
  rm -rf "$smoke_dir"
  trap - RETURN
}

verify_codex_subagent_start_launcher() {
  local smoke_dir
  smoke_dir=$(mktemp -d)
  # shellcheck disable=SC2064
  trap "rm -rf '$smoke_dir'" RETURN
  local smoke_project="${smoke_dir}/project"
  local smoke_plugin="${smoke_dir}/plugin"
  local smoke_data="${smoke_dir}/plugin-data"
  mkdir -p "$smoke_project/.cat/rules/codex" "$smoke_plugin/.codex-plugin" \
    "$smoke_plugin/rules/codex" "$smoke_data"
  printf '{"version":"2.1"}\n' > "$smoke_plugin/.codex-plugin/plugin.json"

  log "  Testing codex subagent-start launcher..."
  local subagent_output
  subagent_output=$(printf '{"cwd":"%s","plugin_root":"%s","plugin_data":"%s","hook_event_name":"SubagentStart","agent_type":"cat:work-execute"}\n' \
    "$smoke_project" "$smoke_plugin" "$smoke_data" | \
    env -u CAT_PROJECT_DIR -u CAT_PLUGIN_ROOT -u CAT_PLUGIN_DATA -u CAT_SESSION_ID \
      -u CAT_ENGINE -u CAT_CONFIG_DIR -u CLAUDE_PROJECT_DIR -u CLAUDE_PLUGIN_ROOT \
      -u CLAUDE_PLUGIN_DATA -u CLAUDE_SESSION_ID -u CLAUDE_CONFIG_DIR \
      CODEX_HOME="${smoke_dir}/codex-home" TZ="${TZ:-UTC}" \
    "${OUTPUT_DIR}/bin/subagent-start") || error "codex subagent-start launcher failed"
  if [[ "$subagent_output" != *'"hookSpecificOutput"'* ]]; then
    error "codex subagent-start launcher did not emit hookSpecificOutput"
  fi
  log "  codex subagent-start launcher works"
  rm -rf "$smoke_dir"
  trap - RETURN
}

verify_status_launcher() {
  local status_project_dir="${TARGET_DIR}/status-verify-project"
  local status_plugin_data="${TARGET_DIR}/status-verify-plugin-data"
  local status_config_dir="${TARGET_DIR}/status-verify-config-home"
  local status_codex_home="${TARGET_DIR}/status-verify-codex-home"
  rm -rf "$status_project_dir" "$status_plugin_data" "$status_config_dir" "$status_codex_home"
  mkdir -p "$status_project_dir" "$status_plugin_data" "$status_config_dir" "$status_codex_home"

  log "  Testing get-status-output launcher..."
  local status_output
  if ! status_output=$(cd "$status_project_dir" && \
    env -u CAT_PROJECT_DIR -u CAT_PLUGIN_ROOT -u CAT_PLUGIN_DATA -u CAT_SESSION_ID \
    -u CAT_ENGINE -u CAT_CONFIG_DIR -u CLAUDE_PROJECT_DIR -u CLAUDE_PLUGIN_ROOT \
    -u CLAUDE_PLUGIN_DATA -u CLAUDE_SESSION_ID -u CLAUDE_CONFIG_DIR \
    CAT_JVM_OPTS="-Dcat.plugin.root=${REACTOR_DIR}/plugin ${CAT_JVM_OPTS:-}" \
    CODEX_THREAD_ID="jlink-status-verify-session" CODEX_HOME="$status_codex_home" \
    TZ="${TZ:-UTC}" "${OUTPUT_DIR}/bin/get-status-output"); then
    error "get-status-output launcher failed"
  fi
  if [[ "$status_output" != "No CAT project found. Initialize one first." ]]; then
    error "get-status-output launcher returned unexpected output: $status_output"
  fi

  log "  get-status-output launcher works"
}

verify_update_branch_launcher() {
  log "  Testing update-branch launcher..."
  local update_output
  set +e
  update_output=$("${OUTPUT_DIR}/bin/update-branch" 2>&1)
  local update_status=$?
  set -e
  if [[ "$update_status" -eq 0 ]]; then
    error "update-branch launcher unexpectedly succeeded without arguments"
  fi
  if [[ "$update_output" != *"Usage: update-branch"* ]]; then
    error "update-branch launcher returned unexpected output: $update_output"
  fi
  log "  update-branch launcher works"
}

verify_image() {
  local engine="$1"
  log "Verifying ${engine} jlink image..."

  if ! "${OUTPUT_DIR}/bin/java" -version &>/dev/null; then
    error "java -version failed"
  fi

  case "$engine" in
    claude)
      verify_pre_bash_launcher "$engine"
      ;;
    codex)
      verify_pre_bash_launcher "$engine"
      verify_codex_session_start_launcher
      verify_codex_subagent_start_launcher
      ;;
    *)
      error "Unknown engine: $engine"
      ;;
  esac
  verify_status_launcher
  verify_update_branch_launcher

  log "${engine} verification complete"
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
  for engine in claude codex; do
    OUTPUT_DIR="${OUTPUT_ROOT}/${engine}"
    set_engine_handlers "$engine"
    build_jlink_image "$engine"
    copy_legal_notices
    generate_launchers
    generate_startup_archives "$engine"
    verify_image "$engine"
  done
  write_engine_plugin_descriptors "$(plugin_version)"

  log "Build complete!"
  log "Output: $OUTPUT_ROOT"
  for engine in claude codex; do
    OUTPUT_DIR="${OUTPUT_ROOT}/${engine}"
    set_engine_handlers "$engine"
    log "${engine} launchers:"
    for handler in "${HANDLERS[@]}"; do
      log "  - ${OUTPUT_DIR}/bin/${handler%%:*}"
    done
  done
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
