#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

setup() {
  TEST_DIR=$(mktemp -d)
  BATS_TEST_SOURCED=true source "${BATS_TEST_DIRNAME}/../../../hooks/claude/session-start.sh" || true
}

teardown() {
  rm -rf "$TEST_DIR"
}

make_plugin_root() {
  local dir="$1" version="$2"
  mkdir -p "${dir}/.claude-plugin"
  printf '{"name":"cat","version":"%s"}' "$version" > "${dir}/.claude-plugin/plugin.json"
}

make_mock_java() {
  local bin_dir="$1" log_file="$2"
  mkdir -p "$bin_dir"
  cat > "${bin_dir}/java" <<'JAVA_EOF'
#!/usr/bin/env bash
if [[ "$1" == "-version" ]]; then
  echo "openjdk version \"26\" 2026-03-17" >&2
  exit 0
fi
printf '%s\n' "$*" >> "${MOCK_JAVA_LOG}"
printf '{"continue":true,"suppressOutput":false}\n'
exit 0
JAVA_EOF
  chmod +x "${bin_dir}/java"
  export MOCK_JAVA_LOG="$log_file"
}

@test "validate_semver accepts two and three component versions" {
  run validate_semver "2.1"
  [ "$status" -eq 0 ]

  run validate_semver "2.1.0"
  [ "$status" -eq 0 ]
}

@test "validate_semver rejects invalid versions" {
  run validate_semver "abc"
  [ "$status" -ne 0 ]

  run validate_semver "1.2.3-beta"
  [ "$status" -ne 0 ]

  run validate_semver "1.2.3.4"
  [ "$status" -ne 0 ]
}

@test "engine_version_matches fails when VERSION is missing" {
  local engine_dir="${TEST_DIR}/engine"
  mkdir -p "$engine_dir"

  run engine_version_matches "$engine_dir" "2.1.0"
  [ "$status" -ne 0 ]
}

@test "engine_version_matches fails on version mismatch" {
  local engine_dir="${TEST_DIR}/engine"
  mkdir -p "$engine_dir"
  echo "2.0.0" > "${engine_dir}/VERSION"

  run engine_version_matches "$engine_dir" "2.1.0"
  [ "$status" -ne 0 ]
}

@test "engine_version_matches succeeds on matching version" {
  local engine_dir="${TEST_DIR}/engine"
  mkdir -p "$engine_dir"
  echo "2.1.0" > "${engine_dir}/VERSION"

  run engine_version_matches "$engine_dir" "2.1.0"
  [ "$status" -eq 0 ]
}

@test "check_engine fails when directory does not exist" {
  run check_engine "/nonexistent/path/$$"
  [ "$status" -ne 0 ]
}

@test "check_engine fails when java binary is missing" {
  local engine_dir="${TEST_DIR}/engine"
  mkdir -p "${engine_dir}/bin"

  run check_engine "$engine_dir"
  [ "$status" -ne 0 ]
}

@test "check_engine fails when java binary exits non-zero" {
  local engine_dir="${TEST_DIR}/engine"
  mkdir -p "${engine_dir}/bin"
  cat > "${engine_dir}/bin/java" <<'JAVA_EOF'
#!/usr/bin/env bash
exit 1
JAVA_EOF
  chmod +x "${engine_dir}/bin/java"

  run check_engine "$engine_dir"
  [ "$status" -ne 0 ]
}

@test "check_engine succeeds when java -version works" {
  local engine_dir="${TEST_DIR}/engine"
  make_mock_java "${engine_dir}/bin" "${TEST_DIR}/java.log"

  run check_engine "$engine_dir"
  [ "$status" -eq 0 ]
}

@test "flush_log with no message returns without output" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""

  run flush_log
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "flush_log with warning level outputs hook JSON" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  log "warning" "test warning message"

  run flush_log
  [ "$status" -eq 0 ]
  [[ "$output" == *'"status"'* ]]
  [[ "$output" == *'"warning"'* ]]
}

@test "flush_log with error level exits zero after writing hook JSON" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  log "error" "test error message"

  run flush_log
  [ "$status" -eq 0 ]
  [[ "$output" == *'"error"'* ]]
}

@test "flush_log with debug context includes additionalContext" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  debug "test debug line"
  log "warning" "test warning with debug"

  run flush_log
  [ "$status" -eq 0 ]
  [[ "$output" == *'additionalContext'* ]]
}

@test "main reports error when plugin.json is not found" {
  export CLAUDE_PLUGIN_ROOT="${TEST_DIR}/empty-plugin"
  mkdir -p "$CLAUDE_PLUGIN_ROOT"

  run main
  [ "$status" -eq 0 ]
  [[ "$output" == *'"error"'* ]]
  unset CLAUDE_PLUGIN_ROOT
}

@test "main reports error when plugin.json has invalid version" {
  export CLAUDE_PLUGIN_ROOT="${TEST_DIR}/bad-version-plugin"
  mkdir -p "$CLAUDE_PLUGIN_ROOT/.claude-plugin"
  echo '{"version":"not-a-version"}' > "$CLAUDE_PLUGIN_ROOT/.claude-plugin/plugin.json"

  run main
  [ "$status" -eq 0 ]
  [[ "$output" == *'"error"'* ]]
  unset CLAUDE_PLUGIN_ROOT
}

@test "main launches Java from CLAUDE_PLUGIN_ROOT client engine" {
  local plugin_root="${TEST_DIR}/plugin-root"
  local plugin_data="${TEST_DIR}/plugin-data"
  local java_log="${TEST_DIR}/java.log"
  make_plugin_root "$plugin_root" "2.1.0"
  mkdir -p "${plugin_root}/client"
  echo "2.1.0" > "${plugin_root}/client/VERSION"
  make_mock_java "${plugin_root}/client/bin" "$java_log"

  export CLAUDE_PLUGIN_ROOT="$plugin_root"
  export CLAUDE_PLUGIN_DATA="$plugin_data"
  run main

  [ "$status" -eq 0 ]
  [[ "$output" == *'"continue":true'* ]]
  [[ "$(cat "$java_log")" == *'io.github.cowwoc.cat.client/io.github.cowwoc.cat.claude.hook.SessionStartHook'* ]]
  [ ! -e "${plugin_data}/client" ]
  unset CLAUDE_PLUGIN_ROOT
  unset CLAUDE_PLUGIN_DATA
}

@test "main warns when plugin-root engine is missing" {
  local plugin_root="${TEST_DIR}/plugin-root"
  make_plugin_root "$plugin_root" "2.1.0"

  export CLAUDE_PLUGIN_ROOT="$plugin_root"
  run main

  [ "$status" -eq 0 ]
  [[ "$output" == *'"warning"'* ]]
  [[ "$output" == *'CLAUDE_PLUGIN_ROOT'* ]]
  unset CLAUDE_PLUGIN_ROOT
}

@test "main ignores plugin-data engine when plugin-root engine is missing" {
  local plugin_root="${TEST_DIR}/plugin-root"
  local plugin_data="${TEST_DIR}/plugin-data"
  local java_log="${TEST_DIR}/data-java.log"
  make_plugin_root "$plugin_root" "2.1.0"
  mkdir -p "${plugin_data}/client"
  echo "2.1.0" > "${plugin_data}/client/VERSION"
  make_mock_java "${plugin_data}/client/bin" "$java_log"

  export CLAUDE_PLUGIN_ROOT="$plugin_root"
  export CLAUDE_PLUGIN_DATA="$plugin_data"
  run main

  [ "$status" -eq 0 ]
  [[ "$output" == *'"warning"'* ]]
  [ ! -s "$java_log" ]
  unset CLAUDE_PLUGIN_ROOT
  unset CLAUDE_PLUGIN_DATA
}
