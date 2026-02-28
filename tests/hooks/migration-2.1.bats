#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for plugin/migrations/2.1.sh (consolidated migration)

load '../test_helper'

setup() {
    setup_test_dir
    mkdir -p "$TEST_TEMP_DIR/.claude/cat"
    export CLAUDE_PLUGIN_ROOT="$PLUGIN_ROOT"
}

teardown() {
    teardown_test_dir
}

# ─── Phase 1: Merge In Progress into Pending ─────────────────────────────────

@test "2.1.sh phase 1: skips when no version STATE.md files exist" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
}

# ─── Phase 2: Rename status values ───────────────────────────────────────────

@test "2.1.sh phase 2: renames pending to open in STATE.md" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** pending
- **Progress:** 0%
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "Status.*open" ".claude/cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 2: renames completed to closed in STATE.md" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** completed
- **Progress:** 100%
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "Status.*closed" ".claude/cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

# ─── Phase 3: Move version to VERSION file ───────────────────────────────────

@test "2.1.sh phase 3: moves last_migrated_version from config to VERSION file" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".claude/cat/VERSION" ]
    [ "$(cat .claude/cat/VERSION | tr -d '[:space:]')" = "2.0" ]
}

@test "2.1.sh phase 3: moves old version field from config to VERSION file" {
    echo '{"version": "1.0.10", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".claude/cat/VERSION" ]
    [ "$(cat .claude/cat/VERSION | tr -d '[:space:]')" = "1.0.10" ]
}

@test "2.1.sh phase 3: removes version field from cat-config.json after migration" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '"last_migrated_version"' ".claude/cat/cat-config.json"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 3: skips when VERSION file already exists" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"
    echo "2.0" > "$TEST_TEMP_DIR/.claude/cat/VERSION"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ "$(cat .claude/cat/VERSION)" = "2.0" ]
}

@test "2.1.sh phase 3: skips when no version field in config" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ ! -f ".claude/cat/VERSION" ]
}

# ─── Phase 4: Rename PLAN.md sections ────────────────────────────────────────

@test "2.1.sh phase 4: renames Acceptance Criteria to Post-conditions" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Acceptance Criteria

- [ ] First criterion
- [ ] Second criterion

## Execution Steps

1. Do the work
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Acceptance Criteria" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 4: merges Success Criteria into Post-conditions when both exist" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Acceptance Criteria

- [ ] First criterion

## Success Criteria

- [ ] Additional criterion

## Execution Steps

1. Do the work
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Success Criteria" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
    run grep "Additional criterion" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    postcond_line=$(grep -n "^## Post-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    additional_line=$(grep -n "Additional criterion" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    [ "$additional_line" -gt "$postcond_line" ]
}

@test "2.1.sh phase 4: converts Gates with Entry/Exit subsections in-place" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Gates

### Entry

- Previous version complete

### Exit

- All tasks complete

## Execution Steps

1. Do the work
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Pre-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Gates" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
    # Verify Pre-conditions appears before Execution Steps (in-place, not at EOF)
    precond_line=$(grep -n "^## Pre-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    exec_line=$(grep -n "^## Execution Steps" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    [ "$precond_line" -lt "$exec_line" ]
    postcond_line=$(grep -n "^## Post-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    [ "$postcond_line" -lt "$exec_line" ]
}

@test "2.1.sh phase 4: renames Entry Gate to Pre-conditions" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Entry Gate

- Previous version complete

## Exit Gate

- All tasks complete
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Pre-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Entry Gate$" ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
}
