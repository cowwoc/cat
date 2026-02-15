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
