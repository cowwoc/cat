#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
# Tests for CLAUDE_PLUGIN_ROOT resolution logic in session-start.sh main().
# Covers: unset (script-dir fallback), set to valid path, empty string, and nonexistent path.

setup() {
  TEST_DIR=$(mktemp -d)
  BATS_TEST_SOURCED=true source "${BATS_TEST_DIRNAME}/../../plugin/hooks/session-start.sh" || true

  # Create a minimal valid plugin root with plugin.json
  VALID_PLUGIN_ROOT="${TEST_DIR}/valid-root"
  mkdir -p "${VALID_PLUGIN_ROOT}/.claude-plugin"
  printf '{"name":"cat","version":"2.1.0"}' > "${VALID_PLUGIN_ROOT}/.claude-plugin/plugin.json"
}

teardown() {
  unset CLAUDE_PLUGIN_ROOT
  rm -rf "$TEST_DIR"
}

# Mirror the plugin_root determination block from main() in session-start.sh
resolve_plugin_root() {
  local script_dir
  script_dir="$(cd "${BATS_TEST_DIRNAME}/../../plugin/hooks" && pwd)"
  local plugin_root="${script_dir}/.."
  if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
    plugin_root="$CLAUDE_PLUGIN_ROOT"
  fi
  realpath "$plugin_root" 2>/dev/null || echo "$plugin_root"
}

@test "CLAUDE_PLUGIN_ROOT unset resolves to the script parent directory" {
  unset CLAUDE_PLUGIN_ROOT
  local resolved expected
  resolved=$(resolve_plugin_root)
  expected="$(realpath "${BATS_TEST_DIRNAME}/../../plugin/hooks/..")"
  [ "$resolved" = "$expected" ]
}

@test "CLAUDE_PLUGIN_ROOT set to a valid path is used directly" {
  export CLAUDE_PLUGIN_ROOT="$VALID_PLUGIN_ROOT"
  local resolved
  resolved=$(resolve_plugin_root)
  [ "$resolved" = "$(realpath "$VALID_PLUGIN_ROOT")" ]
}

@test "CLAUDE_PLUGIN_ROOT set to empty string falls back to script directory" {
  export CLAUDE_PLUGIN_ROOT=""
  local resolved expected
  resolved=$(resolve_plugin_root)
  expected="$(realpath "${BATS_TEST_DIRNAME}/../../plugin/hooks/..")"
  [ "$resolved" = "$expected" ]
}

@test "CLAUDE_PLUGIN_ROOT set to nonexistent path causes plugin.json existence check to fail" {
  export CLAUDE_PLUGIN_ROOT="${TEST_DIR}/nonexistent"
  local plugin_json="${CLAUDE_PLUGIN_ROOT}/.claude-plugin/plugin.json"
  [ ! -f "$plugin_json" ]
}

@test "valid CLAUDE_PLUGIN_ROOT with plugin.json passes existence check" {
  export CLAUDE_PLUGIN_ROOT="$VALID_PLUGIN_ROOT"
  local plugin_json="${CLAUDE_PLUGIN_ROOT}/.claude-plugin/plugin.json"
  [ -f "$plugin_json" ]
}
