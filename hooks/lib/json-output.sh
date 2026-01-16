#!/bin/bash
# Standardized JSON output helper for hooks
# Source this file in hooks to simplify hook output generation
#
# Usage:
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "$SCRIPT_DIR/lib/json-output.sh"
#   output_hook_message "EventName" "Message content here"

# Output standardized hook message in JSON format
# Args: event_name message_content
output_hook_message() {
	local event="$1"
	local message="$2"

	jq -n \
		--arg event "$event" \
		--arg context "$message" \
		'{
			"hookSpecificOutput": {
				"hookEventName": $event,
				"additionalContext": $context
			}
		}'
}

# Output hook warning message (does NOT block)
# Args: message_content
# Note: Outputs to stderr for user visibility, JSON to stdout
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

# Output hook error message (does NOT block - use output_hook_block to block)
# Args: event_name message_content
output_hook_error() {
	local event="$1"
	local message="$2"
	output_hook_message "$event" "$message"
}

# Output hook block message and deny permission (PreToolUse hooks ONLY)
# This ACTUALLY BLOCKS the action via Claude Code's permission system
#
# Args: user_message
#   user_message: Message explaining why blocked (shown to user and Claude)
#
# CRITICAL: Caller MUST exit 0 after calling this function
# Usage: output_hook_block "Blocked: policy violation"; exit 0
#
# How it works:
# - JSON with permissionDecision goes to stdout (processed by Claude Code)
# - User-visible message goes to stderr (displayed to user)
# - Exit code 0 required for JSON processing (exit 2 ignores JSON)
# - Reason is truncated to 200 chars for JSON output
output_hook_block() {
	local user_message="$1"

	# Output detailed message to stderr for user visibility
	echo "$user_message" >&2

	# Truncate reason to 200 chars for JSON output
	local reason="${user_message:0:200}"

	# Output JSON permission denial to stdout with proper structure
	# CRITICAL: Must go to stdout with exit 0 for Claude Code to process
	jq -n \
		--arg reason "$reason" \
		'{
			"hookSpecificOutput": {
				"hookEventName": "PreToolUse",
				"permissionDecision": "deny",
				"permissionDecisionReason": $reason
			}
		}'
}

# Output JSON error message and exit
# Args: error_message
json_error() {
	local message="$1"
	jq -n \
		--arg msg "$message" \
		'{
			"status": "error",
			"message": $msg
		}'
	exit 1
}

# Output JSON success message
# Args: message additional_fields_json
#
# USAGE:
#   json_success "Operation completed"
#   json_success "Operation completed" '{"key": "value"}'
#
# NOTE: The second argument MUST be valid JSON (an object).
#       Common mistake: passing key=value pairs instead of JSON.
#       WRONG: json_success "msg" "key=value"
#       RIGHT: json_success "msg" '{"key": "value"}'
#
json_success() {
	local message="$1"
	local additional="${2-}"

	# Use empty object if no additional fields provided
	if [[ -z "$additional" ]]; then
		additional='{}'
	fi

	# Validate that additional is valid JSON before using it
	if ! echo "$additional" | jq empty 2>/dev/null; then
		echo "ERROR in json_success: Second argument is not valid JSON" >&2
		echo "  Received: $additional" >&2
		echo "  Expected: A JSON object like '{\"key\": \"value\"}'" >&2
		echo "  Common mistake: Passing key=value pairs instead of JSON" >&2
		# Output error JSON instead of crashing
		jq -n \
			--arg msg "json_success called with invalid JSON: $additional" \
			'{"status": "error", "message": $msg}'
		return 1
	fi

	echo "$additional" | jq \
		--arg msg "$message" \
		'. + {"status": "success", "message": $msg}'
}
