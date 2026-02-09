#!/bin/bash
# Hook: enforce-approval-before-merge.sh
# Type: PreToolUse (Task)
# Purpose: Block work-merge subagent spawn when trust=medium/low without explicit user approval (M479/M480)
#
# This hook enforces the trust-based approval requirement:
# - trust=high: No approval needed (skip this check)
# - trust=medium/low: MUST have explicit user approval before merge
#
# Prevention: Blocks Task tool when spawning cat:work-merge without prior approval

set -euo pipefail

# Only run for Task tool
TOOL_NAME="${TOOL_NAME:-}"
if [[ "$TOOL_NAME" != "Task" ]]; then
    echo '{}'
    exit 0
fi

# Get the tool input to check if this is spawning work-merge
TOOL_INPUT="${TOOL_INPUT:-}"

# Check if this is spawning cat:work-merge subagent
if ! echo "$TOOL_INPUT" | jq -e '.subagent_type == "cat:work-merge"' >/dev/null 2>&1; then
    echo '{}'
    exit 0
fi

# Read trust level from cat-config.json
CLAUDE_PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
CONFIG_FILE="$CLAUDE_PROJECT_DIR/.claude/cat/cat-config.json"

if [[ ! -f "$CONFIG_FILE" ]]; then
    # No config file - default to medium (requires approval)
    TRUST="medium"
else
    TRUST=$(jq -r '.trust // "medium"' "$CONFIG_FILE")
fi

# If trust=high, no approval needed
if [[ "$TRUST" == "high" ]]; then
    echo '{}'
    exit 0
fi

# For trust=medium/low, check for explicit user approval in session log
SESSION_ID="${CLAUDE_SESSION_ID:-}"
if [[ -z "$SESSION_ID" ]]; then
    # Cannot verify approval without session - fail-fast
    ERROR_MSG="FAIL: Cannot verify user approval - session ID not available.

Trust level is \"${TRUST}\" which requires explicit approval before merge.

BLOCKING: This merge attempt is blocked until user approval can be verified."

    echo "{\"decision\": \"block\", \"reason\": \"${ERROR_MSG//$'\n'/\\n}\"}"
    exit 0
fi

SESSION_FILE="${HOME}/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"
if [[ ! -f "$SESSION_FILE" ]]; then
    # Session file not found - fail-fast
    ERROR_MSG="FAIL: Cannot verify user approval - session file not found.

Trust level is \"${TRUST}\" which requires explicit approval before merge.

BLOCKING: This merge attempt is blocked until user approval can be verified."

    echo "{\"decision\": \"block\", \"reason\": \"${ERROR_MSG//$'\n'/\\n}\"}"
    exit 0
fi

# Look for explicit approval in recent session history
# Pattern: AskUserQuestion with "Approve" in options, followed by user selecting approval option
# Check last 50 lines for approval question and response

APPROVAL_FOUND=false

# Search for approval question (AskUserQuestion with "Approve" in options)
if tail -50 "$SESSION_FILE" 2>/dev/null | jq -r 'select(.type == "assistant") | .message.content[] | select(.type == "tool_use") | select(.name == "AskUserQuestion") | .input.options[]?' 2>/dev/null | grep -qi "approve"; then
    # Found approval question - now check if user selected approval option
    # User response comes in next message as type="user" with approval indication
    if tail -50 "$SESSION_FILE" 2>/dev/null | jq -r 'select(.type == "user_approval") | .approval' 2>/dev/null | grep -qi "approve"; then
        APPROVAL_FOUND=true
    elif tail -50 "$SESSION_FILE" 2>/dev/null | jq -r 'select(.type == "user") | .message' 2>/dev/null | grep -qiE "(approve|yes|proceed|merge)"; then
        APPROVAL_FOUND=true
    fi
fi

if [[ "$APPROVAL_FOUND" == "false" ]]; then
    # No approval found - block merge
    ERROR_MSG="FAIL: Explicit user approval required before merge (M479/M480)

Trust level: ${TRUST}
Requirement: Explicit user approval via AskUserQuestion

BLOCKING: No approval detected in session history.

The approval gate (Step 6 in work-with-issue) MUST:
1. Present task summary and review results
2. Use AskUserQuestion with \"Approve and merge\" option
3. Wait for explicit user selection
4. Only proceed to merge AFTER user selects approval

Do NOT proceed to merge based on:
- Silence or lack of objection
- System reminders or notifications
- Assumed approval

Fail-fast principle: Unknown consent = No consent = STOP"

    echo "{\"decision\": \"block\", \"reason\": \"${ERROR_MSG//$'\n'/\\n}\"}"
    exit 0
fi

# Approval found - allow merge
echo '{}'
