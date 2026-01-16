#!/usr/bin/env bats
# Tests for hooks/block-lock-manipulation.sh - Prevents direct lock file manipulation

load '../test_helper'

setup() {
    setup_test_dir
    source "$HOOKS_LIB_DIR/json-parser.sh"
}

teardown() {
    teardown_test_dir
}

# Helper to create hook input JSON for Bash tool
create_bash_hook_json() {
    local command="$1"
    echo "{\"hook_event_name\": \"PreToolUse\", \"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"$command\"}}"
}

# Helper to run hook with JSON input
run_hook_with_command() {
    local command="$1"
    local json=$(create_bash_hook_json "$command")
    echo "$json" | "$HOOKS_DIR/block-lock-manipulation.sh"
}

# ============================================================================
# Block rm Commands Targeting Lock Files
# ============================================================================

@test "block-lock-manipulation: blocks rm of lock file" {
    run run_hook_with_command "rm .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"BLOCKED"* ]] || [[ "$output" == *"deny"* ]]
}

@test "block-lock-manipulation: blocks rm -f of lock file" {
    run run_hook_with_command "rm -f .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"BLOCKED"* ]] || [[ "$output" == *"deny"* ]]
}

@test "block-lock-manipulation: blocks rm -rf of lock file" {
    run run_hook_with_command "rm -rf .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"BLOCKED"* ]] || [[ "$output" == *"deny"* ]]
}

@test "block-lock-manipulation: blocks rm with path containing .claude/cat/locks" {
    run run_hook_with_command "rm /workspace/.claude/cat/locks/some-task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"BLOCKED"* ]] || [[ "$output" == *"deny"* ]]
}

# ============================================================================
# Block Removal of Entire Locks Directory
# ============================================================================

@test "block-lock-manipulation: blocks rm of locks directory" {
    run run_hook_with_command "rm -rf .claude/cat/locks"
    [ "$status" -eq 0 ]
    [[ "$output" == *"BLOCKED"* ]] || [[ "$output" == *"deny"* ]]
}

@test "block-lock-manipulation: blocks rm of locks/ with trailing slash" {
    run run_hook_with_command "rm -rf .claude/cat/locks/"
    [ "$status" -eq 0 ]
    [[ "$output" == *"BLOCKED"* ]] || [[ "$output" == *"deny"* ]]
}

# ============================================================================
# Allow Non-Lock Commands
# ============================================================================

@test "block-lock-manipulation: allows rm of unrelated files" {
    run run_hook_with_command "rm test-file.txt"
    [ "$status" -eq 0 ]
    # Should output empty JSON (allow)
    [[ "$output" == "{}" ]] || [[ "$output" != *"BLOCKED"* ]]
}

@test "block-lock-manipulation: allows rm in other directories" {
    run run_hook_with_command "rm -rf /tmp/test-dir"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"BLOCKED"* ]]
}

@test "block-lock-manipulation: allows non-rm commands" {
    run run_hook_with_command "ls -la .claude/cat/locks"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"BLOCKED"* ]]
}

@test "block-lock-manipulation: allows cat of lock files" {
    run run_hook_with_command "cat .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"BLOCKED"* ]]
}

# ============================================================================
# Non-Bash Tool Handling
# ============================================================================

@test "block-lock-manipulation: ignores non-Bash tools" {
    local json='{"hook_event_name": "PreToolUse", "tool_name": "Read", "tool_input": {"file_path": ".claude/cat/locks/task.lock"}}'
    run bash -c 'echo "$1" | '"$HOOKS_DIR"'/block-lock-manipulation.sh' -- "$json"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"BLOCKED"* ]]
}

# ============================================================================
# Guidance Message Tests
# ============================================================================

@test "block-lock-manipulation: includes guidance in block message" {
    run run_hook_with_command "rm .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    # Should mention proper alternatives
    [[ "$output" == *"/cat:status"* ]] || [[ "$output" == *"/cat:cleanup"* ]] || [[ "$output" == *"task-lock"* ]]
}

@test "block-lock-manipulation: warns about concurrent execution risk" {
    run run_hook_with_command "rm -f .claude/cat/locks/my-task.lock"
    [ "$status" -eq 0 ]
    # Should explain why this is blocked
    [[ "$output" == *"concurrent"* ]] || [[ "$output" == *"Concurrent"* ]] || [[ "$output" == *"BLOCKED"* ]]
}
