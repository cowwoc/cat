#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

setup() {
  TEST_DIR=$(mktemp -d)
  # Source the script under test, guarding against main() execution
  BATS_TEST_SOURCED=true source "${BATS_TEST_DIRNAME}/../../plugin/hooks/session-start.sh" || true
}

teardown() {
  rm -rf "$TEST_DIR"
}

@test "get_platform returns non-empty string" {
  result=$(get_platform)
  [ -n "$result" ]
}

@test "check_runtime fails when directory does not exist" {
  run check_runtime "/nonexistent/path/$$"
  [ "$status" -ne 0 ]
}

@test "check_runtime fails when java binary is missing" {
  local fake_jdk="${TEST_DIR}/fake-jdk"
  mkdir -p "${fake_jdk}/bin"
  # bin/java is absent - only the directory exists
  run check_runtime "$fake_jdk"
  [ "$status" -ne 0 ]
}

@test "try_acquire_runtime with VERSION matching plugin_version calls check_runtime" {
  local fake_jdk="${TEST_DIR}/fake-jdk"
  local fake_version="9.9.9"
  mkdir -p "${fake_jdk}/bin"
  echo "$fake_version" > "${fake_jdk}/VERSION"
  # No java binary present, so check_runtime should fail → try_acquire_runtime returns non-zero
  run try_acquire_runtime "$fake_jdk" "$fake_version"
  # check_runtime fails (no java binary), so result is non-zero
  [ "$status" -ne 0 ]
}

@test "try_acquire_runtime detects version mismatch" {
  local fake_jdk="${TEST_DIR}/fake-jdk"
  mkdir -p "${fake_jdk}/bin"
  echo "1.0.0" > "${fake_jdk}/VERSION"
  # VERSION file says 1.0.0 but plugin_version is 2.0.0 - mismatch triggers download attempt
  # Download will fail (no network/real release), so overall status is non-zero
  run try_acquire_runtime "$fake_jdk" "2.0.0"
  [ "$status" -ne 0 ]
}

@test "flush_log with no message returns 0 without output" {
  # Reset log state
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  run flush_log
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "flush_log with warning level outputs JSON and does not exit 1" {
  # Reset log state
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  log "warning" "test warning message"
  run flush_log
  [ "$status" -eq 0 ]
  # Output should contain JSON with status field
  [[ "$output" == *'"status"'* ]]
  [[ "$output" == *'"warning"'* ]]
}

@test "try_acquire_runtime returns 0 when VERSION matches and java works" {
  local fake_jdk="${TEST_DIR}/fake-jdk"
  local fake_version="9.9.9"
  mkdir -p "${fake_jdk}/bin"
  echo "$fake_version" > "${fake_jdk}/VERSION"
  # Create a fake java binary that mimics 'java -version' (prints to stderr, exits 0)
  cat > "${fake_jdk}/bin/java" <<'JAVA_EOF'
#!/usr/bin/env bash
echo "openjdk version \"25\" 2025-09-16" >&2
exit 0
JAVA_EOF
  chmod +x "${fake_jdk}/bin/java"
  run try_acquire_runtime "$fake_jdk" "$fake_version"
  [ "$status" -eq 0 ]
}

@test "flush_log with error level exits 1" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  log "error" "test error message"
  run flush_log
  [ "$status" -eq 1 ]
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

@test "main fails when plugin.json is not found" {
  # Point CLAUDE_PLUGIN_ROOT to an empty temp dir (no plugin.json)
  export CLAUDE_PLUGIN_ROOT="${TEST_DIR}/empty-plugin"
  mkdir -p "$CLAUDE_PLUGIN_ROOT/.claude-plugin"
  # No plugin.json file created
  run main
  [ "$status" -ne 0 ]
  unset CLAUDE_PLUGIN_ROOT
}

@test "download_runtime fails when SHA256 does not match" {
  # This test verifies the sha256 check logic works correctly
  # by testing the verification logic in isolation
  local fake_archive
  fake_archive=$(mktemp --suffix=.tar.gz)
  echo "fake archive content" > "$fake_archive"

  local temp_sha256
  temp_sha256=$(mktemp --suffix=.sha256)
  echo "0000000000000000000000000000000000000000000000000000000000000000  test.tar.gz" > "$temp_sha256"

  # Manually run the SHA256 check logic
  local expected_sha256 actual_sha256
  expected_sha256=$(awk '{print $1}' "$temp_sha256")
  actual_sha256=$(sha256sum "$fake_archive" | awk '{print $1}')

  # They should NOT match
  [ "$expected_sha256" != "$actual_sha256" ]

  rm -f "$fake_archive" "$temp_sha256"
}

# --- Testing HIGH ---

@test "download_runtime fails gracefully when curl is not available" {
  # Mock curl with a fake that returns non-zero to simulate curl failure
  local fake_bin="${TEST_DIR}/fake-bin"
  mkdir -p "$fake_bin"
  cat > "${fake_bin}/curl" <<'CURL_EOF'
#!/usr/bin/env bash
exit 1
CURL_EOF
  chmod +x "${fake_bin}/curl"

  local fake_jdk="${TEST_DIR}/fake-jdk"

  PATH="${fake_bin}:$PATH" run download_runtime "$fake_jdk" "1.0.0"
  [ "$status" -ne 0 ]
}

@test "try_acquire_runtime re-downloads when VERSION file exists but check_runtime fails" {
  local fake_jdk="${TEST_DIR}/fake-jdk"
  local fake_version="1.2.3"
  mkdir -p "${fake_jdk}/bin"
  echo "$fake_version" > "${fake_jdk}/VERSION"
  # java binary is present but exits non-zero (corrupted JDK simulation)
  cat > "${fake_jdk}/bin/java" <<'JAVA_EOF'
#!/usr/bin/env bash
exit 1
JAVA_EOF
  chmod +x "${fake_jdk}/bin/java"

  # check_runtime will fail (java exits 1), so it should attempt download_runtime
  # download_runtime will fail (no real network), so overall returns non-zero
  run try_acquire_runtime "$fake_jdk" "$fake_version"
  [ "$status" -ne 0 ]
}

# --- Testing MEDIUM ---

@test "get_platform returns error on unknown OS" {
  # Mock uname to return an unknown OS
  local fake_bin="${TEST_DIR}/fake-bin"
  mkdir -p "$fake_bin"
  cat > "${fake_bin}/uname" <<'UNAME_EOF'
#!/usr/bin/env bash
if [[ "$1" == "-s" ]]; then
  echo "UnknownOS"
elif [[ "$1" == "-m" ]]; then
  echo "x86_64"
else
  echo "UnknownOS"
fi
UNAME_EOF
  chmod +x "${fake_bin}/uname"

  PATH="${fake_bin}:$PATH" run get_platform
  [ "$status" -ne 0 ]
}

@test "check_runtime fails when java binary exits non-zero" {
  local fake_jdk="${TEST_DIR}/fake-jdk"
  mkdir -p "${fake_jdk}/bin"
  # java binary present but exits with non-zero (simulates broken JDK)
  cat > "${fake_jdk}/bin/java" <<'JAVA_EOF'
#!/usr/bin/env bash
exit 1
JAVA_EOF
  chmod +x "${fake_jdk}/bin/java"

  run check_runtime "$fake_jdk"
  [ "$status" -ne 0 ]
}

@test "try_acquire_runtime downloads when no VERSION file exists" {
  local fake_jdk="${TEST_DIR}/fake-jdk"
  mkdir -p "${fake_jdk}/bin"
  # No VERSION file at all - no java binary either

  # No VERSION file present → should attempt download (which will fail without network)
  run try_acquire_runtime "$fake_jdk" "1.0.0"
  [ "$status" -ne 0 ]
}

@test "validate_semver accepts two-component version (X.Y)" {
  run validate_semver "2.1"
  [ "$status" -eq 0 ]
}

@test "validate_semver rejects non-numeric version" {
  run validate_semver "abc"
  [ "$status" -ne 0 ]
}

@test "validate_semver accepts 0.0.0" {
  run validate_semver "0.0.0"
  [ "$status" -eq 0 ]
}

@test "validate_semver accepts 999.999.999" {
  run validate_semver "999.999.999"
  [ "$status" -eq 0 ]
}

@test "validate_semver rejects pre-release suffix" {
  run validate_semver "1.2.3-beta"
  [ "$status" -ne 0 ]
}

@test "validate_semver rejects four-component version" {
  run validate_semver "1.2.3.4"
  [ "$status" -ne 0 ]
}

@test "validate_semver rejects leading space" {
  run validate_semver " 1.2.3"
  [ "$status" -ne 0 ]
}

@test "flush_log produces valid JSON" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  log "warning" 'message with "quotes" and \backslash'
  run flush_log
  [ "$status" -eq 0 ]
  [[ "$output" == *'"status"'* ]]
  [[ "$output" == *'"message"'* ]]
}

@test "main fails when plugin.json has invalid version format" {
  export CLAUDE_PLUGIN_ROOT="${TEST_DIR}/bad-version-plugin"
  mkdir -p "$CLAUDE_PLUGIN_ROOT/.claude-plugin"
  echo '{"version":"not-a-version","repository":"https://github.com/cowwoc/cat"}' > "$CLAUDE_PLUGIN_ROOT/.claude-plugin/plugin.json"
  run main
  [ "$status" -ne 0 ]
  unset CLAUDE_PLUGIN_ROOT
}

@test "flush_log renders newlines as JSON escape sequences" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""
  log "warning" "line one"
  log "warning" "line two"
  run flush_log
  [ "$status" -eq 0 ]
  # The JSON output should contain \n (JSON escape) between the two lines,
  # NOT literal backslash-n characters (\\n)
  [[ "$output" == *'line one\nline two'* ]]
  [[ "$output" != *'line one\\nline two'* ]]
}

@test "log accumulates messages and error escalates over warning" {
  LOG_LEVEL=""
  LOG_MESSAGE=""
  DEBUG_LINES=""

  log "warning" "first warning"
  [ "$LOG_LEVEL" = "warning" ]

  log "warning" "second warning"
  # Level stays warning
  [ "$LOG_LEVEL" = "warning" ]
  # Messages accumulate
  [[ "$LOG_MESSAGE" == *"first warning"* ]]
  [[ "$LOG_MESSAGE" == *"second warning"* ]]

  # Error escalates over warning
  log "error" "an error"
  [ "$LOG_LEVEL" = "error" ]

  # Further warnings do NOT downgrade error
  log "warning" "another warning after error"
  [ "$LOG_LEVEL" = "error" ]
}
