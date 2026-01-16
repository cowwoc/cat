#!/usr/bin/env bats
# Tests for scripts/monitor-subagents.sh - Subagent status monitoring

load '../test_helper'

setup() {
    setup_git_repo
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# Basic Output Tests (no subagents)
# ============================================================================

@test "monitor-subagents: returns valid JSON with no subagents" {
    run "$SCRIPTS_DIR/monitor-subagents.sh"
    [ "$status" -eq 0 ]

    # Verify valid JSON output
    echo "$output" | jq -e '.' > /dev/null
}

@test "monitor-subagents: returns empty subagents array when none exist" {
    run "$SCRIPTS_DIR/monitor-subagents.sh"
    [ "$status" -eq 0 ]

    assert_json_field "$output" ".subagents | length" "0"
}

@test "monitor-subagents: returns zero counts when no subagents" {
    run "$SCRIPTS_DIR/monitor-subagents.sh"
    [ "$status" -eq 0 ]

    assert_json_field "$output" ".summary.total" "0"
    assert_json_field "$output" ".summary.running" "0"
    assert_json_field "$output" ".summary.complete" "0"
}

# ============================================================================
# JSON Structure Tests
# ============================================================================

@test "monitor-subagents: output has subagents array" {
    run "$SCRIPTS_DIR/monitor-subagents.sh"
    [ "$status" -eq 0 ]

    [[ "$output" == *'"subagents":'* ]]
}

@test "monitor-subagents: output has summary object" {
    run "$SCRIPTS_DIR/monitor-subagents.sh"
    [ "$status" -eq 0 ]

    [[ "$output" == *'"summary":'* ]]
    [[ "$output" == *'"total":'* ]]
    [[ "$output" == *'"running":'* ]]
    [[ "$output" == *'"complete":'* ]]
}

@test "monitor-subagents: output has warning count in summary" {
    run "$SCRIPTS_DIR/monitor-subagents.sh"
    [ "$status" -eq 0 ]

    [[ "$output" == *'"warning":'* ]]
}

# ============================================================================
# Argument Parsing Tests
# ============================================================================

@test "monitor-subagents: accepts --worktree-dir option" {
    run "$SCRIPTS_DIR/monitor-subagents.sh" --worktree-dir "$TEST_TEMP_DIR/.worktrees"
    [ "$status" -eq 0 ]
}

@test "monitor-subagents: rejects unknown options" {
    run "$SCRIPTS_DIR/monitor-subagents.sh" --invalid-option value
    [ "$status" -eq 1 ]
    [[ "$output" == *"error"* ]]
    [[ "$output" == *"Unknown option"* ]]
}

# ============================================================================
# Subagent Detection Tests (with mocked worktrees)
# ============================================================================

@test "monitor-subagents: ignores non-subagent worktrees" {
    # Create a regular worktree (not subagent pattern)
    mkdir -p "$TEST_TEMP_DIR/.worktrees"
    git worktree add "$TEST_TEMP_DIR/.worktrees/feature-branch" -b feature-branch 2>/dev/null || true

    run "$SCRIPTS_DIR/monitor-subagents.sh" --worktree-dir "$TEST_TEMP_DIR/.worktrees"
    [ "$status" -eq 0 ]

    # Should not count non-subagent worktree
    assert_json_field "$output" ".summary.total" "0"

    # Cleanup
    git worktree remove "$TEST_TEMP_DIR/.worktrees/feature-branch" --force 2>/dev/null || true
}

@test "monitor-subagents: detects subagent worktree pattern" {
    # Create a subagent-style worktree (contains -sub-)
    mkdir -p "$TEST_TEMP_DIR/.worktrees"
    git worktree add "$TEST_TEMP_DIR/.worktrees/task-sub-abc12345" -b task-sub-abc12345 2>/dev/null || true

    run "$SCRIPTS_DIR/monitor-subagents.sh" --worktree-dir "$TEST_TEMP_DIR/.worktrees"
    [ "$status" -eq 0 ]

    # Should count subagent worktree
    assert_json_field "$output" ".summary.total" "1"

    # Cleanup
    git worktree remove "$TEST_TEMP_DIR/.worktrees/task-sub-abc12345" --force 2>/dev/null || true
}

@test "monitor-subagents: marks worktree as running without completion file" {
    # Create subagent worktree without completion marker
    mkdir -p "$TEST_TEMP_DIR/.worktrees"
    git worktree add "$TEST_TEMP_DIR/.worktrees/mytask-sub-def67890" -b mytask-sub-def67890 2>/dev/null || true

    run "$SCRIPTS_DIR/monitor-subagents.sh" --worktree-dir "$TEST_TEMP_DIR/.worktrees"
    [ "$status" -eq 0 ]

    assert_json_field "$output" ".summary.running" "1"
    assert_json_field "$output" ".summary.complete" "0"

    # Cleanup
    git worktree remove "$TEST_TEMP_DIR/.worktrees/mytask-sub-def67890" --force 2>/dev/null || true
}

@test "monitor-subagents: marks worktree as complete with completion file" {
    # Create subagent worktree with completion marker
    mkdir -p "$TEST_TEMP_DIR/.worktrees"
    git worktree add "$TEST_TEMP_DIR/.worktrees/done-sub-111222" -b done-sub-111222 2>/dev/null || true

    # Add completion marker
    echo '{"tokensUsed": 5000, "compactionEvents": 0}' > "$TEST_TEMP_DIR/.worktrees/done-sub-111222/.completion.json"

    run "$SCRIPTS_DIR/monitor-subagents.sh" --worktree-dir "$TEST_TEMP_DIR/.worktrees"
    [ "$status" -eq 0 ]

    assert_json_field "$output" ".summary.complete" "1"
    assert_json_field "$output" ".summary.running" "0"

    # Cleanup
    git worktree remove "$TEST_TEMP_DIR/.worktrees/done-sub-111222" --force 2>/dev/null || true
}

@test "monitor-subagents: extracts token count from completion file" {
    mkdir -p "$TEST_TEMP_DIR/.worktrees"
    git worktree add "$TEST_TEMP_DIR/.worktrees/tokens-sub-333444" -b tokens-sub-333444 2>/dev/null || true

    # Add completion with specific token count
    echo '{"tokensUsed": 12345, "compactionEvents": 2}' > "$TEST_TEMP_DIR/.worktrees/tokens-sub-333444/.completion.json"

    run "$SCRIPTS_DIR/monitor-subagents.sh" --worktree-dir "$TEST_TEMP_DIR/.worktrees"
    [ "$status" -eq 0 ]

    # Check tokens in subagent data
    TOKENS=$(echo "$output" | jq -r '.subagents[0].tokens')
    [ "$TOKENS" = "12345" ]

    # Cleanup
    git worktree remove "$TEST_TEMP_DIR/.worktrees/tokens-sub-333444" --force 2>/dev/null || true
}

@test "monitor-subagents: extracts compaction count from completion file" {
    mkdir -p "$TEST_TEMP_DIR/.worktrees"
    git worktree add "$TEST_TEMP_DIR/.worktrees/compact-sub-555666" -b compact-sub-555666 2>/dev/null || true

    echo '{"tokensUsed": 50000, "compactionEvents": 3}' > "$TEST_TEMP_DIR/.worktrees/compact-sub-555666/.completion.json"

    run "$SCRIPTS_DIR/monitor-subagents.sh" --worktree-dir "$TEST_TEMP_DIR/.worktrees"
    [ "$status" -eq 0 ]

    COMPACTIONS=$(echo "$output" | jq -r '.subagents[0].compactions')
    [ "$COMPACTIONS" = "3" ]

    # Cleanup
    git worktree remove "$TEST_TEMP_DIR/.worktrees/compact-sub-555666" --force 2>/dev/null || true
}
