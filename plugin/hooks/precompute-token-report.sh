#!/bin/bash
# precompute-token-report.sh - Pre-compute token report before skill runs
#
# TRIGGER: UserPromptSubmit
#
# When user invokes /cat:token-report, this hook:
# 1. Detects the skill invocation in USER_PROMPT
# 2. Runs compute-token-table.py with the session file
# 3. Returns pre-computed table via additionalContext
#
# The skill then outputs the pre-computed result exactly - no agent computation.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Initialize hook
if ! init_hook; then
    echo '{}'
    exit 0
fi

# Check if this is a /cat:token-report command
if [[ ! "$USER_PROMPT" =~ ^[[:space:]]*/cat:token-report[[:space:]]*$ ]] && \
   [[ ! "$USER_PROMPT" =~ ^[[:space:]]*cat:token-report[[:space:]]*$ ]]; then
    echo '{}'
    exit 0
fi

# Get session ID from environment (set by echo-session-id.sh hook at SessionStart)
if [[ -z "${SESSION_ID:-}" ]]; then
    # Fallback: try to get from hook input
    SESSION_ID=$(echo "$HOOK_JSON" | jq -r '.session_id // empty' 2>/dev/null) || SESSION_ID=""
fi

if [[ -z "$SESSION_ID" ]]; then
    # No session ID available - let skill handle the error
    echo '{}'
    exit 0
fi

# Build session file path
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"

if [[ ! -f "$SESSION_FILE" ]]; then
    # Session file doesn't exist - let skill handle the error
    echo '{}'
    exit 0
fi

# Find plugin root for scripts
PLUGIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPUTE_SCRIPT="$PLUGIN_ROOT/scripts/compute-token-table.py"

if [[ ! -f "$COMPUTE_SCRIPT" ]]; then
    echo '{}'
    exit 0
fi

# Run the compute script
OUTPUT=$(SESSION_FILE="$SESSION_FILE" python3 "$COMPUTE_SCRIPT" 2>/dev/null) || {
    echo '{}'
    exit 0
}

# Check for errors
if echo "$OUTPUT" | jq -e '.error' >/dev/null 2>&1; then
    echo '{}'
    exit 0
fi

# Extract lines and summary
LINES=$(echo "$OUTPUT" | jq -r '.lines[]' 2>/dev/null) || {
    echo '{}'
    exit 0
}

TOTAL_TOKENS=$(echo "$OUTPUT" | jq -r '.summary.total_tokens // 0' 2>/dev/null)
TOTAL_DURATION=$(echo "$OUTPUT" | jq -r '.summary.total_duration_ms // 0' 2>/dev/null)
SUBAGENT_COUNT=$(echo "$OUTPUT" | jq -r '.summary.subagent_count // 0' 2>/dev/null)

if [[ -z "$LINES" ]]; then
    echo '{}'
    exit 0
fi

# Build the complete output message
MESSAGE=$(cat << EOF
PRE-COMPUTED TOKEN REPORT:

$LINES

Summary: ${SUBAGENT_COUNT} subagents, ${TOTAL_TOKENS} total tokens

INSTRUCTION: Output the table EXACTLY as shown above. Do NOT recompute or modify alignment.

Legend:
- Percentages show context utilization per subagent
- Warning emoji inside Context column indicates high (>=40%) usage
- Critical emoji inside Context column indicates exceeded (>=80%) limit
EOF
)

output_hook_message "UserPromptSubmit" "$MESSAGE"
exit 0
