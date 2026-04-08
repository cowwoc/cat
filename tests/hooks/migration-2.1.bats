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
    export CLAUDE_PLUGIN_ROOT="$PLUGIN_ROOT"
}

teardown() {
    teardown_test_dir
}

# ─── Phase 1: Merge In Progress into Pending ─────────────────────────────────
# NOTE: setup_config_fixture creates config.json (post-migration state).
# Phase 3 tests that need a pre-migration state create cat-config.json directly.

@test "2.1.sh phase 1: skips when no version STATE.md files exist" {
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
}

# ─── Phase 2: Rename status values ───────────────────────────────────────────

@test "2.1.sh phase 2: renames pending to open in STATE.md" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** pending
- **Progress:** 0%
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "Status.*open" ".cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 2: renames completed to closed in STATE.md" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** completed
- **Progress:** 100%
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "Status.*closed" ".cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

# ─── Phase 3: Move version to VERSION file ───────────────────────────────────

@test "2.1.sh phase 3: moves last_migrated_version from config to VERSION file" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/VERSION" ]
    [ "$(tr -d '[:space:]' < .cat/VERSION)" = "2.0" ]
}

@test "2.1.sh phase 3: moves old version field from config to VERSION file" {
    echo '{"version": "1.0.10", "trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/VERSION" ]
    [ "$(tr -d '[:space:]' < .cat/VERSION)" = "1.0.10" ]
}

@test "2.1.sh phase 3: removes version field from config.json after migration" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '"last_migrated_version"' ".cat/config.json"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 3: skips when VERSION file already exists" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    echo "2.0" > "$TEST_TEMP_DIR/.cat/VERSION"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ "$(cat .cat/VERSION)" = "2.0" ]
}

@test "2.1.sh phase 3: skips when no version field in config" {
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ ! -f ".cat/VERSION" ]
}

# ─── Phase 4: Rename PLAN.md sections ────────────────────────────────────────

@test "2.1.sh phase 4: renames Acceptance Criteria to Post-conditions" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Acceptance Criteria

- [ ] First criterion
- [ ] Second criterion

## Execution Steps

1. Do the work
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Acceptance Criteria" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 4: merges Success Criteria into Post-conditions when both exist" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
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
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Success Criteria" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
    run grep "Additional criterion" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    postcond_line=$(grep -n "^## Post-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    additional_line=$(grep -n "Additional criterion" ".cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    [ "$additional_line" -gt "$postcond_line" ]
}

@test "2.1.sh phase 4: converts Gates with Entry/Exit subsections in-place" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
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
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Pre-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Gates" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
    # Verify Pre-conditions appears before Execution Steps (in-place, not at EOF)
    precond_line=$(grep -n "^## Pre-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    exec_line=$(grep -n "^## Execution Steps" ".cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    [ "$precond_line" -lt "$exec_line" ]
    postcond_line=$(grep -n "^## Post-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md" | cut -d: -f1)
    [ "$postcond_line" -lt "$exec_line" ]
}

@test "2.1.sh phase 4: renames Entry Gate to Pre-conditions" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Entry Gate

- Previous version complete

## Exit Gate

- All tasks complete
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Pre-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Post-conditions" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Entry Gate$" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
}

# ─── Phase 8: Execution Steps → Execution Waves ───────────────────────────────

@test "2.1.sh phase 8: renames heading and inserts Wave 1 subheading" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Steps

1. Install dependencies
2. Run tests
3. Deploy to staging
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Execution Waves" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^### Wave 1" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -eq 0 ]
    run grep "^## Execution Steps" ".cat/issues/v2/v2.1/test-issue/PLAN.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 8: preserves numbered step lines verbatim under Wave 1" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Steps

1. Install dependencies
2. Run tests
3. Deploy to staging
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    plan_file=".cat/issues/v2/v2.1/test-issue/PLAN.md"
    waves_line=$(grep -n "^## Execution Waves" "$plan_file" | cut -d: -f1)
    # All three numbered steps must appear after the Execution Waves heading
    step1_line=$(grep -n "^1\. Install dependencies" "$plan_file" | cut -d: -f1)
    step2_line=$(grep -n "^2\. Run tests" "$plan_file" | cut -d: -f1)
    step3_line=$(grep -n "^3\. Deploy to staging" "$plan_file" | cut -d: -f1)
    [ -n "$step1_line" ]
    [ -n "$step2_line" ]
    [ -n "$step3_line" ]
    [ "$step1_line" -gt "$waves_line" ]
    [ "$step2_line" -gt "$waves_line" ]
    [ "$step3_line" -gt "$waves_line" ]
}

@test "2.1.sh phase 8: preserves sub-bullets, file lists, code blocks, and inner headings" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Steps

1. Update configuration files
   - `config/app.yaml`
   - `config/db.yaml`
   Verify with:
   ```bash
   cat config/app.yaml
   ```
2. Run the test suite
   - Unit tests
   - Integration tests

### Notes

Keep these in mind.

3. Deploy
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    plan_file=".cat/issues/v2/v2.1/test-issue/PLAN.md"
    # Sub-bullets preserved
    run grep "config/app.yaml" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "config/db.yaml" "$plan_file"
    [ "$status" -eq 0 ]
    # Code block preserved
    run grep '```bash' "$plan_file"
    [ "$status" -eq 0 ]
    run grep 'cat config/app.yaml' "$plan_file"
    [ "$status" -eq 0 ]
    # Inner heading preserved
    run grep "^### Notes" "$plan_file"
    [ "$status" -eq 0 ]
    # All three steps present
    run grep "^1\. Update configuration files" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "^2\. Run the test suite" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "^3\. Deploy" "$plan_file"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 8: stops at the next top-level section boundary" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Steps

1. Step one
2. Step two

## Post-conditions

- All done
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    plan_file=".cat/issues/v2/v2.1/test-issue/PLAN.md"
    run grep "^## Post-conditions" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "^- All done" "$plan_file"
    [ "$status" -eq 0 ]
    # Post-conditions heading must appear after Execution Waves heading
    waves_line=$(grep -n "^## Execution Waves" "$plan_file" | cut -d: -f1)
    postcond_line=$(grep -n "^## Post-conditions" "$plan_file" | cut -d: -f1)
    [ "$postcond_line" -gt "$waves_line" ]
}

@test "2.1.sh phase 8: preserves content when Execution Steps is the last section" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Steps

1. Only step
   - with a sub-bullet
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    plan_file=".cat/issues/v2/v2.1/test-issue/PLAN.md"
    run grep "^## Execution Waves" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "^### Wave 1" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "^1\. Only step" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "   - with a sub-bullet" "$plan_file"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 8: skips already-migrated files (idempotent)" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Waves

### Wave 1

1. Already migrated step
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    plan_file=".cat/issues/v2/v2.1/test-issue/PLAN.md"
    # Should remain exactly as-is: Execution Waves present, no Execution Steps added
    run grep "^## Execution Waves" "$plan_file"
    [ "$status" -eq 0 ]
    run grep "^## Execution Steps" "$plan_file"
    [ "$status" -ne 0 ]
    run grep "^1\. Already migrated step" "$plan_file"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 8: leaves files without Execution Steps unchanged" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Post-conditions

- Done
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    plan_file=".cat/issues/v2/v2.1/test-issue/PLAN.md"
    run grep "^## Execution Waves" "$plan_file"
    [ "$status" -ne 0 ]
    run grep "^## Post-conditions" "$plan_file"
    [ "$status" -eq 0 ]
}

# ─── Phase 1 (move .claude/cat → .cat): actual directory move ────────────────

@test "2.1.sh phase 1: moves .claude/cat to .cat when .cat does not exist" {
    # Start fresh without the .cat directory setup_test_dir created
    rm -rf "$TEST_TEMP_DIR/.cat"
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** open
EOF

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d ".cat" ]
    [ ! -d ".claude/cat" ]
    [ -f ".cat/config.json" ]
}

@test "2.1.sh phase 1: updates STATE.md path references after .claude/cat move" {
    rm -rf "$TEST_TEMP_DIR/.cat"
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** open
- **Worktree path:** /workspace/.claude/cat/worktrees/test-issue
EOF

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '\.claude/cat' ".cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -ne 0 ]
    run grep '\.cat/worktrees' ".cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

# ─── Phase 7 (.gitignore): work/ pattern ──────────────────────────────────────

@test "2.1.sh phase 7: adds work/ to .gitignore when .gitignore already exists" {
    setup_config_fixture
    # Create .gitignore without work/ entry
    printf 'cat-config.local.json\n' > "$TEST_TEMP_DIR/.cat/.gitignore"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep 'work/' ".cat/.gitignore"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 7: does not duplicate work/ when already in .gitignore" {
    setup_config_fixture
    printf 'cat-config.local.json\nwork/\n' > "$TEST_TEMP_DIR/.cat/.gitignore"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    count=$(grep -c 'work/' ".cat/.gitignore" || true)
    [ "$count" -eq 1 ]
}

# ─── Phase 11: Migrate cross-session dirs to .cat/work/ ───────────────────────

# Helper to set up external storage path for Phase 11 tests.
# Requires CLAUDE_CONFIG_DIR and CLAUDE_PROJECT_DIR to be exported.
setup_phase11() {
    export CLAUDE_CONFIG_DIR="$TEST_TEMP_DIR/fake-config"
    ENCODED_PROJECT=$(echo "${CLAUDE_PROJECT_DIR}" | tr '/.' '-')
    OLD_PROJECT_CAT_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT}/cat"
    export OLD_PROJECT_CAT_DIR
    export ENCODED_PROJECT
}

@test "2.1.sh phase 11: idempotent when old locations absent and .cat/work/locks exists" {
    setup_config_fixture
    # .cat/work/locks already exists (setup_test_dir creates it)

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d ".cat/work/locks" ]
}

@test "2.1.sh phase 11: creates .cat/work/locks when .cat/locks is absent and no external dir" {
    setup_config_fixture
    # Remove the pre-existing work/locks so we test the mkdir path
    rm -rf "$TEST_TEMP_DIR/.cat/work"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d ".cat/work/locks" ]
    [ -d ".cat/work/worktrees" ]
}

@test "2.1.sh phase 11: moves .cat/locks/ to .cat/work/locks/" {
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "$TEST_TEMP_DIR/.cat/locks"
    printf '{"session_id":"test"}\n' > "$TEST_TEMP_DIR/.cat/locks/test-issue.lock"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/work/locks/test-issue.lock" ]
    [ ! -d ".cat/locks" ]
}

@test "2.1.sh phase 11: aborts when active locks exist in .cat/locks/" {
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "$TEST_TEMP_DIR/.cat/locks"
    touch "$TEST_TEMP_DIR/.cat/locks/active.lock"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -ne 0 ]
    [ -f ".cat/locks/active.lock" ]
}

@test "2.1.sh phase 11: moves external storage locks to .cat/work/locks/" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/locks"
    printf '{"session_id":"test"}\n' > "${OLD_PROJECT_CAT_DIR}/locks/test-issue.lock"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/work/locks/test-issue.lock" ]
    [ ! -f "${OLD_PROJECT_CAT_DIR}/locks/test-issue.lock" ]
}

@test "2.1.sh phase 11: aborts when active locks exist in external storage" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/locks"
    touch "${OLD_PROJECT_CAT_DIR}/locks/active.lock"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -ne 0 ]
    [ -f "${OLD_PROJECT_CAT_DIR}/locks/active.lock" ]
}

@test "2.1.sh phase 11: moves external storage worktrees to .cat/work/worktrees/" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/worktrees/feature-branch"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d ".cat/work/worktrees/feature-branch" ]
    [ ! -d "${OLD_PROJECT_CAT_DIR}/worktrees/feature-branch" ]
}

@test "2.1.sh phase 11: aborts when active worktrees exist in external storage" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/worktrees/active-task"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -ne 0 ]
    [ -d "${OLD_PROJECT_CAT_DIR}/worktrees/active-task" ]
}

@test "2.1.sh phase 11: migrates session-scoped verify dirs from external storage" {
    setup_phase11
    SESSION_ID="aaaabbbb-cccc-dddd-eeee-ffffaaaabbbb"
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT}/${SESSION_ID}/cat/verify"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d ".cat/work/verify/${SESSION_ID}" ]
    [ ! -d "${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT}/${SESSION_ID}/cat/verify" ]
}

@test "2.1.sh phase 11: skips verify dir with non-UUID session name" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT}/not-a-uuid/cat/verify"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d "${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT}/not-a-uuid/cat/verify" ]
    [ ! -d ".cat/work/verify/not-a-uuid" ]
}

@test "2.1.sh phase 11: updates STATE.md worktree path refs from external storage" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/locks"
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/STATE.md" <<EOF
# State

- **Status:** open
- **Worktree path:** ${OLD_PROJECT_CAT_DIR}/worktrees/test-issue
EOF

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '.cat/work/worktrees/test-issue' ".cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 11: removes empty old external directory" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/locks"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ ! -d "${OLD_PROJECT_CAT_DIR}" ]
}

@test "2.1.sh phase 11: idempotent on second run" {
    setup_phase11
    setup_config_fixture
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/locks"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    first_output="$output"

    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # On second run, migration should indicate already migrated or no changes
    [[ "$output" == *"already migrated"* ]] || [[ "$output" == *"0 files"* ]] || [[ -z "$output" ]]
}

# ─── Phase 16: Rename cat-config.json → config.json ──────────────────────────

@test "2.1.sh phase 16: renames cat-config.json to config.json" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/config.json" ]
    [ ! -f ".cat/cat-config.json" ]
    # Verify content preservation: config.json contains expected JSON from pre-migration cat-config.json
    run grep '"trust".*"medium"' ".cat/config.json"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 16: renames cat-config.local.json to config.local.json" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    echo '{"displayWidth": 100}' > "$TEST_TEMP_DIR/.cat/cat-config.local.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/config.local.json" ]
    [ ! -f ".cat/cat-config.local.json" ]
    # Verify content preservation: config.local.json contains expected JSON
    run grep '"displayWidth"' ".cat/config.local.json"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 16: updates .gitignore cat-config.local.json entry to config.local.json" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    printf 'cat-config.local.json\n' > "$TEST_TEMP_DIR/.cat/.gitignore"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep 'config\.local\.json' ".cat/.gitignore"
    [ "$status" -eq 0 ]
    run grep 'cat-config\.local\.json' ".cat/.gitignore"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 16: skips rename when config.json already exists" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    echo '{"trust": "high"}' > "$TEST_TEMP_DIR/.cat/config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # Both files must still exist (manual resolution required)
    [ -f ".cat/cat-config.json" ]
    [ -f ".cat/config.json" ]
}

@test "2.1.sh phase 16: idempotent when cat-config.json already absent" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/config.json" ]
}

@test "2.1.sh phase 16: E2E verify get-config-output effective reads migrated config.json" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]

    # Verify get-config-output can read the migrated config
    run "$CLAUDE_PLUGIN_ROOT/../client/target/jlink/bin/get-config-output" effective
    [ "$status" -eq 0 ]

    # Verify output contains expected trust value
    run grep '"trust"' <<< "$output"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 16: skips rename when cat-config.json missing (no .cat dir)" {
    # Create temp dir WITH .cat directory but no cat-config.json
    mkdir -p "$TEST_TEMP_DIR/.cat"
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # Should leave config.json untouched if cat-config.json doesn't exist
    [ -f ".cat/config.json" ]
    [ ! -f ".cat/cat-config.json" ]
}

@test "2.1.sh phase 16: handles conflict when both config.local.json files exist" {
    setup_config_fixture
    # Create both old and new local config files to simulate conflict
    echo '{"displayWidth": 100}' > "$TEST_TEMP_DIR/.cat/cat-config.local.json"
    echo '{"displayWidth": 120}' > "$TEST_TEMP_DIR/.cat/config.local.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # Both files should exist unchanged (conflict, no migration)
    [ -f ".cat/cat-config.local.json" ]
    [ -f ".cat/config.local.json" ]
    # Verify neither was modified by checking the original values are preserved
    run grep '"displayWidth": 100' ".cat/cat-config.local.json"
    [ "$status" -eq 0 ]
    run grep '"displayWidth": 120' ".cat/config.local.json"
    [ "$status" -eq 0 ]
}

# ─── Phase 17: Convert bare sub-issue names to qualified names ───────────────

@test "2.1.sh phase 17: converts bare names to qualified names in Decomposed Into section" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue/STATE.md" <<'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []

## Decomposed Into
- rename-config-java-core
- rename-config-plugin-docs
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "2\.1-rename-config-java-core" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
    run grep "2\.1-rename-config-plugin-docs" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
    # Bare names should be gone
    run grep "^- rename-config-java-core$" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 17: skips already-qualified names in Decomposed Into section" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue/STATE.md" <<'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []

## Decomposed Into
- 2.1-rename-config-java-core
- 2.1-rename-config-plugin-docs
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # Already-qualified names should be unchanged
    run grep "2\.1-rename-config-java-core" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
    # Should NOT have double-prefixed names
    run grep "2\.1-2\.1-rename-config-java-core" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 17: idempotent when run twice" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue/STATE.md" <<'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []

## Decomposed Into
- rename-config-java-core
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # After two runs, should be qualified exactly once
    run grep "2\.1-rename-config-java-core" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
    run grep "2\.1-2\.1-rename-config-java-core" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 17: skips STATE.md without Decomposed Into section" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/simple-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/simple-issue/STATE.md" <<'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # File should be unchanged (no Decomposed Into section added)
    run grep "Decomposed Into" ".cat/issues/v2/v2.1/simple-issue/STATE.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 17: preserves trailing description text after bare name" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue/STATE.md" <<'EOF'
# State

- **Status:** open
- **Progress:** 0%

## Decomposed Into
- rename-config-java-core (core Java files)
- rename-config-plugin-docs (docs and plugin files)
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "2\.1-rename-config-java-core (core Java files)" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
    run grep "2\.1-rename-config-plugin-docs (docs and plugin files)" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 17: does not modify content outside Decomposed Into section" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/parent-issue/STATE.md" <<'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** [some-dep]
- **Blocks:** [some-block]

## Decomposed Into
- rename-config-java-core

## Parallel Execution Plan

### Wave 1 (Concurrent)
| Issue | Dependencies |
|-------|-------------|
| rename-config-java-core | None |
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # The table entry outside the Decomposed Into section should NOT be prefixed
    run grep "| rename-config-java-core | None |" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
    # The Decomposed Into entry should be prefixed
    run grep "^- 2\.1-rename-config-java-core$" ".cat/issues/v2/v2.1/parent-issue/STATE.md"
    [ "$status" -eq 0 ]
}

# ─── Phase 26: Rename ## Sub-Agent Waves → ## Jobs and ### Wave N → ### Job N ─

@test "2.1.sh phase 26: renames Sub-Agent Waves section to Jobs in plan.md" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/plan.md" <<'EOF'
## Goal
Refactor something.

## Sub-Agent Waves

### Wave 1
Do the first thing.

### Wave 2
Do the second thing.
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^## Jobs$" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep "^### Job 1$" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep "^### Job 2$" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep "^## Sub-Agent Waves$" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 26: renames Wave N headers with suffix (e.g. Wave 1 (Concurrent))" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/plan.md" <<'EOF'
## Sub-Agent Waves

### Wave 1 (Concurrent)
Content.
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "^### Job 1 (Concurrent)$" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep "^### Wave 1" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 26: idempotent - skips plan.md already using ## Jobs" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/plan.md" <<'EOF'
## Goal
Already migrated.

## Jobs

### Job 1
Do the thing.
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # Content should be unchanged
    run grep "^## Jobs$" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -eq 0 ]
    run grep "^### Job 1$" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -eq 0 ]
    # No Sub-Agent Waves should have been introduced
    run grep "^## Sub-Agent Waves" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 26: leaves plan.md unchanged when it has neither Sub-Agent Waves nor Wave headers" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/plan.md" <<'EOF'
## Goal
Simple plan.

## Execution Steps
- Step 1: Do something
- Step 2: Do something else
EOF
    setup_config_fixture

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    # File should be unchanged - no Jobs section added
    run grep "^## Jobs" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -ne 0 ]
    run grep "^## Goal" ".cat/issues/v2/v2.1/test-issue/plan.md"
    [ "$status" -eq 0 ]
}
