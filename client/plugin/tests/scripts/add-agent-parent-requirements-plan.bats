#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Regression test for cat:add issue_create plan skeleton.
# Verifies that lightweight plan.md generation includes "## Parent Requirements"
# and does not include the legacy "## Satisfies" heading.

TEST_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
PROJECT_ROOT="$(cd "$TEST_DIR/../../../.." && pwd)"
FIRST_USE_MD="$PROJECT_ROOT/client/plugin/skills/common/add/first-use.md"

extract_lightweight_plan_block() {
    awk '
        /^2\. Write the lightweight plan\.md to / {capture=1}
        /^3\. Write the index\.json content to / {capture=0}
        capture {print}
    ' "$FIRST_USE_MD"
}

@test "lightweight plan skeleton includes Parent Requirements heading" {
    run extract_lightweight_plan_block
    [ "$status" -eq 0 ]
    echo "$output" | grep -q '^## Parent Requirements$'
}

@test "lightweight plan skeleton sets Parent Requirements to None by default" {
    run extract_lightweight_plan_block
    [ "$status" -eq 0 ]
    echo "$output" | grep -q '^None$'
}

@test "lightweight plan skeleton does not use legacy Satisfies heading" {
    run extract_lightweight_plan_block
    [ "$status" -eq 0 ]
    run bash -lc "printf '%s\n' \"$output\" | grep -q '^## Satisfies$'"
    [ "$status" -ne 0 ]
}
