#!/usr/bin/env bats
# Tests for hooks/verify-destructive-operations.sh - Warns about destructive operations

load '../test_helper'

setup() {
    setup_test_dir
    source "$HOOKS_LIB_DIR/json-parser.sh"
}

teardown() {
    teardown_test_dir
}

# Helper to create UserPromptSubmit hook JSON
create_prompt_json() {
    local message="$1"
    # Escape special characters for JSON
    local escaped_message=$(echo "$message" | sed 's/"/\\"/g' | sed 's/\\/\\\\/g')
    echo "{\"hook_event_name\": \"UserPromptSubmit\", \"session_id\": \"test-session\", \"message\": \"$escaped_message\"}"
}

# Helper to run hook with user prompt
run_hook_with_prompt() {
    local message="$1"
    local json=$(create_prompt_json "$message")
    echo "$json" | "$HOOKS_DIR/verify-destructive-operations.sh"
}

# ============================================================================
# Detect Destructive Git Operations
# ============================================================================

@test "verify-destructive-operations: detects git rebase" {
    run run_hook_with_prompt "Please git rebase the branch"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
    [[ "$output" == *"VERIFICATION"* ]]
}

@test "verify-destructive-operations: detects git reset" {
    run run_hook_with_prompt "Run git reset --hard HEAD~1"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects git checkout" {
    run run_hook_with_prompt "git checkout the old version"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

# ============================================================================
# Detect File Operations
# ============================================================================

@test "verify-destructive-operations: detects delete keyword" {
    run run_hook_with_prompt "Delete the old configuration files"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects rm command" {
    run run_hook_with_prompt "Please rm the unused files"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

# ============================================================================
# Detect Refactoring Operations
# ============================================================================

@test "verify-destructive-operations: detects refactor" {
    run run_hook_with_prompt "Refactor the database module"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects squash" {
    run run_hook_with_prompt "Squash these commits together"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects consolidate" {
    run run_hook_with_prompt "Consolidate these functions into one"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects merge" {
    run run_hook_with_prompt "Merge the two config files"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects cleanup" {
    run run_hook_with_prompt "Cleanup the unused code"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects reorganize" {
    run run_hook_with_prompt "Reorganize the project structure"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: detects remove duplicate" {
    run run_hook_with_prompt "Remove duplicate entries"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}

# ============================================================================
# Allow Safe Operations
# ============================================================================

@test "verify-destructive-operations: allows read operations" {
    run run_hook_with_prompt "Show me the file contents"
    [ "$status" -eq 0 ]
    [[ "$output" != *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: allows add operations" {
    run run_hook_with_prompt "Add a new function to handle errors"
    [ "$status" -eq 0 ]
    [[ "$output" != *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: allows create operations" {
    run run_hook_with_prompt "Create a new test file"
    [ "$status" -eq 0 ]
    [[ "$output" != *"DESTRUCTIVE"* ]]
}

@test "verify-destructive-operations: allows search operations" {
    run run_hook_with_prompt "Find all TODO comments"
    [ "$status" -eq 0 ]
    [[ "$output" != *"DESTRUCTIVE"* ]]
}

# ============================================================================
# Event Handling
# ============================================================================

@test "verify-destructive-operations: ignores non-UserPromptSubmit events" {
    local json='{"hook_event_name": "PreToolUse", "tool_name": "Bash", "message": "git rebase"}'
    run bash -c 'echo "$1" | '"$HOOKS_DIR"'/verify-destructive-operations.sh' -- "$json"
    [ "$status" -eq 0 ]
    [[ "$output" != *"DESTRUCTIVE"* ]]
}

# ============================================================================
# Verification Message Content
# ============================================================================

@test "verify-destructive-operations: includes verification steps" {
    run run_hook_with_prompt "Delete the old files"
    [ "$status" -eq 0 ]
    [[ "$output" == *"Double-check"* ]] || [[ "$output" == *"Verify"* ]]
    [[ "$output" == *"preserved"* ]] || [[ "$output" == *"retained"* ]]
}

# ============================================================================
# Case Insensitivity
# ============================================================================

@test "verify-destructive-operations: case insensitive detection" {
    run run_hook_with_prompt "REFACTOR the module"
    [ "$status" -eq 0 ]
    [[ "$output" == *"DESTRUCTIVE"* ]]
}
