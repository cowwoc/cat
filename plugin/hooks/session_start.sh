#!/usr/bin/env bash
# session_start.sh - Bootstrap the CAT jlink runtime and run session handlers
#
# Ensures the custom JDK runtime is available for Java hooks by trying
# (in order): existing install → download pre-built bundle from GitHub.
# After JDK is ready, invokes the GetSessionStartOutput Java dispatcher
# which handles all session start tasks (upgrade check, update check,
# session ID injection, retrospective reminders, instructions, env injection,
# skill marker cleanup).

set -euo pipefail

# --- Save stdin early (before any command consumes it) ---

STDIN_CONTENT=""
if [ ! -t 0 ]; then
  STDIN_CONTENT=$(cat)
fi

# --- Configuration ---

readonly JDK_SUBDIR="hooks"
readonly DOWNLOAD_BASE_URL="https://github.com/cowwoc/cat/releases/download"
readonly PLUGIN_VERSION="v2.1"

# --- Logging ---

LOG_LEVEL=""
LOG_MESSAGE=""
DEBUG_LINES=""

debug() {
  if [[ -n "$DEBUG_LINES" ]]; then
    DEBUG_LINES="${DEBUG_LINES}\\n$*"
  else
    DEBUG_LINES="$*"
  fi
}

log() {
  local level="$1" message="$2"

  # Accumulate message
  if [[ -n "$LOG_MESSAGE" ]]; then
    LOG_MESSAGE="${LOG_MESSAGE}\\n${message}"
  else
    LOG_MESSAGE="$message"
  fi

  # Escalate level: error > any other level
  if [[ "$level" == "error" ]]; then
    LOG_LEVEL="error"
  elif [[ -z "$LOG_LEVEL" ]]; then
    LOG_LEVEL="$level"
  fi
}

flush_log() {
  [[ -z "$LOG_MESSAGE" ]] && return 0

  local context=""
  if [[ -n "$DEBUG_LINES" ]]; then
    context="[session_start debug]\\n${DEBUG_LINES}"
  fi

  if [[ -n "$context" ]]; then
    cat <<EOF
{
  "status": "$LOG_LEVEL",
  "message": "$LOG_MESSAGE",
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": "$context"
  }
}
EOF
  else
    cat <<EOF
{
  "status": "$LOG_LEVEL",
  "message": "$LOG_MESSAGE"
}
EOF
  fi

  if [[ "$LOG_LEVEL" == "error" ]]; then
    exit 1
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

# --- Runtime setup with fallback chain ---

try_acquire_runtime() {
  local jdk_path="$1"

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

  # Acquire runtime
  local jdk_path="${plugin_root}/${JDK_SUBDIR}"
  debug "JDK path: $jdk_path"

  if try_acquire_runtime "$jdk_path"; then
    export CAT_JAVA_HOME="$jdk_path"
    debug "JDK runtime ready, invoking Java dispatcher"

    # Invoke the GetSessionStartOutput Java dispatcher
    # It handles all session start tasks: upgrade check, update check, session ID,
    # retrospective reminders, session instructions, env injection, skill marker cleanup
    echo "$STDIN_CONTENT" | "$jdk_path/bin/java" \
      -Xms16m -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
      -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.GetSessionStartOutput
    return 0
  fi

  # All strategies failed
  local platform
  platform=$(get_platform 2>/dev/null || echo "unknown")
  local archive_name="cat-jdk-25-${platform}.tar.gz"
  local download_url="${DOWNLOAD_BASE_URL}/${PLUGIN_VERSION}/${archive_name}"
  debug "All acquisition methods failed"
  log "error" "Failed to download CAT hooks runtime from ${download_url}"
  flush_log
}

main "$@"
