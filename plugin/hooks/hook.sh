#!/usr/bin/env bash
# hook.sh - Bridge between Claude Code's hook system and Java hook handlers
#
# Locates the jlink runtime, enables startup archives (AppCDS + Leyden AOT),
# and invokes the specified handler class via the Java module system.
#
# Usage:
#   echo '{"tool":"Bash","input":"..."}' | hook.sh GetBashPretoolOutput
#
# Environment:
#   CAT_JAVA_HOME  - Path to jlink runtime image (required, set by session_start.sh)
#   CAT_JAVA_TIMEOUT - Handler timeout in seconds (default: 30)
#   CAT_JAVA_XMS     - Initial heap size (default: 16m)
#   CAT_JAVA_XMX     - Maximum heap size (default: 64m)

set -euo pipefail

# --- Configuration ---

readonly MODULE="io.github.cowwoc.cat.hooks"
readonly JAVA_TIMEOUT="${CAT_JAVA_TIMEOUT:-30}"
readonly JAVA_XMS="${CAT_JAVA_XMS:-16m}"
readonly JAVA_XMX="${CAT_JAVA_XMX:-64m}"

# --- Functions ---

find_java() {
  if [[ -z "${CAT_JAVA_HOME:-}" ]]; then
    echo "Error: CAT_JAVA_HOME not set. Run session_start.sh first." >&2
    return 1
  fi

  local java_bin="${CAT_JAVA_HOME}/bin/java"
  if [[ ! -x "$java_bin" ]]; then
    echo "Error: Java binary not found at ${java_bin}" >&2
    return 1
  fi

  echo "$java_bin"
}

run_handler() {
  local handler_class="$1"; shift

  local java_bin
  java_bin=$(find_java) || {
    echo '{"status":"error","message":"CAT jlink runtime not found. Run session_start.sh to install."}' >&2
    return 1
  }

  # Support both short names (GetBashPretoolOutput) and fully qualified names
  local full_class
  if [[ "$handler_class" == *.* ]]; then
    full_class="$handler_class"
  else
    full_class="${MODULE}.${handler_class}"
  fi

  # JVM flags optimized for short-lived CLI processes
  local java_opts=(
    "-Xms${JAVA_XMS}"
    "-Xmx${JAVA_XMX}"
    "-XX:+UseSerialGC"
    "-XX:TieredStopAtLevel=1"
    "-Djava.security.egd=file:/dev/./urandom"
  )

  # Enable startup archives if present (AppCDS + Leyden AOT → ~8ms startup)
  local aot_cache="${CAT_JAVA_HOME}/lib/server/aot-cache.aot"
  local appcds="${CAT_JAVA_HOME}/lib/server/appcds.jsa"
  [[ -f "$aot_cache" ]] && java_opts+=("-XX:AOTCache=$aot_cache")
  [[ -f "$appcds" ]]    && java_opts+=("-XX:SharedArchiveFile=$appcds")

  timeout "${JAVA_TIMEOUT}" "$java_bin" \
    "${java_opts[@]}" \
    -m "${MODULE}/${full_class}" \
    "$@"
}

# --- Main ---

main() {
  if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <handler-class> [args...]" >&2
    exit 1
  fi

  local handler_class="$1"; shift

  if [[ ! "$handler_class" =~ ^[A-Za-z][A-Za-z0-9.]*$ ]]; then
    echo '{"status":"error","message":"Invalid handler class name"}' >&2
    exit 1
  fi

  run_handler "$handler_class" "$@"
}

main "$@"
