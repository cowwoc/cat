#!/bin/bash
# Hook: warn-approval-without-renderdiff.sh
# Type: PreToolUse (AskUserQuestion)
# Purpose: Warn when presenting approval gate without render-diff output (M232)
#
# This hook detects when an approval gate is being presented during /cat:work
# and warns if render-diff.py wasn't used to display the diff.
#
# Related mistakes: M170, M171, M201, M211, M231, M232

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

# Check if we're in a CAT context (worktree or task branch)
CAT_BASE_FILE="$(git rev-parse --git-dir 2>/dev/null)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
    # Not in a CAT worktree, skip check
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

# Look for render-diff.py in recent Bash tool calls (last 50 lines)
RENDER_DIFF_FOUND=$(tail -50 "$SESSION_FILE" 2>/dev/null | grep -c "render-diff.py" || echo "0")

# Also check if the output contains box-drawing characters (╭╮╰╯│) indicating render-diff format
BOX_CHARS_FOUND=$(tail -100 "$SESSION_FILE" 2>/dev/null | grep -c "[╭╮╰╯│├┤]" || echo "0")

if [[ "$RENDER_DIFF_FOUND" -eq 0 ]] && [[ "$BOX_CHARS_FOUND" -lt 5 ]]; then
    echo "{\"additionalContext\": \"⚠️ PRE-APPROVAL CHECK: RENDER-DIFF NOT DETECTED (M232)\\n\\nApproval gate requires showing diff in 4-column table format using render-diff skill.\\n\\nBEFORE presenting approval:\\n1. Run: git diff \\\${BASE_BRANCH}..HEAD | \\\\\"\\\${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py\\\"\\n2. Present the VERBATIM output with box characters (╭╮╰╯│)\\n3. Then show the approval question\\n\\nSee: work.md approval gate requirements (M160/M201/M211/M231/M232)\"}"
    exit 0
fi

echo '{}'
