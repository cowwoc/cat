#!/bin/bash
# compute-box-lines.sh - Invisible box line computation via hook
#
# TRIGGER: PreToolUse (Bash)
#
# M192: Agent calculated box widths correctly but re-typed output from memory,
# causing alignment errors. This hook executes Python-based computation invisibly
# and returns results via additionalContext, preventing the agent from corrupting
# the output.
#
# USAGE: Agent invokes Bash with marker comment:
#   Bash("#BOX_COMPUTE\ncontent1\ncontent2\ncontent3")
#
# The hook intercepts this, runs Python to compute lines, and returns the result
# to Claude without showing the tool call to the user.

set -euo pipefail

# Source standard hook libraries
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/json-parser.sh"

# Initialize as Bash hook (reads stdin JSON, extracts command)
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

# Check for the BOX_COMPUTE marker
if ! echo "$HOOK_COMMAND" | head -1 | grep -q '^#BOX_COMPUTE'; then
    echo '{}'
    exit 0
fi

# Extract content items (all lines after the marker)
CONTENT_ITEMS=$(echo "$HOOK_COMMAND" | tail -n +2)

if [[ -z "$CONTENT_ITEMS" ]]; then
    # No content provided
    jq -n '{
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": "BOX_COMPUTE: No content items provided",
            "additionalContext": "Error: No content items provided for box computation"
        }
    }'
    exit 0
fi

# Find the Python script
PLUGIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PYTHON_SCRIPT="$PLUGIN_ROOT/scripts/build-box-lines.py"

if [[ ! -f "$PYTHON_SCRIPT" ]]; then
    jq -n --arg reason "BOX_COMPUTE: Python script not found at $PYTHON_SCRIPT" '{
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": $reason,
            "additionalContext": "Error: build-box-lines.py not found"
        }
    }'
    exit 0
fi

# Run the Python script with content items
# Use --format lines for direct output
RESULT=$(echo "$CONTENT_ITEMS" | python3 "$PYTHON_SCRIPT" --format json 2>&1) || {
    jq -n --arg err "Python error: $RESULT" '{
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": "BOX_COMPUTE: Python execution failed",
            "additionalContext": $err
        }
    }'
    exit 0
}

# Format the result for Claude
# Include the JSON data so Claude can extract the exact lines
CONTEXT=$(cat << EOF
BOX_COMPUTE RESULT (copy these exact lines):

$RESULT

INSTRUCTIONS: Copy the "lines", "top", and "bottom" values exactly as shown.
Do not re-type or approximate - use these computed strings directly.
EOF
)

# Return result and block the original command
jq -n --arg context "$CONTEXT" '{
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": "BOX_COMPUTE: Computed invisibly via hook",
        "additionalContext": $context
    }
}'

exit 0
