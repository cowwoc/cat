#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# session-start.sh - Bootstrap the CAT jlink runtime and run session handlers
#
# Ensures the custom JDK runtime is available for Java hooks by comparing the
# local bundle version against plugin.json version. If they match, uses the
# existing bundle. If they differ, downloads the correct bundle from GitHub.
# After JDK is ready, invokes the SessionStartHook Java dispatcher
# which handles all session start tasks (upgrade check, update check,
# session ID injection, retrospective reminders, instructions, env injection,
# skill marker cleanup).

set -euo pipefail

# --- Configuration ---

readonly JDK_SUBDIR="hooks"

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

json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\t'/\\t}"
  s="${s//$'\r'/\\r}"
  printf '%s' "$s"
}

flush_log() {
  [[ -z "$LOG_MESSAGE" ]] && return 0

  local escaped_status escaped_message
  escaped_status=$(json_escape "$LOG_LEVEL")
  escaped_message=$(json_escape "$LOG_MESSAGE")

  if [[ -n "$DEBUG_LINES" ]]; then
    local context="[session_start debug]\\n${DEBUG_LINES}"
    local escaped_context
    escaped_context=$(json_escape "$context")
    printf '{"status":"%s","message":"%s","systemMessage":"%s","hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' \
      "$escaped_status" "$escaped_message" "$escaped_message" "$escaped_context"
  else
    printf '{"status":"%s","message":"%s","systemMessage":"%s"}\n' \
      "$escaped_status" "$escaped_message" "$escaped_message"
  fi

  if [[ "$LOG_LEVEL" == "error" ]]; then
    exit 1
  fi
}

# --- Version validation ---

validate_semver() {
  local version="$1"
  if ! [[ "$version" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    debug "Invalid version format: '$version' (expected X.Y or X.Y.Z)"
    return 1
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
  local plugin_version="$2"

  validate_semver "$plugin_version" || { debug "Refusing to construct URL with invalid version: $plugin_version"; return 1; }

  local platform
  platform=$(get_platform) || { debug "Unsupported platform"; return 1; }

  local archive_name="cat-jdk-25-${platform}.tar.gz"
  local base_url="https://github.com/cowwoc/cat/releases/download/v${plugin_version}"
  local asset_url="${base_url}/${archive_name}"
  local sha256_url="${base_url}/${archive_name}.sha256"

  debug "Downloading runtime from ${asset_url}"

  local temp temp_sha256
  temp=$(mktemp --suffix=.tar.gz) || { debug "Failed to create temp file"; return 1; }
  temp_sha256=$(mktemp --suffix=.sha256) || { rm -f "$temp"; debug "Failed to create sha256 temp file"; return 1; }

  # Clean up temp files on function exit (success or failure)
  trap 'rm -f "$temp" "$temp_sha256"' RETURN

  curl -sSfL --max-time 300 --max-filesize 524288000 -o "$temp" "$asset_url" 2>/dev/null || { debug "Download failed: $asset_url"; return 1; }

  curl -sSfL --max-time 30 -o "$temp_sha256" "$sha256_url" 2>/dev/null || {
    debug "SHA256 download failed: $sha256_url"
    return 1
  }

  # Verify checksum (sha256 file contains "hash  filename" format)
  local expected_sha256 actual_sha256
  expected_sha256=$(awk '{print $1}' "$temp_sha256")
  actual_sha256=$(sha256sum "$temp" | awk '{print $1}')

  if [[ "$expected_sha256" != "$actual_sha256" ]]; then
    debug "SHA256 verification failed for $archive_name"
    return 1
  fi
  debug "SHA256 verified for $archive_name"

  mkdir -p "$(dirname "$target_dir")"
  if ! tar --no-absolute-names -xzf "$temp" -C "$(dirname "$target_dir")" 2>/dev/null; then
    debug "Failed to extract archive"
    return 1
  fi

  # Verify no symlinks in extracted directory (defense against symlink attacks)
  if find "$target_dir" -type l 2>/dev/null | grep -q .; then
    debug "Archive contains symlinks (rejected for security)"
    rm -rf "$target_dir"
    return 1
  fi

  debug "Runtime installed to $target_dir"
}

# --- Runtime setup with version comparison ---

try_acquire_runtime() {
  local jdk_path="$1"
  local plugin_version="$2"

  # Check if the local bundle version matches the plugin version
  local version_file="${jdk_path}/VERSION"
  if [[ -f "$version_file" ]]; then
    local local_version
    local_version=$(cat "$version_file")
    debug "Local bundle version: $local_version, plugin version: $plugin_version"

    if [[ "$local_version" == "$plugin_version" ]]; then
      # Versions match - verify runtime works and return
      debug "Versions match, checking existing runtime..."
      if check_runtime "$jdk_path"; then
        return 0
      fi
      debug "Existing runtime check failed despite version match, re-downloading..."
    else
      debug "Version mismatch (local: $local_version, required: $plugin_version), downloading correct bundle..."
    fi
  else
    debug "No VERSION file found at $version_file, downloading bundle..."
  fi

  # Download the bundle matching the plugin version
  if download_runtime "$jdk_path" "$plugin_version" && check_runtime "$jdk_path"; then
    echo "${plugin_version}" > "${jdk_path}/VERSION"
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

  # Read plugin version from plugin.json
  local plugin_json="${plugin_root}/.claude-plugin/plugin.json"
  if [[ ! -f "$plugin_json" ]]; then
    log "error" "plugin.json not found: $plugin_json"
    flush_log
    return 1
  fi

  local plugin_version
  plugin_version=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' "$plugin_json" | sed 's/.*"\([^"]*\)"$/\1/')

  if [[ -z "$plugin_version" ]]; then
    log "error" "Failed to read version from plugin.json: $plugin_json"
    flush_log
    return 1
  fi

  if ! validate_semver "$plugin_version"; then
    log "error" "plugin.json contains invalid version format: '$plugin_version' (expected X.Y or X.Y.Z): $plugin_json"
    flush_log
    return 1
  fi

  debug "Plugin version: $plugin_version"

  # Acquire runtime
  local jdk_path="${plugin_root}/${JDK_SUBDIR}"
  debug "JDK path: $jdk_path"

  if try_acquire_runtime "$jdk_path" "$plugin_version"; then
    debug "JDK runtime ready, invoking Java dispatcher"

    # Invoke the SessionStartHook Java dispatcher
    # It handles all session start tasks: upgrade check, update check, session ID,
    # retrospective reminders, session instructions, env injection, skill marker cleanup.
    # Pipe stdin directly to avoid buffering large input in memory.
    "$jdk_path/bin/java" \
      -Xms16m -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
      -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.SessionStartHook
    return 0
  fi

  # All strategies failed - warn with helpful context
  local platform
  platform=$(get_platform 2>/dev/null || echo "unknown")
  debug "All acquisition methods failed"
  log "warning" "Failed to acquire CAT hooks runtime (version ${plugin_version}, platform ${platform}). Sessions will start without hook processing. Check your network connection and try restarting."
  flush_log
}

if [[ "${BATS_TEST_SOURCED:-}" != "true" ]]; then
  main "$@"
fi
