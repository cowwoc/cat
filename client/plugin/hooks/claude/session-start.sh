#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# session-start.sh - Validate bundled runtime and run session handlers.

set -euo pipefail

LOG_LEVEL=""
LOG_MESSAGE=""
DEBUG_LINES=""
FAILURE_CAUSE=""

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
    fail "Invalid version format: '$version' (expected X.Y or X.Y.Z)"
    return 1
  fi
}

runtime_version_matches() {
  local runtime_dir="$1" plugin_version="$2"
  local version_file="${runtime_dir}/VERSION"

  if [[ ! -f "$version_file" ]]; then
    fail "No VERSION file found at $version_file"
    return 1
  fi

  local runtime_version
  runtime_version=$(cat "$version_file")
  debug "Runtime version at $runtime_dir: $runtime_version, plugin version: $plugin_version"
  if [[ "$runtime_version" != "$plugin_version" ]]; then
    fail "Runtime version mismatch at $runtime_dir: expected $plugin_version, found $runtime_version"
    return 1
  fi
}

check_runtime() {
  local runtime_dir="$1"

  if [[ ! -d "$runtime_dir" ]]; then
    fail "Runtime directory does not exist: $runtime_dir"
    return 1
  fi

  local java_bin="${runtime_dir}/bin/java"
  if [[ ! -x "$java_bin" ]]; then
    fail "java binary not executable or missing: $java_bin"
    return 1
  fi

  local java_err=""
  java_err=$("$java_bin" -version 2>&1) || {
    fail "java binary exists but failed to run: $java_bin: $java_err"
    return 1
  }

  debug "JDK runtime verified at: $runtime_dir"
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
    log "error" "${FAILURE_CAUSE:-plugin.json contains invalid version format}"
    flush_log
    return 1
  fi

  debug "Plugin version: $plugin_version"
  debug ""
  while IFS= read -r line; do
    debug "$line"
  done < <(env | sort)

  local runtime_dir="${plugin_root}/client"
  debug "JDK path: $runtime_dir"

  if runtime_version_matches "$runtime_dir" "$plugin_version" && check_runtime "$runtime_dir"; then
    debug "JDK runtime ready, invoking Java dispatcher"

    local java_exit=0
    "${plugin_root}/client/bin/java" \
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
  local msg="Bundled CAT hooks runtime under CLAUDE_PLUGIN_ROOT is missing or invalid (version ${plugin_version})."
  msg+=$'\n'"Cause: ${cause}"
  msg+=$'\n'"Session will start without hook processing."
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
