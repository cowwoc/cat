#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for migration 2.1 phase 14 (rename ## Satisfies to ## Parent Requirements)

load '../test_helper'

setup() {
    setup_test_dir
    export CAT_PLUGIN_ROOT="$TEST_TEMP_DIR/plugin-under-test"
    cp -R "$PROJECT_ROOT/client/plugin/." "$CAT_PLUGIN_ROOT/"
    mkdir -p "$CAT_PLUGIN_ROOT/migrations/lib"
    cat > "$CAT_PLUGIN_ROOT/migrations/lib/utils.sh" <<'UTILS'
#!/usr/bin/env bash
log_migration() { printf '%s\n' "$*"; }
log_success() { printf '%s\n' "$*"; }
UTILS
    chmod +x "$CAT_PLUGIN_ROOT/migrations/2.1.sh" "$CAT_PLUGIN_ROOT/migrations/lib/utils.sh"
}

teardown() {
    teardown_test_dir
}

@test "2.1.sh phase 14: renames Satisfies to Parent Requirements for open issues" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/open-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/open-issue/STATE.md" <<'STATE'
# State

- **Status:** open
STATE
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/open-issue/PLAN.md" <<'PLAN'
# Plan

## Goal

Test feature

## Satisfies

- parent-1
PLAN
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CAT_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '^## Parent Requirements$' ".cat/issues/v2/v2.1/open-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep '^## Satisfies$' ".cat/issues/v2/v2.1/open-issue/plan.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 14: skips closed issues" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/closed-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/closed-issue/STATE.md" <<'STATE'
# State

- **Status:** closed
STATE
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/closed-issue/PLAN.md" <<'PLAN'
# Plan

## Goal

Done work

## Satisfies

- parent-closed
PLAN
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CAT_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '^## Satisfies$' ".cat/issues/v2/v2.1/closed-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep '^## Parent Requirements$' ".cat/issues/v2/v2.1/closed-issue/plan.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 14: is idempotent across two runs" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/open-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/open-issue/STATE.md" <<'STATE'
# State

- **Status:** open
STATE
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/open-issue/PLAN.md" <<'PLAN'
# Plan

## Goal

Idempotency

## Satisfies

- parent-1
PLAN
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CAT_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '^## Parent Requirements$' ".cat/issues/v2/v2.1/open-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep '^## Satisfies$' ".cat/issues/v2/v2.1/open-issue/plan.md"
    [ "$status" -ne 0 ]

    run bash "$CAT_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [[ "$output" == *"Phase 14 complete: 0 files changed"* || "$output" == *"No issue-level PLAN.md files found - skipping phase 14"* ]]
}

@test "2.1.sh phase 14: logs warning and skips issue when STATE.md is missing" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/no-state-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/no-state-issue/PLAN.md" <<'PLAN'
# Plan

## Goal

No state

## Satisfies

- parent-1
PLAN
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CAT_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING: Missing STATE.md for issue"* ]]
    run grep '^## Satisfies$' ".cat/issues/v2/v2.1/no-state-issue/plan.md"
    [ "$status" -eq 0 ]
}
