#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
# Tests for git-squash-quick.sh commit message validation

load '../test_helper'

setup() {
    setup_git_repo
    create_test_branch "test-branch" 3
}

teardown() {
    teardown_test_dir
}

@test "accepts valid feature message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: add user authentication" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts valid bugfix message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "bugfix: fix crash on startup" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts valid docs message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "docs: update README with installation guide" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts valid refactor message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "refactor: extract validation logic" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts valid test message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "test: add validation tests" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts valid performance message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "performance: optimize search algorithm" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts valid config message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "config: update project settings" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts valid planning message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "planning: add task breakdown" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "rejects generic 'squash commit' message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "squash commit" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects generic 'fix typo' message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "fix typo" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects generic 'add parser' message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "add parser" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects message with missing space after colon - feature" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature:nodescription" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects message with missing space after colon - bugfix" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "bugfix:fix" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects message with only colon and space (empty description)" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: " "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects uppercase type prefix" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "Feature: add auth" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects mixed case type prefix" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "Bugfix: fix crash" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "rejects empty string message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
}

@test "rejects invalid type prefix" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "chore: update dependencies" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "accepts long valid message" {
    local long_msg="feature: add comprehensive user authentication system with OAuth2 support and session management"
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "$long_msg" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "rejects message with only whitespace after type and colon" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature:   " "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid commit message format"* ]]
}

@test "error message shows received message" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "invalid message" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Received: invalid message"* ]]
}

@test "error message shows valid type prefixes" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "invalid: message" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"feature:"* ]]
    [[ "$output" == *"bugfix:"* ]]
    [[ "$output" == *"docs:"* ]]
}

@test "accepts message with special characters in description" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" 'feature: add $VAR and `backtick` support' "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

@test "accepts message with parentheses and brackets" {
    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "bugfix: fix array[0] access (null check)" "$TEST_TEMP_DIR"
    [ "$status" -eq 0 ]
    assert_json_field "$output" '.status' 'OK'
}

# ============================================================================
# Rebase conflict tests
# ============================================================================

@test "rebase conflict: exits with status 1" {
    # Create a conflict: modify the same file on main and on test-branch
    # setup() already created test-branch with 3 commits from main
    # Add a conflicting commit to main
    git -C "$TEST_TEMP_DIR" checkout --quiet main
    echo "conflict on main" > "$TEST_TEMP_DIR/file.txt"
    git -C "$TEST_TEMP_DIR" add file.txt
    git -C "$TEST_TEMP_DIR" commit --quiet -m "conflicting change on main"

    git -C "$TEST_TEMP_DIR" checkout --quiet test-branch

    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: squash with conflict" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
}

@test "rebase conflict: outputs JSON with REBASE_CONFLICT status" {
    git -C "$TEST_TEMP_DIR" checkout --quiet main
    echo "conflict on main" > "$TEST_TEMP_DIR/file.txt"
    git -C "$TEST_TEMP_DIR" add file.txt
    git -C "$TEST_TEMP_DIR" commit --quiet -m "conflicting change on main"

    git -C "$TEST_TEMP_DIR" checkout --quiet test-branch

    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: squash with conflict" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"REBASE_CONFLICT"* ]]
}

@test "rebase conflict: output includes backup_branch field" {
    git -C "$TEST_TEMP_DIR" checkout --quiet main
    echo "conflict on main" > "$TEST_TEMP_DIR/file.txt"
    git -C "$TEST_TEMP_DIR" add file.txt
    git -C "$TEST_TEMP_DIR" commit --quiet -m "conflicting change on main"

    git -C "$TEST_TEMP_DIR" checkout --quiet test-branch

    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: squash with conflict" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"backup_branch"* ]]
    [[ "$output" == *"backup-after-rebase-conflict-"* ]]
}

@test "rebase conflict: creates backup-after-rebase-conflict-* branch" {
    git -C "$TEST_TEMP_DIR" checkout --quiet main
    echo "conflict on main" > "$TEST_TEMP_DIR/file.txt"
    git -C "$TEST_TEMP_DIR" add file.txt
    git -C "$TEST_TEMP_DIR" commit --quiet -m "conflicting change on main"

    git -C "$TEST_TEMP_DIR" checkout --quiet test-branch

    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: squash with conflict" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]

    # Verify backup branch was created
    local backup_branches
    backup_branches=$(git -C "$TEST_TEMP_DIR" branch --list "backup-after-rebase-conflict-*" --format="%(refname:short)")
    [ -n "$backup_branches" ]
}

@test "rebase conflict: working directory is left clean (rebase aborted)" {
    git -C "$TEST_TEMP_DIR" checkout --quiet main
    echo "conflict on main" > "$TEST_TEMP_DIR/file.txt"
    git -C "$TEST_TEMP_DIR" add file.txt
    git -C "$TEST_TEMP_DIR" commit --quiet -m "conflicting change on main"

    git -C "$TEST_TEMP_DIR" checkout --quiet test-branch

    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: squash with conflict" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]

    # Working tree should be clean (rebase was aborted)
    local status_output
    status_output=$(git -C "$TEST_TEMP_DIR" status --porcelain 2>/dev/null)
    [ -z "$status_output" ]

    # No ongoing rebase
    local rebase_dir="$TEST_TEMP_DIR/.git/rebase-merge"
    [ ! -d "$rebase_dir" ]
}

@test "rebase conflict: output includes conflicting_files array" {
    git -C "$TEST_TEMP_DIR" checkout --quiet main
    echo "conflict on main" > "$TEST_TEMP_DIR/file.txt"
    git -C "$TEST_TEMP_DIR" add file.txt
    git -C "$TEST_TEMP_DIR" commit --quiet -m "conflicting change on main"

    git -C "$TEST_TEMP_DIR" checkout --quiet test-branch

    run "$SCRIPTS_DIR/git-squash-quick.sh" "main" "feature: squash with conflict" "$TEST_TEMP_DIR"
    [ "$status" -eq 1 ]
    [[ "$output" == *"conflicting_files"* ]]
}
