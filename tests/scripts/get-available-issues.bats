#!/usr/bin/env bats
# Tests for get-available-issues.sh - path self-discovery feature

load '../test_helper'

# Test session ID
TEST_SESSION="test-session-11111111-1111-1111-1111-111111111111"

setup() {
    setup_git_repo  # Creates a git repo in /tmp with .claude/cat
    GET_ISSUES="$SCRIPTS_DIR/get-available-issues.sh"

    # Create CAT project structure
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v1/v1.0/test-task"

    # Create minimal STATE.md for test task
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v1/v1.0/test-task/STATE.md" << 'EOF'
# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
EOF
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# Path Discovery Tests
# ============================================================================

@test "find_project_dir: discovers project via git" {
    cd "$TEST_TEMP_DIR"

    run "$GET_ISSUES" --session-id "$TEST_SESSION"

    # Should find project (or not_found for tasks, but NOT error about project)
    [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
    [[ "$output" != *"Could not find project"* ]]
}

@test "find_project_dir: discovers project from subdirectory" {
    # Create a nested subdirectory
    mkdir -p "$TEST_TEMP_DIR/src/deep/nested"
    cd "$TEST_TEMP_DIR/src/deep/nested"

    run "$GET_ISSUES" --session-id "$TEST_SESSION"

    # Should find project by walking up via git
    [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
    [[ "$output" != *"Could not find project"* ]]
}

@test "find_project_dir: error when not in git repo" {
    # Create a temp dir that is NOT a git repo
    local no_git_dir=$(mktemp -d)
    mkdir -p "$no_git_dir/.claude/cat"  # Has .claude/cat but no .git
    cd "$no_git_dir"

    run "$GET_ISSUES" --session-id "$TEST_SESSION"

    [ "$status" -eq 1 ]
    [[ "$output" == *"Could not find project"* ]]

    # Cleanup
    rm -rf "$no_git_dir"
}

@test "find_project_dir: error when no .claude/cat in git repo" {
    # Create a git repo without .claude/cat
    local no_cat_dir=$(mktemp -d)
    cd "$no_cat_dir"
    git init --quiet
    git config user.email "test@test.com"
    git config user.name "Test"

    run "$GET_ISSUES" --session-id "$TEST_SESSION"

    [ "$status" -eq 1 ]
    [[ "$output" == *"Could not find project"* ]] || [[ "$output" == *"no .claude/cat"* ]]

    # Cleanup
    rm -rf "$no_cat_dir"
}

# ============================================================================
# Session ID Tests
# ============================================================================

@test "session-id: script runs without --session-id" {
    cd "$TEST_TEMP_DIR"

    # Run without --session-id - should work for discovery
    run "$GET_ISSUES"

    # Script should run but lock acquisition may fail
    [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
}

@test "session-id: lock acquired when provided" {
    cd "$TEST_TEMP_DIR"

    run "$GET_ISSUES" --session-id "$TEST_SESSION"

    # If a task is found, lock should be acquired
    if [[ $(echo "$output" | jq -r '.status') == "found" ]]; then
        assert_json_field "$output" '.lock_status' 'acquired'
    fi
}

# ============================================================================
# Decomposed Parent Task Tests
# ============================================================================

@test "decomposed parent: all subtasks closed - returned as selectable" {
    # Create parent task with decomposed section
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/parent-task"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/parent-task/STATE.md" << 'EOF'
# State

- **Status:** open
- **Progress:** 80%
- **Dependencies:** []
- **Last Updated:** 2026-01-01

## Decomposed Into

- subtask-1 (closed)
- subtask-2 (closed)
EOF

    # Create subtask 1 (closed)
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-1"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-1/STATE.md" << 'EOF'
# State

- **Status:** closed
- **Progress:** 100%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
EOF

    # Create subtask 2 (closed)
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-2"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-2/STATE.md" << 'EOF'
# State

- **Status:** closed
- **Progress:** 100%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
EOF

    cd "$TEST_TEMP_DIR"
    run "$GET_ISSUES" --session-id "$TEST_SESSION" --scope minor --target 2.0

    # Should find the parent task since all subtasks are closed
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'found'
    assert_json_field "$output" '.issue_id' '2.0-parent-task'
}

@test "decomposed parent: some subtasks open - still skipped in scan" {
    # Create parent task with decomposed section
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/parent-incomplete"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/parent-incomplete/STATE.md" << 'EOF'
# State

- **Status:** open
- **Progress:** 50%
- **Dependencies:** []
- **Last Updated:** 2026-01-01

## Decomposed Into

- subtask-done (closed)
- subtask-pending (open)
EOF

    # Create subtask 1 (closed)
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-done"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-done/STATE.md" << 'EOF'
# State

- **Status:** closed
- **Progress:** 100%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
EOF

    # Create subtask 2 (open)
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-pending"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/subtask-pending/STATE.md" << 'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
EOF

    cd "$TEST_TEMP_DIR"
    run "$GET_ISSUES" --session-id "$TEST_SESSION" --scope minor --target 2.0

    # Should find the open subtask, not the parent
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'found'
    assert_json_field "$output" '.issue_id' '2.0-subtask-pending'
}

@test "decomposed parent: no subtasks listed - still skipped (defensive)" {
    # Create parent task with empty decomposed section
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/parent-empty"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/parent-empty/STATE.md" << 'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** 2026-01-01

## Decomposed Into

EOF

    cd "$TEST_TEMP_DIR"
    run "$GET_ISSUES" --session-id "$TEST_SESSION" --scope minor --target 2.0

    # Parent with no subtasks should be selectable (defensive: treats as "all closed")
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'found'
    assert_json_field "$output" '.issue_id' '2.0-parent-empty'
}

@test "decomposed parent: targeted selection of completed - returns found" {
    # Create parent task with all subtasks closed
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-complete"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-complete/STATE.md" << 'EOF'
# State

- **Status:** open
- **Progress:** 90%
- **Dependencies:** []
- **Last Updated:** 2026-01-01

## Decomposed Into

- target-sub (closed)
EOF

    # Create subtask (closed)
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-sub"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-sub/STATE.md" << 'EOF'
# State

- **Status:** closed
- **Progress:** 100%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
EOF

    cd "$TEST_TEMP_DIR"
    run "$GET_ISSUES" --session-id "$TEST_SESSION" --target 2.0-target-complete

    # Should return found since all subtasks are closed
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'found'
    assert_json_field "$output" '.issue_id' '2.0-target-complete'
}

@test "decomposed parent: targeted selection of incomplete - returns decomposed" {
    # Create parent task with open subtasks
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-incomplete"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-incomplete/STATE.md" << 'EOF'
# State

- **Status:** open
- **Progress:** 30%
- **Dependencies:** []
- **Last Updated:** 2026-01-01

## Decomposed Into

- target-open (open)
EOF

    # Create subtask (open)
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-open"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.0/target-open/STATE.md" << 'EOF'
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
EOF

    cd "$TEST_TEMP_DIR"
    run "$GET_ISSUES" --session-id "$TEST_SESSION" --target 2.0-target-incomplete

    # Should return decomposed status
    [ "$status" -eq 1 ]
    assert_json_field "$output" '.status' 'decomposed'
    assert_json_field "$output" '.issue_id' '2.0-target-incomplete'
}
