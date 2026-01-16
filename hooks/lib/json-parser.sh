#!/bin/bash
# Library for JSON parsing utilities
# Source this file in hooks that need to parse JSON input
#
# REQUIREMENT: jq must be installed for reliable JSON parsing.
# Fallback regex parsing only works for simple, flat JSON.
#
# Usage: source "${CLAUDE_PLUGIN_ROOT}/hooks/lib/json-parser.sh"

# Check jq availability once at load time
_JQ_AVAILABLE=false
if command -v jq &>/dev/null; then
    _JQ_AVAILABLE=true
fi

# Extract a single JSON value
# Usage: extract_json_value "$json" "key"
# Returns: The value of the key, or empty string if not found
extract_json_value() {
    local json="$1"
    local key="$2"

    # Prefer jq for reliable parsing
    if $_JQ_AVAILABLE; then
        echo "$json" | jq -r --arg k "$key" '.[$k] // empty' 2>/dev/null || echo ""
        return
    fi

    # Fallback: regex parsing (WARNING: only works for simple flat JSON with string values)
    # Does NOT handle: nested objects, arrays, escaped quotes, multiline values
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\"/\1/" || true
}

# Extract a numeric JSON value
# Usage: extract_json_number "$json" "key"
# Returns: The numeric value of the key, or empty string if not found
extract_json_number() {
    local json="$1"
    local key="$2"

    if $_JQ_AVAILABLE; then
        echo "$json" | jq -r --arg k "$key" '.[$k] // empty | select(type == "number")' 2>/dev/null || echo ""
        return
    fi

    # Fallback
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*[0-9.-]*" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*//" || true
}

# Extract a boolean JSON value
# Usage: extract_json_bool "$json" "key"
# Returns: "true", "false", or empty string if not found
extract_json_bool() {
    local json="$1"
    local key="$2"

    if $_JQ_AVAILABLE; then
        echo "$json" | jq -r --arg k "$key" '.[$k] // empty | select(type == "boolean")' 2>/dev/null || echo ""
        return
    fi

    # Fallback
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

# Parse all common hook JSON fields at once (optimized - single jq call when available)
# Usage: parse_hook_json "$JSON_INPUT"
# Sets global variables: HOOK_EVENT, SESSION_ID, USER_PROMPT, TOOL_NAME, TOOL_INPUT_JSON
parse_hook_json() {
    local json="$1"

    if $_JQ_AVAILABLE; then
        # Single jq call extracts all fields efficiently
        local parsed
        parsed=$(echo "$json" | jq -r '[
            .hook_event_name // "",
            .session_id // "",
            (.message // .user_message // .prompt // ""),
            .tool_name // "",
            (.tool_input | if . then tostring else "" end)
        ] | @tsv' 2>/dev/null) || parsed=""

        if [[ -n "$parsed" ]]; then
            IFS=$'\t' read -r HOOK_EVENT SESSION_ID USER_PROMPT TOOL_NAME TOOL_INPUT_JSON <<< "$parsed"
        fi
    else
        # Fallback: multiple extractions (less efficient)
        HOOK_EVENT=$(extract_json_value "$json" "hook_event_name")
        SESSION_ID=$(extract_json_value "$json" "session_id")

        # Try multiple possible fields for user prompt
        USER_PROMPT=$(extract_json_value "$json" "message")
        [[ -z "$USER_PROMPT" ]] && USER_PROMPT=$(extract_json_value "$json" "user_message")
        [[ -z "$USER_PROMPT" ]] && USER_PROMPT=$(extract_json_value "$json" "prompt")

        # Tool-related fields
        TOOL_NAME=$(extract_json_value "$json" "tool_name")
        TOOL_INPUT_JSON=$(echo "$json" | grep -o '"tool_input"[[:space:]]*:[[:space:]]*{[^}]*}' | sed 's/"tool_input"[[:space:]]*:[[:space:]]*//' || true)
    fi

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
# Standard Hook Initialization
# ============================================================================

# Initialize a hook by reading JSON from stdin with timeout
# Usage: init_hook [timeout_seconds]
# Sets: HOOK_JSON (raw JSON input), plus calls parse_hook_json
# Returns: 0 on success, 1 if no input or invalid JSON
init_hook() {
    local timeout_secs="${1:-5}"

    # Read JSON from stdin with timeout (prevent hanging)
    if [ -t 0 ]; then
        # No stdin available
        HOOK_JSON="{}"
        return 1
    fi

    HOOK_JSON=$(timeout "${timeout_secs}s" cat 2>/dev/null) || HOOK_JSON="{}"

    # Validate it's actually JSON
    if $_JQ_AVAILABLE && ! echo "$HOOK_JSON" | jq -e '.' > /dev/null 2>&1; then
        echo "Warning: Invalid JSON input to hook" >&2
        HOOK_JSON="{}"
        return 1
    fi

    # Parse common fields
    parse_hook_json "$HOOK_JSON"

    export HOOK_JSON
    return 0
}

# Initialize a Bash tool hook (PreToolUse/PostToolUse for Bash)
# Usage: init_bash_hook
# Sets: HOOK_JSON, TOOL_NAME, BASH_COMMAND
# Returns: 0 if Bash tool, 1 otherwise (caller should exit 0)
init_bash_hook() {
    init_hook || return 1

    # Check if this is a Bash tool call
    if [[ "$TOOL_NAME" != "Bash" && "$TOOL_NAME" != "bash" ]]; then
        echo '{}'
        return 1
    fi

    # Extract command from tool_input
    if $_JQ_AVAILABLE; then
        BASH_COMMAND=$(echo "$HOOK_JSON" | jq -r '.tool_input.command // empty' 2>/dev/null) || BASH_COMMAND=""
    else
        BASH_COMMAND=$(echo "$TOOL_INPUT_JSON" | grep -o '"command"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"command"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' || true)
    fi

    export BASH_COMMAND
    return 0
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
