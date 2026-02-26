#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# aot-harness.sh - Test harness that isolates generate_startup_archives logic.
#
# This mirrors the AOT recording pattern in build-jlink.sh so tests can validate
# error reporting behavior without running a full jlink build.
#
# Usage:
#   aot-harness.sh <OUTPUT_DIR>
#
# Exit code:
#   0  AOT recording and cache creation succeeded
#   1  AOT recording or cache creation failed (with error details on stderr)

set -euo pipefail

OUTPUT_DIR="${1:?Usage: aot-harness.sh <OUTPUT_DIR>}"
java_bin="${OUTPUT_DIR}/bin/java"
aot_config="${OUTPUT_DIR}/lib/server/aot-config.aotconf"
aot_cache="${OUTPUT_DIR}/lib/server/aot-cache.aot"

log()   { echo "[aot-harness] $*"; }
error() { echo "[aot-harness] ERROR: $*" >&2; exit 1; }

MODULE_NAME="io.github.cowwoc.cat.hooks"

handler_main() {
  echo "${MODULE_NAME}/${MODULE_NAME}.$1"
}

log "Recording AOT training data..."
aot_stderr=$(mktemp)
trap 'rm -f "$aot_stderr"' EXIT

if ! CLAUDE_PROJECT_DIR="${OUTPUT_DIR}" CLAUDE_PLUGIN_ROOT="${OUTPUT_DIR}/plugin" \
  "$java_bin" \
    -XX:AOTMode=record \
    -XX:AOTConfiguration="$aot_config" \
    -m "$(handler_main AotTraining)" \
    2>"$aot_stderr"; then
  cat "$aot_stderr" >&2
  error "Failed to record AOT training data"
fi
rm -f "$aot_stderr"
trap - EXIT

[[ -f "$aot_config" ]] || error "AOT configuration file not created: $aot_config"

if ! "$java_bin" \
  -XX:AOTMode=create \
  -XX:AOTConfiguration="$aot_config" \
  -XX:AOTCache="$aot_cache" \
  -m "$(handler_main PreToolUseHook)" \
  2>&1; then
  error "Failed to create AOT cache"
fi

rm -f "$aot_config"
log "AOT cache: $(du -h "$aot_cache" | cut -f1)"
log "Startup archives complete"
