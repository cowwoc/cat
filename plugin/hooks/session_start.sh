#!/usr/bin/env bash
# session_start.sh - Bootstrap the CAT jlink runtime at session start
#
# Ensures the custom JDK runtime is available for Java hooks by trying
# (in order): existing install → download pre-built bundle → local build.
# Exports CAT_JAVA_HOME on success. Silent on success (JSON output only).

set -euo pipefail

# --- Configuration ---

readonly HOOKS_JAR_SUBPATH="hooks/cat-hooks-2.1.jar"
readonly JDK_SUBDIR="hooks"
readonly DOWNLOAD_BASE_URL="https://github.com/cowwoc/cat/releases/download"
readonly PLUGIN_VERSION="v2.1"

# --- Debug logging ---

DEBUG_LINES=""

debug() {
  if [[ -n "$DEBUG_LINES" ]]; then
    DEBUG_LINES="${DEBUG_LINES}\\n$*"
  else
    DEBUG_LINES="$*"
  fi
}

# --- JSON output ---

log_json() {
  local status="$1" message="$2" context="${3:-}"

  # Append debug trace
  if [[ -n "$DEBUG_LINES" ]]; then
    local debug_block="[session_start debug]\\n${DEBUG_LINES}"
    if [[ -n "$context" ]]; then
      context="${context}\\n\\n${debug_block}"
    else
      context="$debug_block"
    fi
  fi

  if [[ -n "$context" ]]; then
    cat <<EOF
{
  "status": "$status",
  "message": "$message",
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": "$context"
  }
}
EOF
  else
    cat <<EOF
{
  "status": "$status",
  "message": "$message"
}
EOF
  fi
}

# --- Platform detection ---

get_platform() {
  local os arch

  case "$(uname -s)" in
    Linux*)  os="linux" ;;
    Darwin*) os="macos" ;;
    MINGW*|MSYS*|CYGWIN*) os="windows" ;;
    *) echo "unknown"; return 1 ;;
  esac

  case "$(uname -m)" in
    x86_64|amd64)   arch="x64" ;;
    aarch64|arm64)   arch="aarch64" ;;
    *) echo "unknown"; return 1 ;;
  esac

  echo "${os}-${arch}"
}

# --- Runtime acquisition strategies ---

check_runtime() {
  local jdk_path="$1"

  [[ -d "$jdk_path" ]] || { debug "JDK directory does not exist: $jdk_path"; return 1; }

  local java_bin="${jdk_path}/bin/java"
  [[ -x "$java_bin" ]] || { debug "java binary not executable or missing: $java_bin"; return 1; }

  "$java_bin" -version &>/dev/null || { debug "java binary exists but failed to run: $java_bin"; return 1; }

  debug "JDK runtime verified at: $jdk_path"
}

download_runtime() {
  local target_dir="$1"
  local platform
  platform=$(get_platform) || { debug "Unsupported platform"; return 1; }

  local archive_name="cat-jdk-25-${platform}.tar.gz"
  local url="${DOWNLOAD_BASE_URL}/${PLUGIN_VERSION}/${archive_name}"
  local temp="/tmp/${archive_name}"

  debug "Downloading runtime from ${url}"

  curl -sSfL -o "$temp" "$url" 2>/dev/null || { debug "Download failed: $url"; return 1; }

  mkdir -p "$(dirname "$target_dir")"
  if ! tar -xzf "$temp" -C "$(dirname "$target_dir")" 2>/dev/null; then
    debug "Failed to extract archive"
    rm -f "$temp"
    return 1
  fi

  rm -f "$temp"
  debug "Runtime installed to $target_dir"
}

build_runtime_locally() {
  local plugin_root="$1" target_dir="$2"
  local build_script="${plugin_root}/hooks/jlink-config.sh"

  [[ -x "$build_script" ]] || { debug "Build script not found: $build_script"; return 1; }

  debug "Building runtime locally via $build_script"

  mkdir -p "$(dirname "$target_dir")"
  "$build_script" build --output-dir "$(dirname "$target_dir")" 2>&1 || { debug "Local build failed"; return 1; }
}

# --- Runtime setup with fallback chain ---

try_acquire_runtime() {
  local jdk_path="$1" plugin_root="$2"

  # Strategy 1: Already installed
  debug "Checking existing runtime..."
  if check_runtime "$jdk_path"; then
    return 0
  fi

  # Strategy 2: Download pre-built bundle
  debug "Attempting download..."
  if download_runtime "$jdk_path" && check_runtime "$jdk_path"; then
    return 0
  fi

  # Strategy 3: Build locally (requires full JDK)
  debug "Attempting local build..."
  if build_runtime_locally "$plugin_root" "$jdk_path" && check_runtime "$jdk_path"; then
    return 0
  fi

  return 1
}

# --- Main ---

main() {
  # Determine plugin root
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local plugin_root="${script_dir}/.."
  debug "script_dir=$script_dir"

  if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
    plugin_root="$CLAUDE_PLUGIN_ROOT"
    debug "plugin_root=$plugin_root (from CLAUDE_PLUGIN_ROOT)"
  else
    debug "plugin_root=$plugin_root (from script location)"
  fi

  # Verify hooks JAR exists
  local hooks_jar="${plugin_root}/${HOOKS_JAR_SUBPATH}"
  debug "Checking hooks JAR at: $hooks_jar"
  if [[ ! -f "$hooks_jar" ]]; then
    log_json "warning" "Java hooks JAR not found at ${hooks_jar}. Run /build-hooks to build and install it."
    return 0
  fi

  # Acquire runtime
  local jdk_path="${plugin_root}/${JDK_SUBDIR}"
  debug "JDK path: $jdk_path"

  if try_acquire_runtime "$jdk_path" "$plugin_root"; then
    export CAT_JAVA_HOME="$jdk_path"
    log_json "success" "CAT JDK runtime ready"
    return 0
  fi

  # All strategies failed
  debug "All acquisition methods failed"
  log_json "warning" "CAT JDK runtime not available. Java hooks will use system Java if available." \
    "To enable optimized Java hooks, either:\\n1. Install JDK 25 and run: ${plugin_root}/hooks/jlink-config.sh build\\n2. Download pre-built runtime from GitHub releases"
}

main "$@"
