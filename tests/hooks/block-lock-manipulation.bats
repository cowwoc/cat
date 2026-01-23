#!/usr/bin/env bats
# Tests for bash_handlers/block_lock_manipulation.py - Prevents direct lock file manipulation

load '../test_helper'

setup() {
    setup_test_dir
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
# Block rm Commands Targeting Lock Files
# ============================================================================

@test "block-lock-manipulation: blocks rm of lock file" {
    run run_hook_with_command "rm .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-lock-manipulation: blocks rm -f of lock file" {
    run run_hook_with_command "rm -f .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-lock-manipulation: blocks rm -rf of lock file" {
    run run_hook_with_command "rm -rf .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-lock-manipulation: blocks rm with path containing .claude/cat/locks" {
    run run_hook_with_command "rm /workspace/.claude/cat/locks/some-task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

# ============================================================================
# Block Removal of Entire Locks Directory
# ============================================================================

@test "block-lock-manipulation: blocks rm of locks directory" {
    run run_hook_with_command "rm -rf .claude/cat/locks"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "block-lock-manipulation: blocks rm of locks/ with trailing slash" {
    run run_hook_with_command "rm -rf .claude/cat/locks/"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

# ============================================================================
# Allow Non-Lock Commands
# ============================================================================

@test "block-lock-manipulation: allows rm of unrelated files" {
    run run_hook_with_command "rm test-file.txt"
    [ "$status" -eq 0 ]
    # Should output empty JSON (allow) or minimal JSON without block/deny
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "block-lock-manipulation: allows rm in other directories" {
    run run_hook_with_command "rm -rf /tmp/test-dir"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "block-lock-manipulation: allows non-rm commands" {
    run run_hook_with_command "ls -la .claude/cat/locks"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "block-lock-manipulation: allows cat of lock files" {
    run run_hook_with_command "cat .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

# ============================================================================
# Non-Bash Tool Handling
# ============================================================================

@test "block-lock-manipulation: ignores non-Bash tools" {
    local json='{"hook_event_name": "PreToolUse", "tool_name": "Read", "tool_input": {"file_path": ".claude/cat/locks/task.lock"}}'
    run bash -c 'echo "$1" | python3 '"$HOOKS_DIR"'/get-bash-pretool-output.py' -- "$json"
    [ "$status" -eq 0 ]
    # Hook outputs {} for non-Bash tools
    [[ "$output" == "{}" ]]
}

# ============================================================================
# Guidance Message Tests
# ============================================================================

@test "block-lock-manipulation: includes guidance in block message" {
    run run_hook_with_command "rm .claude/cat/locks/task.lock"
    [ "$status" -eq 0 ]
    # Should mention proper alternatives
    [[ "$output" == *"/cat:status"* ]] || [[ "$output" == *"/cat:cleanup"* ]] || [[ "$output" == *"task"* ]]
}

@test "block-lock-manipulation: warns about concurrent execution risk" {
    run run_hook_with_command "rm -f .claude/cat/locks/my-task.lock"
    [ "$status" -eq 0 ]
    # Should explain why this is blocked
    [[ "$output" == *"concurrent"* ]] || [[ "$output" == *"Concurrent"* ]] || [[ "$output" == *"BLOCKED"* ]]
}
