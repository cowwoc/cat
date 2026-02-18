#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for skill-preprocessor-failure.sh
# Runs without Bats by using a simple pass/fail counting approach.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_SCRIPT="$SCRIPT_DIR/../skill-preprocessor-failure.sh"

PASS_COUNT=0
FAIL_COUNT=0
FAILURES=()

# Run a test that expects the hook to produce output containing a given substring.
# Arguments:
#   $1 - test name
#   $2 - expected output substring (non-empty: hook should output this)
#   $3 - input JSON to pass via stdin
expect_output()
{
  local test_name="$1"
  local expected_substring="$2"
  local input="$3"

  local actual_output
  actual_output=$(printf "%s" "$input" | bash "$HOOK_SCRIPT" 2>/dev/null || true)

  if echo "$actual_output" | grep -qF "$expected_substring"; then
    echo "  PASS: $test_name"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL: $test_name"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    FAILURES+=("$test_name: expected output containing '$expected_substring', got: '$actual_output'")
  fi
}

# Run a test that expects the hook to produce no output.
# Arguments:
#   $1 - test name
#   $2 - input to pass via stdin (use "DEVNULL" for closed stdin)
expect_no_output()
{
  local test_name="$1"
  local input="$2"

  local actual_output
  if [[ "$input" == "DEVNULL" ]]; then
    actual_output=$(bash "$HOOK_SCRIPT" </dev/null 2>/dev/null || true)
  else
    actual_output=$(printf "%s" "$input" | bash "$HOOK_SCRIPT" 2>/dev/null || true)
  fi

  if [[ -z "$actual_output" ]]; then
    echo "  PASS: $test_name"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL: $test_name"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    FAILURES+=("$test_name: expected no output, got: '$actual_output'")
  fi
}

echo "Running tests for skill-preprocessor-failure.sh"
echo "------------------------------------------------"

# Happy path: valid JSON with error matching the skill preprocessor failure pattern.
# The error field contains the literal text: Bash command failed for pattern "!`": ...
# JSON-encoded: {"error": "Bash command failed for pattern \"!`\": exit code 1"}
MATCHING_JSON='{"toolName":"Skill","error":"Bash command failed for pattern \"!`\": exit code 1"}'

expect_output \
  "happy_path_matching_error_outputs_additionalContext" \
  '"additionalContext"' \
  "$MATCHING_JSON"

expect_output \
  "happy_path_output_contains_PostToolUseFailure_hookEventName" \
  '"hookEventName": "PostToolUseFailure"' \
  "$MATCHING_JSON"

expect_output \
  "happy_path_output_contains_feedback_instruction" \
  "/cat:feedback" \
  "$MATCHING_JSON"

# Valid JSON without matching error pattern - no output
expect_no_output \
  "valid_json_non_matching_error_produces_no_output" \
  '{"error": "Some other unrelated error occurred"}'

# Invalid JSON - exits gracefully with no output
expect_no_output \
  "invalid_json_exits_gracefully" \
  "not valid json {"

# Empty stdin - exits gracefully with no output
expect_no_output \
  "empty_stdin_exits_gracefully" \
  ""

# Closed stdin (simulating non-piped invocation) - exits gracefully with no output
expect_no_output \
  "closed_stdin_exits_gracefully" \
  "DEVNULL"

echo "------------------------------------------------"
echo "Results: $PASS_COUNT passed, $FAIL_COUNT failed"

if [[ ${#FAILURES[@]} -gt 0 ]]; then
  echo ""
  echo "Failures:"
  for failure in "${FAILURES[@]}"; do
    echo "  - $failure"
  done
  exit 1
fi

exit 0
