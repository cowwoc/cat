#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for EFFORT value extraction from config JSON.
# Covers valid values, invalid values, edge cases, and E2E propagation through
# should_invoke_plan_builder.

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

# --- extract_effort edge cases ---

@test "extract_effort extracts value from realistic full config JSON" {
    result=$(extract_effort '{"displayWidth":50,"fileWidth":120,"trust":"medium","verify":"all","effort":"high","patience":"low","minSeverity":"low"}')
    [ "$result" = "high" ]
}

@test "extract_effort returns raw value for invalid effort string" {
    result=$(extract_effort '{"effort": "invalid"}')
    [ "$result" = "invalid" ]
}

@test "extract_effort returns empty for empty effort string" {
    result=$(extract_effort '{"effort": ""}')
    [ "$result" = "" ]
}

@test "extract_effort returns raw value for uppercase effort" {
    result=$(extract_effort '{"effort": "HIGH"}')
    [ "$result" = "HIGH" ]
}

@test "extract_effort returns empty when effort is numeric" {
    result=$(extract_effort '{"effort": 42}')
    [ "$result" = "" ]
}

@test "extract_effort returns empty when effort is boolean" {
    result=$(extract_effort '{"effort": true}')
    [ "$result" = "" ]
}

@test "extract_effort returns empty when effort is null" {
    result=$(extract_effort '{"effort": null}')
    [ "$result" = "" ]
}

@test "extract_effort handles effort as first field in JSON" {
    result=$(extract_effort '{"effort":"low","trust":"medium"}')
    [ "$result" = "low" ]
}

@test "extract_effort handles effort as last field in JSON" {
    result=$(extract_effort '{"trust":"medium","effort":"high"}')
    [ "$result" = "high" ]
}

@test "extract_effort with nested object returns first match" {
    result=$(extract_effort '{"outer":{"effort":"high"},"effort":"low"}')
    # grep -o returns all matches; first line from pipeline is "high" (nested), second is "low"
    # The pipeline outputs both on separate lines; capture first line only
    first_line=$(echo "$result" | head -1)
    [ "$first_line" = "high" ]
}

# --- E2E: should_invoke_plan_builder effort propagation ---

@test "E2E: should_invoke_plan_builder propagates effort=low to INVOKE args" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{"effort":"low"}'
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path")
    [[ "$result" == *"low revise"* ]]
}

@test "E2E: should_invoke_plan_builder propagates effort=medium to INVOKE args" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{"effort":"medium"}'
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path")
    [[ "$result" == *"medium revise"* ]]
}

@test "E2E: should_invoke_plan_builder propagates effort=high to INVOKE args" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{"effort":"high"}'
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path")
    [[ "$result" == *"high revise"* ]]
}

@test "E2E: should_invoke_plan_builder propagates empty effort when key missing" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{"trust":"medium"}'
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path")
    # Empty effort produces "agent-id  revise" (double space between agent-id and revise)
    [[ "$result" == *"INVOKE:agent-id  revise"* ]]
}

@test "E2E: should_invoke_plan_builder propagates invalid effort unchecked" {
    printf '## Goal\n\nSome goal text.\n' > "${PLAN_MD}"
    create_config_mock '{"effort":"bogus"}'
    result=$(should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-id" "/issue/path")
    [[ "$result" == *"bogus revise"* ]]
}
