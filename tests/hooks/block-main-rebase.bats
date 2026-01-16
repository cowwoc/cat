#!/usr/bin/env bats
# Tests for bash_handlers/block_main_rebase.py - Prevents git rebase on main branch

load '../test_helper'

setup() {
    setup_git_repo
    # Create a main branch for testing
    git checkout -b main 2>/dev/null || git checkout main 2>/dev/null || true
}

teardown() {
    teardown_test_dir
}

# Helper to create hook input JSON for Bash tool
create_bash_hook_json() {
    local command="$1"
    # Escape backslashes and quotes for JSON
    command=$(echo "$command" | sed 's/\\/\\\\/g; s/"/\\"/g')
    echo "{\"hook_event_name\": \"PreToolUse\", \"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"$command\"}}"
}

# Helper to run hook with JSON input via Python dispatcher
run_hook_with_command() {
    local command="$1"
    local json=$(create_bash_hook_json "$command")
    echo "$json" | python3 "$HOOKS_DIR/get-bash-pretool-output.py"
}

# ============================================================================
# Block Rebase on Main
# ============================================================================

@test "block-main-rebase: blocks git rebase on main branch" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "git rebase feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-main-rebase: blocks git rebase -i on main branch" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "git rebase -i HEAD~3"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-main-rebase: blocks git rebase --onto on main branch" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "git rebase --onto main feature1 feature2"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

# ============================================================================
# Allow Rebase on Feature Branch
# ============================================================================

@test "block-main-rebase: allows git rebase on feature branch" {
    cd "$TEST_TEMP_DIR"
    git checkout -b feature-test
    run run_hook_with_command "git rebase main"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "block-main-rebase: allows git rebase -i on feature branch" {
    cd "$TEST_TEMP_DIR"
    git checkout -b feature-test
    run run_hook_with_command "git rebase -i HEAD~2"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

# ============================================================================
# Allow Other Git Commands on Main
# ============================================================================

@test "block-main-rebase: allows git commit on main" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "git commit -m 'test'"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "block-main-rebase: allows git merge --ff-only on main" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "git merge --ff-only feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "block-main-rebase: allows git pull on main" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "git pull origin main"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

# ============================================================================
# Non-Bash Tool Handling
# ============================================================================

@test "block-main-rebase: ignores non-Bash tools" {
    local json='{"hook_event_name": "PreToolUse", "tool_name": "Read", "tool_input": {"file_path": "test.txt"}}'
    run bash -c 'echo "$1" | python3 '"$HOOKS_DIR"'/get-bash-pretool-output.py' -- "$json"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "block-main-rebase: handles empty command" {
    run run_hook_with_command ""
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
}

@test "block-main-rebase: rebase in middle of command chain blocked on main" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "echo test && git rebase main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

# ============================================================================
# Guidance Message Tests
# ============================================================================

@test "block-main-rebase: includes guidance in error message" {
    cd "$TEST_TEMP_DIR"
    run run_hook_with_command "git rebase feature"
    [ "$status" -eq 0 ]
    # Should include guidance about what to do instead
    [[ "$output" == *"worktree"* ]] || [[ "$output" == *"BLOCKED"* ]] || [[ "$output" == *"block"* ]]
}
