#!/usr/bin/env bats
# Tests for hooks/lib/session-helper.sh - Session ID extraction and validation

load '../test_helper'

setup() {
    setup_test_dir
    source "$HOOKS_LIB_DIR/session-helper.sh"
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# get_session_id Tests
# ============================================================================

@test "get_session_id: extracts session_id from valid JSON" {
    local json='{"session_id": "abc-123-def", "other": "value"}'
    local result=$(get_session_id "$json")
    [ "$result" = "abc-123-def" ]
}

@test "get_session_id: returns empty for missing session_id" {
    local json='{"other": "value"}'
    local result=$(get_session_id "$json")
    [ -z "$result" ]
}

@test "get_session_id: returns empty for null session_id" {
    local json='{"session_id": null}'
    local result=$(get_session_id "$json")
    [ -z "$result" ]
}

@test "get_session_id: returns empty for empty string session_id" {
    local json='{"session_id": ""}'
    local result=$(get_session_id "$json")
    [ -z "$result" ]
}

@test "get_session_id: handles invalid JSON gracefully" {
    local json='not valid json'
    local result=$(get_session_id "$json")
    [ -z "$result" ]
}

@test "get_session_id: handles empty input" {
    local result=$(get_session_id "")
    [ -z "$result" ]
}

@test "get_session_id: extracts UUID-format session_id" {
    local json='{"session_id": "550e8400-e29b-41d4-a716-446655440000"}'
    local result=$(get_session_id "$json")
    [ "$result" = "550e8400-e29b-41d4-a716-446655440000" ]
}

# ============================================================================
# require_session_id Tests
# ============================================================================

@test "require_session_id: succeeds with valid session_id" {
    run require_session_id "valid-session-123"
    [ "$status" -eq 0 ]
}

@test "require_session_id: exits 1 for empty session_id" {
    run require_session_id ""
    [ "$status" -eq 1 ]
    [[ "$output" == *"ERROR"* ]]
    [[ "$output" == *"session_id"* ]]
}

@test "require_session_id: error message goes to stderr" {
    # Run and capture both stdout and stderr, then check output contains ERROR
    run bash -c 'source '"$HOOKS_LIB_DIR"'/session-helper.sh; require_session_id "" 2>&1'
    [ "$status" -eq 1 ]
    [[ "$output" == *"ERROR"* ]]
}

# ============================================================================
# get_required_session_id Tests
# ============================================================================

@test "get_required_session_id: returns session_id from valid JSON" {
    local json='{"session_id": "test-session-456"}'
    local result=$(get_required_session_id "$json")
    [ "$result" = "test-session-456" ]
}

@test "get_required_session_id: exits 1 for missing session_id" {
    local json='{"other": "value"}'
    run get_required_session_id "$json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"ERROR"* ]]
}

@test "get_required_session_id: exits 1 for empty JSON" {
    run get_required_session_id "{}"
    [ "$status" -eq 1 ]
}

@test "get_required_session_id: exits 1 for invalid JSON" {
    run get_required_session_id "not json"
    [ "$status" -eq 1 ]
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "get_session_id: handles session_id with special characters" {
    local json='{"session_id": "session_with-special.chars123"}'
    local result=$(get_session_id "$json")
    [ "$result" = "session_with-special.chars123" ]
}

@test "get_session_id: handles nested JSON without crashing" {
    local json='{"outer": {"session_id": "nested"}, "session_id": "top-level"}'
    local result=$(get_session_id "$json")
    # Should get top-level session_id, not nested one
    [ "$result" = "top-level" ]
}

@test "get_session_id: handles very long session_id" {
    local long_id="session-$(printf 'x%.0s' {1..200})"
    local json="{\"session_id\": \"$long_id\"}"
    local result=$(get_session_id "$json")
    [ "$result" = "$long_id" ]
}
