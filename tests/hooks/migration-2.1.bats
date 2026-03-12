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
