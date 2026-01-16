#!/usr/bin/env bats
# Tests for task-lock.sh - atomic task locking mechanism

load '../test_helper'

# Test UUIDs (valid format)
UUID_1="11111111-1111-1111-1111-111111111111"
UUID_2="22222222-2222-2222-2222-222222222222"
UUID_3="33333333-3333-3333-3333-333333333333"
UUID_A="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
UUID_B="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
UUID_ORIG="00000000-0000-0000-0000-000000000001"

setup() {
    setup_test_dir
    TASK_LOCK="$SCRIPTS_DIR/task-lock.sh"
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# Basic Operations
# ============================================================================

@test "acquire: creates lock file for new task" {
    run "$TASK_LOCK" acquire "test-task" "$UUID_1"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'acquired'

    # Verify lock file exists
    [ -f "$TEST_TEMP_DIR/.claude/cat/locks/test-task.lock" ]
}

@test "acquire: lock file contains correct session_id" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null

    local lock_file="$TEST_TEMP_DIR/.claude/cat/locks/test-task.lock"
    assert_file_contains "$lock_file" "session_id=$UUID_1"
}

@test "acquire: lock file contains worktree when provided" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" "/path/to/worktree" > /dev/null

    local lock_file="$TEST_TEMP_DIR/.claude/cat/locks/test-task.lock"
    assert_file_contains "$lock_file" "worktree=/path/to/worktree"
}

@test "acquire: idempotent for same session" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null

    run "$TASK_LOCK" acquire "test-task" "$UUID_1"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'acquired'
    [[ "$output" == *"already held"* ]]
}

@test "acquire: fails when locked by different session" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null

    run "$TASK_LOCK" acquire "test-task" "$UUID_2"

    [ "$status" -eq 1 ]
    assert_json_field "$output" '.status' 'locked'
    assert_json_field "$output" '.owner' "$UUID_1"
}

@test "acquire: returns guidance to find another task" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null

    run "$TASK_LOCK" acquire "test-task" "$UUID_2"

    assert_json_field "$output" '.action' 'FIND_ANOTHER_TASK'
    [[ "$output" == *"Do NOT investigate"* ]]
}

# ============================================================================
# Release Operations
# ============================================================================

@test "release: removes lock when owned by session" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null

    run "$TASK_LOCK" release "test-task" "$UUID_1"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'released'
    [ ! -f "$TEST_TEMP_DIR/.claude/cat/locks/test-task.lock" ]
}

@test "release: fails when owned by different session" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null

    run "$TASK_LOCK" release "test-task" "$UUID_2"

    [ "$status" -eq 1 ]
    assert_json_field "$output" '.status' 'error'
    # Lock should still exist
    [ -f "$TEST_TEMP_DIR/.claude/cat/locks/test-task.lock" ]
}

@test "release: succeeds when no lock exists" {
    run "$TASK_LOCK" release "nonexistent-task" "$UUID_1"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'released'
}

# ============================================================================
# Force Release Operations
# ============================================================================

@test "force-release: removes lock regardless of owner" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null

    run "$TASK_LOCK" force-release "test-task"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'released'
    [ ! -f "$TEST_TEMP_DIR/.claude/cat/locks/test-task.lock" ]
}

@test "force-release: reports previous owner" {
    "$TASK_LOCK" acquire "test-task" "$UUID_ORIG" > /dev/null

    run "$TASK_LOCK" force-release "test-task"

    [[ "$output" == *"$UUID_ORIG"* ]]
}

# ============================================================================
# Check Operations
# ============================================================================

@test "check: reports unlocked when no lock" {
    run "$TASK_LOCK" check "test-task"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.locked' 'false'
}

@test "check: reports locked with details" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" "/work/tree" > /dev/null

    run "$TASK_LOCK" check "test-task"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.locked' 'true'
    assert_json_field "$output" '.session_id' "$UUID_1"
    assert_json_field "$output" '.worktree' '/work/tree'
}

@test "check: includes lock age" {
    "$TASK_LOCK" acquire "test-task" "$UUID_1" > /dev/null
    sleep 1

    run "$TASK_LOCK" check "test-task"

    local age
    age=$(echo "$output" | jq -r '.age_seconds')
    [ "$age" -ge 1 ]
}

# ============================================================================
# List Operations
# ============================================================================

@test "list: returns empty array when no locks" {
    run "$TASK_LOCK" list

    [ "$status" -eq 0 ]
    [ "$output" = "[]" ]
}

@test "list: returns all locks" {
    "$TASK_LOCK" acquire "task-1" "$UUID_A" > /dev/null
    "$TASK_LOCK" acquire "task-2" "$UUID_B" > /dev/null

    run "$TASK_LOCK" list

    [ "$status" -eq 0 ]
    local count
    count=$(echo "$output" | jq 'length')
    [ "$count" -eq 2 ]
}

# ============================================================================
# Task ID Sanitization
# ============================================================================

@test "acquire: sanitizes task_id with slashes" {
    run "$TASK_LOCK" acquire "v1/v1/my-task" "$UUID_1"

    [ "$status" -eq 0 ]
    # Slashes should be replaced with dashes
    [ -f "$TEST_TEMP_DIR/.claude/cat/locks/v1-v1-my-task.lock" ]
}

# ============================================================================
# Error Handling
# ============================================================================

@test "acquire: returns error for missing arguments" {
    run "$TASK_LOCK" acquire "test-task"

    [ "$status" -eq 1 ]
    assert_json_field "$output" '.status' 'error'
}

@test "release: returns error for missing arguments" {
    run "$TASK_LOCK" release "test-task"

    [ "$status" -eq 1 ]
    assert_json_field "$output" '.status' 'error'
}

@test "unknown command: returns error" {
    run "$TASK_LOCK" invalid-command

    [ "$status" -eq 1 ]
    assert_json_field "$output" '.status' 'error'
}

# ============================================================================
# Race Condition Tests
# ============================================================================

@test "acquire: only one lock file exists after concurrent access" {
    # Start two acquire attempts in parallel
    "$TASK_LOCK" acquire "race-task" "$UUID_1" > /dev/null 2>&1 &
    local pid1=$!
    "$TASK_LOCK" acquire "race-task" "$UUID_2" > /dev/null 2>&1 &
    local pid2=$!

    wait $pid1 || true
    wait $pid2 || true

    # Key invariant: only one lock file should exist
    # (mv -n may not be atomic on all filesystems, but file system
    # guarantees only one file can exist at the path)
    local lock_count
    lock_count=$(ls "$TEST_TEMP_DIR/.claude/cat/locks/"*.lock 2>/dev/null | wc -l)
    [ "$lock_count" -eq 1 ]

    # The lock should belong to exactly one session
    local session_id
    session_id=$(grep "^session_id=" "$TEST_TEMP_DIR/.claude/cat/locks/race-task.lock" | cut -d= -f2)
    [[ "$session_id" == "$UUID_1" ]] || [[ "$session_id" == "$UUID_2" ]]
}
