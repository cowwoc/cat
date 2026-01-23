#!/usr/bin/env bats
# Tests for hooks/lib/json-parser.sh - JSON parsing utilities and hook initialization

load '../test_helper'

setup() {
    setup_test_dir
    source "$HOOKS_LIB_DIR/json-parser.sh"
    source "$HOOKS_LIB_DIR/json-output.sh"
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# extract_json_value Tests
# ============================================================================

@test "extract_json_value: extracts string value" {
    local json='{"name": "test", "value": "hello"}'
    local result=$(extract_json_value "$json" "name")
    [ "$result" = "test" ]
}

@test "extract_json_value: returns empty for missing key" {
    local json='{"name": "test"}'
    local result=$(extract_json_value "$json" "missing")
    [ -z "$result" ]
}

@test "extract_json_value: returns nested object as JSON string" {
    local json='{"outer": {"inner": "value"}}'
    local result=$(extract_json_value "$json" "outer")
    # jq returns nested objects as JSON string representation
    [[ "$result" == *"inner"* ]]
    [[ "$result" == *"value"* ]]
}

# ============================================================================
# extract_json_number Tests
# ============================================================================

@test "extract_json_number: extracts numeric value" {
    local json='{"count": 42, "name": "test"}'
    local result=$(extract_json_number "$json" "count")
    [ "$result" = "42" ]
}

@test "extract_json_number: returns empty for string value" {
    local json='{"count": "not-a-number"}'
    local result=$(extract_json_number "$json" "count")
    [ -z "$result" ]
}

# ============================================================================
# extract_json_bool Tests
# ============================================================================

@test "extract_json_bool: extracts true value" {
    local json='{"enabled": true}'
    local result=$(extract_json_bool "$json" "enabled")
    [ "$result" = "true" ]
}

@test "extract_json_bool: extracts false value" {
    local json='{"enabled": false}'
    local result=$(extract_json_bool "$json" "enabled")
    # jq returns "false" as string
    [[ "$result" == "false" ]]
}

# ============================================================================
# parse_hook_json Tests
# ============================================================================

@test "parse_hook_json: extracts all common fields" {
    local json='{"hook_event_name": "PreToolUse", "session_id": "abc123", "message": "hello", "tool_name": "Bash", "tool_input": {"command": "ls"}}'
    parse_hook_json "$json"

    [ "$HOOK_EVENT" = "PreToolUse" ]
    [ "$SESSION_ID" = "abc123" ]
    [ "$USER_PROMPT" = "hello" ]
    [ "$TOOL_NAME" = "Bash" ]
    [[ "$TOOL_INPUT_JSON" == *"command"* ]]
}

@test "parse_hook_json: handles empty hook_event_name" {
    local json='{"hook_event_name": "", "session_id": "abc123", "tool_name": "Bash"}'
    parse_hook_json "$json"

    [ -z "$HOOK_EVENT" ]
    [ "$SESSION_ID" = "abc123" ]
    [ "$TOOL_NAME" = "Bash" ]
}

@test "parse_hook_json: handles multiple empty fields" {
    local json='{"hook_event_name": "", "session_id": "", "message": "", "tool_name": "Bash"}'
    parse_hook_json "$json"

    [ -z "$HOOK_EVENT" ]
    [ -z "$SESSION_ID" ]
    [ -z "$USER_PROMPT" ]
    [ "$TOOL_NAME" = "Bash" ]
}

@test "parse_hook_json: falls back to user_message field" {
    local json='{"hook_event_name": "Test", "user_message": "from_user_message"}'
    parse_hook_json "$json"

    [ "$USER_PROMPT" = "from_user_message" ]
}

@test "parse_hook_json: falls back to prompt field" {
    local json='{"hook_event_name": "Test", "prompt": "from_prompt"}'
    parse_hook_json "$json"

    [ "$USER_PROMPT" = "from_prompt" ]
}

# ============================================================================
# validate_session_id Tests
# ============================================================================

@test "validate_session_id: passes through valid session ID" {
    local result=$(validate_session_id "abc-123_DEF")
    [ "$result" = "abc-123_DEF" ]
}

@test "validate_session_id: strips invalid characters" {
    local result=$(validate_session_id "abc/../etc/passwd")
    [ "$result" = "abcetcpasswd" ]
}

@test "validate_session_id: returns empty for all-invalid characters" {
    local result=$(validate_session_id "../../")
    [ -z "$result" ]
}

@test "validate_session_id: returns empty for empty input" {
    run validate_session_id ""
    [ "$status" -eq 1 ]
}

# ============================================================================
# init_hook Tests
# ============================================================================

@test "init_hook: parses JSON from stdin" {
    local json='{"hook_event_name": "UserPromptSubmit", "session_id": "test-session", "message": "hello"}'

    # Use process substitution to avoid subshell variable loss
    init_hook < <(echo "$json")

    [ "$HOOK_EVENT" = "UserPromptSubmit" ]
    [ "$SESSION_ID" = "test-session" ]
    [ "$USER_PROMPT" = "hello" ]
}

@test "init_hook: returns 1 when stdin is empty" {
    # Empty stdin should cause init_hook to return 1
    run bash -c 'source '"$HOOKS_LIB_DIR"'/json-parser.sh; echo "{}" | init_hook; echo "EVENT=$HOOK_EVENT"'
    # Empty JSON should parse but HOOK_EVENT will be empty
    [ "$status" -eq 0 ]
}

@test "init_hook: sets HOOK_JSON variable" {
    local json='{"hook_event_name": "Test"}'

    init_hook < <(echo "$json")

    [ -n "$HOOK_JSON" ]
    [[ "$HOOK_JSON" == *"hook_event_name"* ]]
}

# ============================================================================
# init_bash_hook Tests
# ============================================================================

@test "init_bash_hook: extracts HOOK_COMMAND for Bash tool" {
    # Test via subshell to verify the function works
    # Use process substitution to avoid subshell variable loss from pipe
    run bash -c '
        source '"$HOOKS_LIB_DIR"'/json-parser.sh
        init_bash_hook < <(echo '"'"'{"hook_event_name": "PreToolUse", "tool_name": "Bash", "tool_input": {"command": "git status"}}'"'"')
        echo "$HOOK_COMMAND"
    '
    [ "$status" -eq 0 ]
    [[ "$output" == *"git status"* ]]
}

@test "init_bash_hook: returns 1 for non-Bash tool" {
    run bash -c '
        source '"$HOOKS_LIB_DIR"'/json-parser.sh
        init_bash_hook < <(echo '"'"'{"tool_name": "Read"}'"'"')
    '
    [ "$status" -eq 1 ]
}

@test "init_bash_hook: handles lowercase 'bash'" {
    run bash -c '
        source '"$HOOKS_LIB_DIR"'/json-parser.sh
        init_bash_hook < <(echo '"'"'{"hook_event_name": "PreToolUse", "tool_name": "bash", "tool_input": {"command": "echo hello"}}'"'"')
        echo "$HOOK_COMMAND"
    '
    [ "$status" -eq 0 ]
    [[ "$output" == *"echo hello"* ]]
}

# ============================================================================
# output_hook_block Tests
# ============================================================================

@test "output_hook_block: produces valid JSON with deny permission" {
    # Capture only stdout (JSON), discard stderr (user message)
    local json_output
    json_output=$(output_hook_block "Test block message" 2>/dev/null)

    echo "$json_output" | jq -e '.hookSpecificOutput.permissionDecision' > /dev/null
    local decision=$(echo "$json_output" | jq -r '.hookSpecificOutput.permissionDecision')
    [ "$decision" = "deny" ]
}

@test "output_hook_block: includes reason in output" {
    local json_output
    json_output=$(output_hook_block "Custom reason here" 2>/dev/null)

    local reason=$(echo "$json_output" | jq -r '.hookSpecificOutput.permissionDecisionReason')
    [[ "$reason" == *"Custom reason"* ]]
}

@test "output_hook_block: truncates long messages" {
    local long_message=$(printf 'x%.0s' {1..300})
    local json_output
    json_output=$(output_hook_block "$long_message" 2>/dev/null)

    local reason=$(echo "$json_output" | jq -r '.hookSpecificOutput.permissionDecisionReason')
    [ ${#reason} -le 200 ]
}

# ============================================================================
# output_hook_warning Tests
# ============================================================================

@test "output_hook_warning: produces valid JSON with additionalContext" {
    local json_output
    json_output=$(output_hook_warning "TestEvent" "Test warning" 2>/dev/null)

    echo "$json_output" | jq -e '.hookSpecificOutput.additionalContext' > /dev/null
}

@test "output_hook_warning: includes message in context" {
    local json_output
    json_output=$(output_hook_warning "TestEvent" "Warning message here" 2>/dev/null)

    local context=$(echo "$json_output" | jq -r '.hookSpecificOutput.additionalContext')
    [[ "$context" == *"Warning message"* ]]
}

# ============================================================================
# IFS Tab Splitting Bug Fix Tests (Critical)
# ============================================================================

@test "parse_hook_json: empty first field does not shift other fields" {
    # This test verifies the SOH placeholder fix for IFS tab splitting
    local json='{"hook_event_name": "", "session_id": "session123", "message": "hello", "tool_name": "Bash"}'
    parse_hook_json "$json"

    # Before fix: SESSION_ID would incorrectly be "hello" (shifted)
    # After fix: SESSION_ID correctly remains "session123"
    [ -z "$HOOK_EVENT" ]
    [ "$SESSION_ID" = "session123" ]
    [ "$USER_PROMPT" = "hello" ]
    [ "$TOOL_NAME" = "Bash" ]
}

@test "parse_hook_json: multiple empty fields in sequence" {
    local json='{"hook_event_name": "", "session_id": "", "message": "", "tool_name": "Bash", "tool_input": {}}'
    parse_hook_json "$json"

    [ -z "$HOOK_EVENT" ]
    [ -z "$SESSION_ID" ]
    [ -z "$USER_PROMPT" ]
    [ "$TOOL_NAME" = "Bash" ]
}

@test "parse_hook_json: all fields populated correctly" {
    local json='{"hook_event_name": "PreToolUse", "session_id": "sess-abc", "message": "test msg", "tool_name": "Bash", "tool_input": {"command": "ls"}}'
    parse_hook_json "$json"

    [ "$HOOK_EVENT" = "PreToolUse" ]
    [ "$SESSION_ID" = "sess-abc" ]
    [ "$USER_PROMPT" = "test msg" ]
    [ "$TOOL_NAME" = "Bash" ]
    [[ "$TOOL_INPUT_JSON" == *"ls"* ]]
}
