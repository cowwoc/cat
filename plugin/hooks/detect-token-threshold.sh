#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Hook: detect-token-threshold.sh
# Trigger: PostToolUse for Bash
# Purpose: Warn when approaching context limit
# Return codes: 0=success, 1=soft error

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Read stdin (hook input from Claude Code)
INPUT=$(cat 2>/dev/null || echo "{}")

# Extract session_id for identifying the session file
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null || echo "")

if [[ -z "$SESSION_ID" ]]; then
    exit 0
fi

# Claude Code stores session data in ~/.claude/sessions/
SESSION_DIR="${HOME}/.claude/sessions"
SESSION_FILE="${SESSION_DIR}/${SESSION_ID}.json"

# Check if session file exists
if [[ ! -f "$SESSION_FILE" ]]; then
    exit 0
fi

# Try to read token usage from session file
# Note: The exact structure depends on Claude Code's session format
TOKEN_USAGE=$(jq -r '.token_usage // .usage // empty' "$SESSION_FILE" 2>/dev/null || echo "")

if [[ -z "$TOKEN_USAGE" || "$TOKEN_USAGE" == "null" ]]; then
    exit 0
fi

# Extract input/output tokens if available
INPUT_TOKENS=$(echo "$TOKEN_USAGE" | jq -r '.input_tokens // 0' 2>/dev/null || echo "0")
OUTPUT_TOKENS=$(echo "$TOKEN_USAGE" | jq -r '.output_tokens // 0' 2>/dev/null || echo "0")
TOTAL_TOKENS=$((INPUT_TOKENS + OUTPUT_TOKENS))

# Define threshold (40% of typical context limit)
# Claude's context is ~200k tokens, 40% = 80k
THRESHOLD=80000
WARNING_THRESHOLD=60000

if [[ $TOTAL_TOKENS -gt $THRESHOLD ]]; then
    # Output warning via hookSpecificOutput
    jq -n --argjson total "$TOTAL_TOKENS" --argjson threshold "$THRESHOLD" '{
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": "TOKEN WARNING: Context usage at \($total) tokens (threshold: \($threshold)). Consider completing current task and starting a new session."
        }
    }'
elif [[ $TOTAL_TOKENS -gt $WARNING_THRESHOLD ]]; then
    # Softer warning at 30% (60k tokens)
    jq -n --argjson total "$TOTAL_TOKENS" '{
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": "Token usage: \($total) tokens. Approaching context limit."
        }
    }'
fi

exit 0
