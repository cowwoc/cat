#!/bin/bash
# Library for JSON parsing utilities
# Source this file in hooks that need to parse JSON input
#
# Usage: source "${CLAUDE_PLUGIN_ROOT}/hooks/lib/json-parser.sh"

# Extract a single JSON value
# Usage: extract_json_value "$json" "key"
# Returns: The value of the key, or empty string if not found
extract_json_value() {
    local json="$1"
    local key="$2"
    # Use grep and sed for basic JSON parsing (allow grep to fail without triggering set -e)
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\"/\1/" || true
}

# Extract a numeric JSON value
# Usage: extract_json_number "$json" "key"
# Returns: The numeric value of the key, or empty string if not found
extract_json_number() {
    local json="$1"
    local key="$2"
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*[0-9]*" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*//" || true
}

# Extract a boolean JSON value
# Usage: extract_json_bool "$json" "key"
# Returns: "true", "false", or empty string if not found
extract_json_bool() {
    local json="$1"
    local key="$2"
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\(true\|false\)" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*//" || true
}

# Cache for parsed JSON to avoid redundant extractions
declare -gA _JSON_CACHE 2>/dev/null || true

# Extract and cache JSON value for multiple accesses
# Usage: extract_json_cached "$json" "key"
# Benefits: Use this when extracting multiple fields from same JSON
extract_json_cached() {
    local json="$1"
    local key="$2"
    local cache_key="${json:0:100}:$key"  # Use first 100 chars as cache key

    # Check if associative arrays are supported and cache exists
    if declare -p _JSON_CACHE &>/dev/null && [[ -n "${_JSON_CACHE[$cache_key]:-}" ]]; then
        echo "${_JSON_CACHE[$cache_key]}"
        return 0
    fi

    local value
    value=$(extract_json_value "$json" "$key")

    # Cache if supported
    if declare -p _JSON_CACHE &>/dev/null; then
        _JSON_CACHE[$cache_key]="$value"
    fi

    echo "$value"
}

# Parse all common hook JSON fields at once
# Usage: parse_hook_json "$JSON_INPUT"
# Sets global variables: HOOK_EVENT, SESSION_ID, USER_PROMPT, TOOL_NAME, TOOL_INPUT_JSON
parse_hook_json() {
    local json="$1"

    # Extract all common fields in one pass
    HOOK_EVENT=$(extract_json_value "$json" "hook_event_name")
    SESSION_ID=$(extract_json_value "$json" "session_id")

    # Try multiple possible fields for user prompt
    USER_PROMPT=$(extract_json_value "$json" "message")
    [[ -z "$USER_PROMPT" ]] && USER_PROMPT=$(extract_json_value "$json" "user_message")
    [[ -z "$USER_PROMPT" ]] && USER_PROMPT=$(extract_json_value "$json" "prompt")

    # Tool-related fields for PreToolUse/PostToolUse hooks
    TOOL_NAME=$(extract_json_value "$json" "tool_name")
    TOOL_INPUT_JSON=$(echo "$json" | grep -o '"tool_input"[[:space:]]*:[[:space:]]*{[^}]*}' | sed 's/"tool_input"[[:space:]]*:[[:space:]]*//' || true)

    # Export for use in calling script
    export HOOK_EVENT SESSION_ID USER_PROMPT TOOL_NAME TOOL_INPUT_JSON
}

# Validate and sanitize session ID
# Usage: validate_session_id "$session_id_raw"
# Returns: Sanitized session ID (only alphanumeric, dash, underscore)
validate_session_id() {
    local session_id_raw="$1"

    [[ -z "$session_id_raw" ]] && return 1

    # Sanitize: only allow alphanumeric, dash, underscore
    echo "$session_id_raw" | tr -cd 'a-zA-Z0-9_-'
}

# Create hookSpecificOutput JSON
# Usage: create_hook_output "event_name" "message"
# Returns: Properly formatted JSON for hook output
create_hook_output() {
    local event_name="$1"
    local message="$2"

    jq -n --arg event "$event_name" --arg msg "$message" '{
        "hookSpecificOutput": {
            "hookEventName": $event,
            "additionalContext": $msg
        }
    }'
}

# Check if jq is available, fallback to basic parsing
check_jq() {
    if command -v jq &>/dev/null; then
        return 0
    else
        return 1
    fi
}

# Safe JSON extraction using jq if available, fallback otherwise
# Usage: safe_json_get "$json" ".key.subkey"
safe_json_get() {
    local json="$1"
    local path="$2"

    if check_jq; then
        echo "$json" | jq -r "$path // empty" 2>/dev/null || echo ""
    else
        # Basic fallback - only supports simple keys
        local key="${path#.}"
        extract_json_value "$json" "$key"
    fi
}

# ============================================================================
# Hook Output Functions
# ============================================================================

# Output hook block message and deny permission (PreToolUse hooks ONLY)
# This ACTUALLY BLOCKS the action via Claude Code's permission system
#
# Args: user_message
#   user_message: Detailed message shown to user
#
# CRITICAL: Caller MUST exit 0 after calling this function
# Usage: output_hook_block "Blocked: policy violation"; exit 0
output_hook_block() {
    local user_message="$1"

    # Output detailed message to stderr for user visibility
    echo "$user_message" >&2

    # Output JSON permission denial to stdout
    jq -n --arg reason "${user_message:0:200}" '{
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": $reason
        }
    }'
}

# Output hook warning message (does NOT block)
# Args: message_content
output_hook_warning() {
    local message="$1"

    # Output message to stderr for user visibility
    echo "$message" >&2

    # Output JSON context
    jq -n --arg msg "$message" '{
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": $msg
        }
    }'
}
