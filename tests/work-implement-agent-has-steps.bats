#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for the HAS_STEPS detection logic used in work-implement-agent Step 4.
#
# The grep command under test (from plugin/skills/work-implement-agent/first-use.md):
#   grep -qE '^## (Sub-Agent Waves|Execution Steps)' "${PLAN_MD}" && \
#   echo "hasSteps=true" || echo "hasSteps=false"

setup() {
    TMPDIR="$(mktemp -d)"
    PLAN_MD="${TMPDIR}/plan.md"
}

teardown() {
    rm -rf "${TMPDIR:-}"
}

# Helper: run the HAS_STEPS detection against PLAN_MD and return hasSteps=true or hasSteps=false.
run_detection() {
    grep -qE '^## (Sub-Agent Waves|Execution Steps)' "${PLAN_MD}" && \
    echo "hasSteps=true" || echo "hasSteps=false"
}

@test "Sub-Agent Waves header: hasSteps=true" {
    printf '## Sub-Agent Waves\n' > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=true" ]
}

@test "Execution Steps header: hasSteps=true" {
    printf '## Execution Steps\n' > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=true" ]
}

@test "neither header present (only Goal and Post-conditions): hasSteps=false" {
    printf '## Goal\n## Post-conditions\n' > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=false" ]
}

@test "empty file: hasSteps=false" {
    > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=false" ]
}

@test "leading whitespace before ## Sub-Agent Waves: hasSteps=false" {
    printf ' ## Sub-Agent Waves\n' > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=false" ]
}

@test "wrong header level ### Sub-Agent Waves: hasSteps=false" {
    printf '### Sub-Agent Waves\n' > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=false" ]
}

@test "both valid headers present: hasSteps=true" {
    printf '## Sub-Agent Waves\n## Execution Steps\n' > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=true" ]
}

@test "whitespace-only file: hasSteps=false" {
    printf '   \n\t\n' > "${PLAN_MD}"
    result=$(run_detection)
    [ "$result" = "hasSteps=false" ]
}
