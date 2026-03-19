#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests verifying that learn phase files use the correct output schema:
#   - JSON output templates contain internal_summary (not user_summary)
#   - Each phase file uses user-centric preamble framing

load "${BATS_TEST_DIRNAME}/../../../../tests/test_helper"

LEARN_DIR="${BATS_TEST_DIRNAME}/.."

@test "phase-investigate.md contains internal_summary field" {
    grep -q '"internal_summary"' "${LEARN_DIR}/phase-investigate.md"
}

@test "phase-investigate.md does not contain user_summary field" {
    run grep -q '"user_summary"' "${LEARN_DIR}/phase-investigate.md"
    [ "$status" -ne 0 ]
}

@test "phase-investigate.md contains user-centric preamble" {
    grep -q 'The user wants to receive this exact JSON object' "${LEARN_DIR}/phase-investigate.md"
}

@test "phase-investigate.md does not contain 'for display to user between phases'" {
    run grep -q 'for display to user between phases' "${LEARN_DIR}/phase-investigate.md"
    [ "$status" -ne 0 ]
}

@test "phase-analyze.md contains internal_summary field" {
    grep -q '"internal_summary"' "${LEARN_DIR}/phase-analyze.md"
}

@test "phase-analyze.md does not contain user_summary field" {
    run grep -q '"user_summary"' "${LEARN_DIR}/phase-analyze.md"
    [ "$status" -ne 0 ]
}

@test "phase-analyze.md contains user-centric preamble" {
    grep -q 'The user wants to receive this exact JSON object' "${LEARN_DIR}/phase-analyze.md"
}

@test "phase-analyze.md does not contain 'for display to user between phases'" {
    run grep -q 'for display to user between phases' "${LEARN_DIR}/phase-analyze.md"
    [ "$status" -ne 0 ]
}

@test "phase-prevent.md contains internal_summary field" {
    grep -q '"internal_summary"' "${LEARN_DIR}/phase-prevent.md"
}

@test "phase-prevent.md does not contain user_summary field" {
    run grep -q '"user_summary"' "${LEARN_DIR}/phase-prevent.md"
    [ "$status" -ne 0 ]
}

@test "phase-prevent.md contains user-centric preamble" {
    grep -q 'The user wants to receive this exact JSON object' "${LEARN_DIR}/phase-prevent.md"
}

@test "phase-prevent.md does not contain 'for display to user between phases'" {
    run grep -q 'for display to user between phases' "${LEARN_DIR}/phase-prevent.md"
    [ "$status" -ne 0 ]
}

@test "first-use.md documents internal_summary field in phase_summaries schema" {
    grep -q '"internal_summary"' "${LEARN_DIR}/first-use.md"
}
