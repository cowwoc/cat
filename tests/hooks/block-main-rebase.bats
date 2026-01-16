#!/usr/bin/env bats
# Tests for hooks/block-main-rebase.sh - Prevents git rebase on main branch

load '../test_helper'

setup() {
    setup_git_repo
    source "$HOOKS_LIB_DIR/json-parser.sh"
}

teardown() {
    teardown_test_dir
}

# Helper to create hook input JSON for Bash tool
create_bash_hook_json() {
    local command="$1"
    # Escape special characters for JSON
    local escaped_command=$(echo "$command" | sed 's/"/\\"/g')
    echo "{\"hook_event_name\": \"PreToolUse\", \"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"$escaped_command\"}}"
}

# Helper to run hook with JSON input
run_hook_with_command() {
    local command="$1"
    local json=$(create_bash_hook_json "$command")
    echo "$json" | "$HOOKS_DIR/block-main-rebase.sh"
}

# ============================================================================
# Block Rebase on Main Branch
# ============================================================================

@test "block-main-rebase: blocks git rebase on main branch" {
    # Ensure we're on main
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "git rebase HEAD~1"
    [ "$status" -eq 0 ]
    [[ "$output" == *"deny"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-main-rebase: blocks git rebase -i on main branch" {
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "git rebase -i HEAD~3"
    [ "$status" -eq 0 ]
    [[ "$output" == *"deny"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-main-rebase: blocks git rebase --onto on main branch" {
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "git rebase --onto feature-branch HEAD~2"
    [ "$status" -eq 0 ]
    [[ "$output" == *"deny"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

# ============================================================================
# Allow Rebase on Feature Branches
# ============================================================================

@test "block-main-rebase: allows git rebase on feature branch" {
    cd "$TEST_TEMP_DIR"
    git checkout -q -b feature-branch

    run run_hook_with_command "git rebase main"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
    [[ "$output" != *"deny"* ]]
}

@test "block-main-rebase: allows git rebase -i on feature branch" {
    cd "$TEST_TEMP_DIR"
    git checkout -q -b fix/my-fix

    run run_hook_with_command "git rebase -i HEAD~3"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
    [[ "$output" != *"deny"* ]]
}

# ============================================================================
# Allow Non-Rebase Commands on Main
# ============================================================================

@test "block-main-rebase: allows git commit on main" {
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "git commit -m 'test'"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
    [[ "$output" != *"deny"* ]]
}

@test "block-main-rebase: allows git merge on main" {
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "git merge --ff-only feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
    [[ "$output" != *"deny"* ]]
}

@test "block-main-rebase: allows git pull on main" {
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "git pull origin main"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
    [[ "$output" != *"deny"* ]]
}

# ============================================================================
# Non-Bash Tool Handling
# ============================================================================

@test "block-main-rebase: ignores non-Bash tools" {
    local json='{"hook_event_name": "PreToolUse", "tool_name": "Read", "tool_input": {"file_path": "test.txt"}}'
    run bash -c 'echo "$1" | '"$HOOKS_DIR"'/block-main-rebase.sh' -- "$json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"{}"* ]]
    [[ "$output" != *"deny"* ]]
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "block-main-rebase: handles empty command" {
    local json='{"hook_event_name": "PreToolUse", "tool_name": "Bash", "tool_input": {"command": ""}}'
    run bash -c 'echo "$1" | '"$HOOKS_DIR"'/block-main-rebase.sh' -- "$json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"{}"* ]]
}

@test "block-main-rebase: rebase in middle of command chain blocked on main" {
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "echo test && git rebase HEAD~1"
    [ "$status" -eq 0 ]
    [[ "$output" == *"deny"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

# ============================================================================
# Message Content Tests
# ============================================================================

@test "block-main-rebase: includes guidance in error message" {
    cd "$TEST_TEMP_DIR"
    git checkout -q main 2>/dev/null || git checkout -q -b main

    run run_hook_with_command "git rebase HEAD~1"
    [ "$status" -eq 0 ]
    # Should mention worktree as alternative
    [[ "$output" == *"worktree"* ]] || [[ "$output" == *"task"* ]] || [[ "$output" == *"branch"* ]]
}
