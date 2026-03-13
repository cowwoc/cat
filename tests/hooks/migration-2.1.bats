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
    mkdir -p "$TEST_TEMP_DIR/.cat"
    export CLAUDE_PLUGIN_ROOT="$PLUGIN_ROOT"
}

teardown() {
    teardown_test_dir
}

# ─── Phase 1: Merge In Progress into Pending ─────────────────────────────────

@test "2.1.sh phase 1: skips when no version STATE.md files exist" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    [ "$(cat .cat/VERSION | tr -d '[:space:]')" = "2.0" ]
}

@test "2.1.sh phase 3: moves old version field from config to VERSION file" {
    echo '{"version": "1.0.10", "trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".cat/VERSION" ]
    [ "$(cat .cat/VERSION | tr -d '[:space:]')" = "1.0.10" ]
}

@test "2.1.sh phase 3: removes version field from cat-config.json after migration" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep '"last_migrated_version"' ".cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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

# ─── Phase 7: Execution Steps → Execution Waves ───────────────────────────────

@test "2.1.sh phase 7: renames heading and inserts Wave 1 subheading" {
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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

@test "2.1.sh phase 7: preserves numbered step lines verbatim under Wave 1" {
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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

@test "2.1.sh phase 7: preserves sub-bullets, file lists, code blocks, and inner headings" {
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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

@test "2.1.sh phase 7: stops at the next top-level section boundary" {
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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

@test "2.1.sh phase 7: preserves content when Execution Steps is the last section" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Steps

1. Only step
   - with a sub-bullet
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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

@test "2.1.sh phase 7: skips already-migrated files (idempotent)" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Execution Waves

### Wave 1

1. Already migrated step
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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

@test "2.1.sh phase 7: leaves files without Execution Steps unchanged" {
    mkdir -p "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.cat/issues/v2/v2.1/test-issue/PLAN.md" <<'EOF'
# Plan

## Goal

Test feature

## Post-conditions

- Done
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"

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
    [ -f ".cat/cat-config.json" ]
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    # Create .gitignore without work/ entry
    printf 'cat-config.local.json\n' > "$TEST_TEMP_DIR/.cat/.gitignore"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep 'work/' ".cat/.gitignore"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 7: does not duplicate work/ when already in .gitignore" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    # .cat/work/locks already exists (setup_test_dir creates it)

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d ".cat/work/locks" ]
}

@test "2.1.sh phase 11: creates .cat/work/locks when .cat/locks is absent and no external dir" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    # Remove the pre-existing work/locks so we test the mkdir path
    rm -rf "$TEST_TEMP_DIR/.cat/work"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -d ".cat/work/locks" ]
    [ -d ".cat/work/worktrees" ]
}

@test "2.1.sh phase 11: moves .cat/locks/ to .cat/work/locks/" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
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
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/locks"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ ! -d "${OLD_PROJECT_CAT_DIR}" ]
}

@test "2.1.sh phase 11: idempotent on second run" {
    setup_phase11
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
    rm -rf "$TEST_TEMP_DIR/.cat/work"
    mkdir -p "${OLD_PROJECT_CAT_DIR}/locks"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]

    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [[ "$output" == *"already migrated"* ]]
}
