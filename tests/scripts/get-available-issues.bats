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
