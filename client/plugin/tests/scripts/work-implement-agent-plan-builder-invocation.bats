#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for the plan-builder-agent invocation logic used in work-implement-agent Step 4.
# Covers hasSteps detection, effort extraction, argument construction, and full conditional flow.

setup() {
    TMPDIR="$(mktemp -d)"
    PLAN_MD="${TMPDIR}/plan.md"
    MOCK_BIN="${TMPDIR}/bin"
    mkdir -p "${MOCK_BIN}"

    # Source the helper
    HELPER_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
    source "${HELPER_DIR}/plan-builder-invocation-helper.bash"
}

teardown() {
    rm -rf "${TMPDIR:-}"
}

# Helper to create mock get-config-output binary
create_config_mock() {
    local json="$1"
    local exit_code="${2:-0}"
    echo "${json}" > "${TMPDIR}/config-output.json"
    cat > "${MOCK_BIN}/get-config-output" << 'SCRIPT'
#!/usr/bin/env bash
if [[ -f "$(dirname "$0")/../config-exit-code" ]]; then
    exit $(cat "$(dirname "$0")/../config-exit-code")
fi
cat "$(dirname "$0")/../config-output.json"
SCRIPT
    chmod +x "${MOCK_BIN}/get-config-output"
    if [[ "$exit_code" -ne 0 ]]; then
        echo "$exit_code" > "${TMPDIR}/config-exit-code"
    else
        rm -f "${TMPDIR}/config-exit-code"
    fi
}

# --- detect_has_steps tests ---

@test "detect_has_steps returns false when plan has no steps section" {
    printf '## Goal\n\nSome goal text.\n\n## Post-conditions\n\n- condition 1\n' > "${PLAN_MD}"
    result=$(detect_has_steps "${PLAN_MD}")
    [ "$result" = "false" ]
}

@test "detect_has_steps returns true when plan has Jobs" {
    printf '## Goal\n\nSome goal.\n\n## Jobs\n\n### Job 1\n' > "${PLAN_MD}"
    result=$(detect_has_steps "${PLAN_MD}")
    [ "$result" = "true" ]
}

@test "detect_has_steps returns true when plan has Execution Steps" {
    printf '## Goal\n\nSome goal.\n\n## Execution Steps\n\n1. Step one\n' > "${PLAN_MD}"
    result=$(detect_has_steps "${PLAN_MD}")
    [ "$result" = "true" ]
}

# --- extract_effort tests ---

@test "extract_effort parses effort from standard config JSON" {
    result=$(extract_effort '{"effort": "high", "trust": "medium"}')
    [ "$result" = "high" ]
}

@test "extract_effort parses effort value low" {
    result=$(extract_effort '{"effort": "low", "trust": "medium"}')
    [ "$result" = "low" ]
}

@test "extract_effort parses effort value medium" {
    result=$(extract_effort '{"effort": "medium"}')
    [ "$result" = "medium" ]
}

@test "extract_effort returns empty when effort field missing" {
    result=$(extract_effort '{"trust": "medium"}')
    [ "$result" = "" ]
}

@test "extract_effort handles whitespace variations in JSON" {
    result=$(extract_effort '{ "effort" : "high" }')
    [ "$result" = "high" ]
}

# --- build_plan_builder_args tests ---

@test "build_plan_builder_args constructs correct args string" {
    result=$(build_plan_builder_args "agent-123" "high" "/path/to/issue")
    [ "$result" = "agent-123 high revise /path/to/issue Generate full implementation steps for this lightweight plan. Add Jobs or Execution Steps section with detailed step-by-step implementation guidance." ]
}

@test "build_plan_builder_args propagates effort value" {
    result=$(build_plan_builder_args "agent-456" "low" "/other/path")
    [[ "$result" == *"low revise"* ]]
}

# --- should_invoke_plan_builder tests ---

@test "should_invoke_plan_builder prints SKIP when hasSteps is true" {
    printf '## Jobs\n\n### Job 1\n' > "${PLAN_MD}"
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path")
    [ "$result" = "SKIP" ]
}

@test "should_invoke_plan_builder prints INVOKE with correct args when hasSteps is false" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{"effort": "high"}'
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-789" "/issue/path")
    [[ "$result" == "INVOKE:"* ]]
    [[ "$result" == *"agent-789 high revise /issue/path"* ]]
}

@test "should_invoke_plan_builder returns error when config read fails" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{}' 1
    run should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path"
    [ "$status" -eq 1 ]
    [[ "$output" == *"ERROR: Failed to read effective config"* ]]
}

@test "should_invoke_plan_builder with effort=low propagates to args" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{"effort": "low"}'
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path")
    [[ "$result" == *"low revise"* ]]
}

@test "full flow: hasSteps=true skips config read entirely" {
    printf '## Execution Steps\n\n1. Do something\n' > "${PLAN_MD}"
    # Do NOT create a config mock — leave get-config-output missing
    run should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path"
    [ "$status" -eq 0 ]
    [ "$output" = "SKIP" ]
}
