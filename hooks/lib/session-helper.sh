#!/bin/bash
# Session ID extraction and validation helper
# Source this file in hooks to simplify session ID handling
#
# Usage:
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "$SCRIPT_DIR/lib/session-helper.sh"
#   SESSION_ID=$(get_session_id "$INPUT")
#   require_session_id "$SESSION_ID"

# Extract session ID from hook JSON input
# Args: json_input
# Returns: session_id or empty string
get_session_id() {
	local input="$1"
	echo "$input" | jq -r '.session_id // empty' 2>/dev/null || echo ""
}

# Require session ID to be non-empty, exit with error if missing
# Args: session_id
require_session_id() {
	local session_id="$1"
	if [ -z "$session_id" ]; then
		echo "ERROR: session_id must be provided in hook JSON input." >&2
		echo "   Hook requires session_id to track warning state." >&2
		exit 1
	fi
}

# Extract session ID and require it in one call
# Args: json_input
# Returns: session_id (exits if missing)
get_required_session_id() {
	local input="$1"
	local session_id=$(get_session_id "$input")
	require_session_id "$session_id"
	echo "$session_id"
}
