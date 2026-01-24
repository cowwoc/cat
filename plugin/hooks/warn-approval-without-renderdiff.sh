#!/bin/bash
# Hook: warn-approval-without-renderdiff.sh
# Type: PreToolUse (AskUserQuestion)
# Purpose: Warn when presenting approval gate without render-diff output (M232)
#
# This hook detects when an approval gate is being presented during /cat:work
# and warns if render-diff.py wasn't used to display the diff.
#
# Related mistakes: M170, M171, M201, M211, M231, M232, R013/A021

set -euo pipefail

# Only run for AskUserQuestion
TOOL_NAME="${TOOL_NAME:-}"
if [[ "$TOOL_NAME" != "AskUserQuestion" ]]; then
    echo '{}'
    exit 0
fi

# Get the tool input to check if this is an approval-related question
TOOL_INPUT="${TOOL_INPUT:-}"

# Check if this looks like an approval gate (contains "Approve" in options or question)
if ! echo "$TOOL_INPUT" | grep -qi "approve"; then
    echo '{}'
    exit 0
fi

# Check if we're in a CAT project (has .claude/cat directory)
# Note: Don't require being in a worktree - approval can happen from main workspace
CLAUDE_PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
if [[ ! -d "$CLAUDE_PROJECT_DIR/.claude/cat" ]]; then
    echo '{}'
    exit 0
fi

# Check session log for render-diff.py invocation in recent assistant messages
SESSION_ID="${CLAUDE_SESSION_ID:-}"
if [[ -z "$SESSION_ID" ]]; then
    echo '{}'
    exit 0
fi

SESSION_FILE="${HOME}/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"
if [[ ! -f "$SESSION_FILE" ]]; then
    echo '{}'
    exit 0
fi

# Look for render-diff.py in recent Bash tool calls (last 100 lines for larger sessions)
# Use tr to remove newlines and ensure clean numeric output
RENDER_DIFF_FOUND=$(tail -100 "$SESSION_FILE" 2>/dev/null | grep -c "render-diff.py" 2>/dev/null | tr -d '[:space:]' || true)
[[ -z "$RENDER_DIFF_FOUND" || ! "$RENDER_DIFF_FOUND" =~ ^[0-9]+$ ]] && RENDER_DIFF_FOUND=0

# Also check if the output contains box-drawing characters (╭╮╰╯│) indicating render-diff format
# We need a good amount to confirm it's actually a diff table, not just a status box
BOX_CHARS_FOUND=$(tail -200 "$SESSION_FILE" 2>/dev/null | grep -c "[╭╮╰╯│├┤]" 2>/dev/null | tr -d '[:space:]' || true)
[[ -z "$BOX_CHARS_FOUND" || ! "$BOX_CHARS_FOUND" =~ ^[0-9]+$ ]] && BOX_CHARS_FOUND=0

# Check for diff-related content patterns that should use render-diff
# Look for signs of diff content that might have been manually formatted
MANUAL_DIFF_SIGNS=$(tail -200 "$SESSION_FILE" 2>/dev/null | grep -cE '^\+\+\+|^---|^@@' 2>/dev/null | tr -d '[:space:]' || true)
[[ -z "$MANUAL_DIFF_SIGNS" || ! "$MANUAL_DIFF_SIGNS" =~ ^[0-9]+$ ]] && MANUAL_DIFF_SIGNS=0

if [[ "$RENDER_DIFF_FOUND" -eq 0 ]] && [[ "$BOX_CHARS_FOUND" -lt 20 ]]; then
    # No render-diff detected - issue warning
    WARNING_MSG="⚠️ RENDER-DIFF NOT DETECTED (M232/A021)

Approval gate REQUIRES 4-column table diff format.

BEFORE presenting approval:
1. Run: git diff \${BASE_BRANCH}..HEAD | \"\${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py\"
2. Present the VERBATIM output (must have ╭╮╰╯│ box characters)
3. DO NOT reformat, summarize, or excerpt the output
4. Then show the approval question

If diff is large, present ALL of it across multiple messages.
NEVER summarize with 'remaining files show...' (M231)"

    echo "{\"additionalContext\": \"${WARNING_MSG//$'\n'/\\n}\"}"
    exit 0
fi

# render-diff was invoked, but check if output might have been reformatted (M211)
if [[ "$RENDER_DIFF_FOUND" -gt 0 ]] && [[ "$BOX_CHARS_FOUND" -lt 10 ]] && [[ "$MANUAL_DIFF_SIGNS" -gt 5 ]]; then
    # render-diff was called but box characters are missing and manual diff signs present
    # This suggests the agent reformatted the output instead of presenting verbatim
    WARNING_MSG="⚠️ RENDER-DIFF OUTPUT MAY BE REFORMATTED (M211)

render-diff.py was invoked but box characters (╭╮╰╯│) are sparse.
The diff may have been reformatted into plain diff format.

REQUIREMENT: Present render-diff output VERBATIM - copy-paste exactly.
DO NOT extract into code blocks or reformat as standard diff.

The user must see the actual 4-column table output."

    echo "{\"additionalContext\": \"${WARNING_MSG//$'\n'/\\n}\"}"
    exit 0
fi

echo '{}'
