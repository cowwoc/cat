#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for WAVES_COUNT boundary detection in work-implement-agent.
#
# The grep command under test (from plugin/skills/work-implement-agent/first-use.md):
#   WAVES_COUNT=$(grep -c '^### Wave ' "$PLAN_MD") && echo "WAVES_COUNT=${WAVES_COUNT}"

setup() {
    TMPDIR="$(mktemp -d)"
    PLAN_MD="${TMPDIR}/plan.md"
    HELPER_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
    source "${HELPER_DIR}/waves-count-helper.bash"
}

teardown() {
    rm -rf "${TMPDIR:-}"
}

# --- detect_waves_count tests ---

@test "detect_waves_count returns 0 for empty plan.md" {
    > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

@test "detect_waves_count returns 0 for plan with no waves section" {
    printf '## Goal\nSome goal text\n\n## Post-conditions\nSome conditions\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

@test "detect_waves_count returns 1 for plan with one wave" {
    printf '## Sub-Agent Waves\n\n### Wave 1\nStep 1 content\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=1" ]
}

@test "detect_waves_count returns 2 for plan with two waves" {
    printf '## Sub-Agent Waves\n\n### Wave 1\nContent\n\n### Wave 2\nContent\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=2" ]
}

@test "detect_waves_count returns 5 for plan with five waves" {
    printf '## Sub-Agent Waves\n\n### Wave 1\n\n### Wave 2\n\n### Wave 3\n\n### Wave 4\n\n### Wave 5\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=5" ]
}

@test "detect_waves_count returns 0 for wrong header level (## Wave 1)" {
    printf '## Wave 1\nContent\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

@test "detect_waves_count returns 0 for wrong header level (#### Wave 1)" {
    printf '#### Wave 1\nContent\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

@test "detect_waves_count returns 0 for leading whitespace before ### Wave" {
    printf ' ### Wave 1\nContent\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

@test "detect_waves_count returns 0 for missing space after ### Wave" {
    printf '### Wave1\nContent\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

@test "detect_waves_count counts only ### Wave headers, ignores other ### headers" {
    printf '### Wave 1\n\n### Implementation Notes\n\n### Wave 2\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=2" ]
}

@test "detect_waves_count returns 0 for whitespace-only file" {
    printf '   \n\t\n  \n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

@test "detect_waves_count handles plan with Execution Steps instead of waves" {
    printf '## Execution Steps\n\n### Step 1\nDo something\n\n### Step 2\nDo more\n' > "${PLAN_MD}"
    result=$(detect_waves_count "${PLAN_MD}")
    [ "$result" = "WAVES_COUNT=0" ]
}

# --- classify_wave_execution tests ---

@test "classify_wave_execution returns single for 0 waves" {
    result=$(classify_wave_execution 0)
    [ "$result" = "single" ]
}

@test "classify_wave_execution returns single for 1 wave" {
    result=$(classify_wave_execution 1)
    [ "$result" = "single" ]
}

@test "classify_wave_execution returns parallel for 2 waves" {
    result=$(classify_wave_execution 2)
    [ "$result" = "parallel" ]
}

@test "classify_wave_execution returns parallel for 10 waves" {
    result=$(classify_wave_execution 10)
    [ "$result" = "parallel" ]
}

# --- build_subagent_prompt relay prohibition tests ---

@test "build_subagent_prompt includes PLAN_MD_PATH" {
    result=$(build_subagent_prompt "/tmp/test/plan.md" 3)
    [[ "$result" == *"PLAN_MD_PATH: /tmp/test/plan.md"* ]]
}

@test "build_subagent_prompt does not embed WAVES_COUNT value" {
    result=$(build_subagent_prompt "/tmp/test/plan.md" 3)
    [[ "$result" != *"WAVES_COUNT="* ]]
    [[ "$result" != *" 3 "* ]]
    [[ "$result" != *" 3"  ]]
}

# --- E2E tests (detect + classify combined) ---

@test "E2E: empty plan yields single execution" {
    > "${PLAN_MD}"
    output=$(detect_waves_count "${PLAN_MD}")
    count="${output#WAVES_COUNT=}"
    result=$(classify_wave_execution "$count")
    [ "$result" = "single" ]
}

@test "E2E: one-wave plan yields single execution" {
    printf '## Sub-Agent Waves\n\n### Wave 1\nContent\n' > "${PLAN_MD}"
    output=$(detect_waves_count "${PLAN_MD}")
    count="${output#WAVES_COUNT=}"
    result=$(classify_wave_execution "$count")
    [ "$result" = "single" ]
}

@test "E2E: two-wave plan yields parallel execution" {
    printf '## Sub-Agent Waves\n\n### Wave 1\nContent\n\n### Wave 2\nContent\n' > "${PLAN_MD}"
    output=$(detect_waves_count "${PLAN_MD}")
    count="${output#WAVES_COUNT=}"
    result=$(classify_wave_execution "$count")
    [ "$result" = "parallel" ]
}

@test "E2E: malformed wave headers yield single execution" {
    printf '## Wave 1\nContent\n\n#### Wave 2\nContent\n' > "${PLAN_MD}"
    output=$(detect_waves_count "${PLAN_MD}")
    count="${output#WAVES_COUNT=}"
    result=$(classify_wave_execution "$count")
    [ "$result" = "single" ]
}
