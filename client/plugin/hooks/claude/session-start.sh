#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# session-start.sh - Bootstrap the bundled CAT jlink runtime and run session handlers.

set -euo pipefail

LOG_LEVEL=""
LOG_MESSAGE=""
DEBUG_LINES=""
FAILURE_CAUSE=""
LOCK_PATH=""

debug() {
  if [[ -n "$DEBUG_LINES" ]]; then
    DEBUG_LINES="${DEBUG_LINES}"$'\n'"$*"
  else
    DEBUG_LINES="$*"
  fi
}

fail() {
  FAILURE_CAUSE="$*"
  debug "$*"
}

log() {
  local level="$1" message="$2"

  if [[ -n "$LOG_MESSAGE" ]]; then
    LOG_MESSAGE="${LOG_MESSAGE}"$'\n'"${message}"
  else
    LOG_MESSAGE="$message"
  fi

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
    local context="[session_start debug]"$'\n'"${DEBUG_LINES}"
    local escaped_context
    escaped_context=$(json_escape "$context")
    printf '{"status":"%s","message":"%s","systemMessage":"%s","hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' \
      "$escaped_status" "$escaped_message" "$escaped_message" "$escaped_context"
  else
    printf '{"status":"%s","message":"%s","systemMessage":"%s"}\n' \
      "$escaped_status" "$escaped_message" "$escaped_message"
  fi

  if [[ "$LOG_LEVEL" == "error" ]]; then
    exit 0
  fi
}

validate_semver() {
  local version="$1"
  if ! [[ "$version" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    debug "Invalid version format: '$version' (expected X.Y or X.Y.Z)"
    return 1
  fi
}

runtime_version_matches() {
  local runtime_dir="$1" plugin_version="$2"
  local version_file="${runtime_dir}/VERSION"

  [[ -f "$version_file" ]] || { debug "No VERSION file found at $version_file"; return 1; }
  local local_version
  local_version=$(cat "$version_file")
  debug "Runtime version at $runtime_dir: $local_version, plugin version: $plugin_version"
  [[ "$local_version" == "$plugin_version" ]] || return 1
}

validate_plugin_data_path() {
  local plugin_data="$1" plugin_data_root="$2"

  if [[ -z "$plugin_data_root" ]]; then
    fail "CLAUDE_PLUGIN_DATA is empty"
    return 1
  fi
  if [[ "$plugin_data_root" != /* ]]; then
    fail "CLAUDE_PLUGIN_DATA must be an absolute path: $plugin_data_root"
    return 1
  fi
  if [[ "$plugin_data_root" == "/" ]]; then
    fail "CLAUDE_PLUGIN_DATA may not point at the filesystem root"
    return 1
  fi
  if [[ "$plugin_data" != "${plugin_data_root}/client" ]]; then
    fail "Refusing runtime install outside CLAUDE_PLUGIN_DATA/client: $plugin_data"
    return 1
  fi
  if [[ -L "$plugin_data" ]]; then
    fail "Refusing to replace symlinked CAT runtime directory: $plugin_data"
    return 1
  fi
  if [[ -e "$plugin_data" && ! -d "$plugin_data" ]]; then
    fail "CAT runtime path exists but is not a directory: $plugin_data"
    return 1
  fi
}

check_runtime() {
  local runtime_dir="$1"

  [[ -d "$runtime_dir" ]] || { debug "Runtime directory does not exist: $runtime_dir"; return 1; }

  local java_bin="${runtime_dir}/bin/java"
  [[ -x "$java_bin" ]] || { debug "java binary not executable or missing: $java_bin"; return 1; }

  local java_err=""
  java_err=$("$java_bin" -version 2>&1) || {
    debug "java binary exists but failed to run: $java_bin: $java_err"
    return 1
  }

  debug "JDK runtime verified at: $runtime_dir"
}

acquire_runtime_lock() {
  local plugin_data="$1"
  local lock_path="${plugin_data}.lock"
  local stale_threshold_seconds=600
  local timeout_seconds=30
  local elapsed=0

  while true; do
    if [[ -d "$lock_path" ]]; then
      local lock_mtime now age
      lock_mtime=$(stat -c "%Y" "$lock_path" 2>/dev/null || stat -f "%m" "$lock_path" 2>/dev/null || echo 0)
      now=$(date +%s)
      age=$(( now - lock_mtime ))
      if (( age > stale_threshold_seconds )); then
        debug "Removing stale lock (age: ${age}s): $lock_path"
        rmdir "$lock_path" 2>/dev/null || true
      fi
    fi

    if mkdir "$lock_path" 2>/dev/null; then
      LOCK_PATH="$lock_path"
      debug "Lock acquired: $lock_path"
      return 0
    fi

    if (( elapsed >= timeout_seconds )); then
      debug "Timed out waiting for lock after ${elapsed}s: $lock_path"
      return 1
    fi

    debug "Lock held by another session, waiting... (${elapsed}s elapsed)"
    sleep 1
    (( elapsed++ )) || true
  done
}

release_runtime_lock() {
  if [[ -n "${LOCK_PATH:-}" && -d "$LOCK_PATH" ]]; then
    rmdir "$LOCK_PATH" 2>/dev/null || true
    debug "Lock released: $LOCK_PATH"
    LOCK_PATH=""
  fi
}

if [[ "${BATS_TEST_SOURCED:-}" != "true" ]]; then
  trap 'release_runtime_lock' EXIT
fi

install_bundled_runtime() {
  local bundled_runtime="$1" plugin_data="$2" plugin_version="$3"

  if ! runtime_version_matches "$bundled_runtime" "$plugin_version" || ! check_runtime "$bundled_runtime"; then
    fail "Bundled CAT runtime is missing or invalid: $bundled_runtime"
    return 1
  fi

  mkdir -p "$(dirname "$plugin_data")"
  local tmp="${plugin_data}.tmp.$$"
  rm -rf -- "$tmp"
  mkdir -p "$tmp"
  cp -R "${bundled_runtime}/." "$tmp/"
  echo "$plugin_version" > "${tmp}/VERSION"

  if [[ -L "$plugin_data" ]]; then
    fail "Refusing to replace symlinked CAT runtime directory: $plugin_data"
    rm -rf -- "$tmp"
    return 1
  fi
  if [[ -d "$plugin_data" ]]; then
    chmod -R u+w "$plugin_data" 2>/dev/null || true
    rm -rf -- "$plugin_data"
  fi
  mv -- "$tmp" "$plugin_data"
  debug "Installed bundled CAT runtime to $plugin_data"
}

try_acquire_runtime() {
  local plugin_root="$1" plugin_data="$2" plugin_version="$3"
  local bundled_runtime="${plugin_root}/client"

  if runtime_version_matches "$plugin_data" "$plugin_version" && check_runtime "$plugin_data"; then
    return 0
  fi

  LOCK_PATH=""
  if ! acquire_runtime_lock "$plugin_data"; then
    fail "Timed out waiting for concurrent runtime install lock: ${plugin_data}.lock"
    return 1
  fi
  trap 'release_runtime_lock' RETURN

  if runtime_version_matches "$plugin_data" "$plugin_version" && check_runtime "$plugin_data"; then
    return 0
  fi

  install_bundled_runtime "$bundled_runtime" "$plugin_data" "$plugin_version" &&
    runtime_version_matches "$plugin_data" "$plugin_version" &&
    check_runtime "$plugin_data"
}

main() {
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local plugin_root="${script_dir}/../.."
  debug "script_dir=$script_dir"

  if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
    plugin_root="$CLAUDE_PLUGIN_ROOT"
    debug "plugin_root=$plugin_root (from CLAUDE_PLUGIN_ROOT)"
  else
    debug "plugin_root=$plugin_root (from script location)"
  fi

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
  debug ""
  while IFS= read -r line; do
    debug "$line"
  done < <(env | sort)

  if [[ -z "${CLAUDE_PLUGIN_DATA:-}" ]]; then
    log "error" "CLAUDE_PLUGIN_DATA is not set"
    flush_log
    return 1
  fi

  local plugin_data="${CLAUDE_PLUGIN_DATA}/client"
  debug "JDK path: $plugin_data"
  if ! validate_plugin_data_path "$plugin_data" "$CLAUDE_PLUGIN_DATA"; then
    log "error" "${FAILURE_CAUSE:-Invalid CLAUDE_PLUGIN_DATA path}"
    flush_log
    return 1
  fi

  if try_acquire_runtime "$plugin_root" "$plugin_data" "$plugin_version"; then
    debug "JDK runtime ready, invoking Java dispatcher"

    local java_exit=0
    "$plugin_data/bin/java" \
      -Xms16m -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
      -m io.github.cowwoc.cat.client/io.github.cowwoc.cat.claude.hook.SessionStartHook || java_exit=$?

    if [[ "$java_exit" -ne 0 ]]; then
      log "error" "SessionStartHook Java dispatcher failed (exit code $java_exit). Run with CLAUDE_CODE_DEBUG_LOGS_DIR=/tmp/cat-debug.log for details."
      flush_log
      return 1
    fi
    return 0
  fi

  local cause="${FAILURE_CAUSE:-Unknown failure}"
  local msg="Failed to acquire bundled CAT hooks runtime (version ${plugin_version})."
  msg+=$'\n'"Cause: ${cause}"
  msg+=$'\n'"Sessions will start without hook processing."
  if [[ -n "$DEBUG_LINES" ]]; then
    msg+=$'\n'
    msg+=$'\n'"Debug trail:"
    local line
    while IFS= read -r line; do
      msg+=$'\n'"  ${line}"
    done <<< "$DEBUG_LINES"
  fi
  log "warning" "$msg"
  flush_log
}

if [[ "${BATS_TEST_SOURCED:-}" != "true" ]]; then
  main "$@"
fi
