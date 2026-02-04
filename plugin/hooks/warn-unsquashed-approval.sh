#!/bin/bash
# Hook: warn-unsquashed-approval.sh
# Type: PreToolUse (AskUserQuestion)
# Purpose: Warn when presenting approval gate with unsquashed commits (M199/M224)
#
# This hook detects when an approval gate is being presented during /cat:work
# and warns if commits haven't been squashed yet.
#
# M224: Also checks main workspace for recent task commits that should be squashed.

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

# Check if we're in a task worktree
CAT_BASE_FILE="$(git rev-parse --git-dir 2>/dev/null)/cat-base"
if [[ -f "$CAT_BASE_FILE" ]]; then
    # In worktree - check commits against base branch
    BASE_BRANCH=$(cat "$CAT_BASE_FILE" 2>/dev/null || echo "")

    if [[ -n "$BASE_BRANCH" ]]; then
        COMMIT_COUNT=$(git rev-list --count "${BASE_BRANCH}..HEAD" 2>/dev/null || echo "0")

        if [[ "$COMMIT_COUNT" -gt 2 ]]; then
            echo "{\"additionalContext\": \"⚠️ PRE-APPROVAL CHECK FAILED: UNSQUASHED COMMITS (M199)\\n\\nFound ${COMMIT_COUNT} commits on task branch (expected 1-2 after squashing).\\n\\nBLOCKING: Run /cat:git-squash BEFORE presenting approval gate.\"}"
            exit 0
        fi
    fi
else
    # M224: In main workspace - check for recent task commits that weren't squashed
    # Check if last 2+ commits are related (same task or config/planning pair)
    LAST_TWO=$(git log --oneline -2 2>/dev/null || echo "")

    # If we have a planning/config commit followed by a task commit, they should be squashed
    if echo "$LAST_TWO" | head -1 | grep -qE "^[a-f0-9]+ (config|planning):.*progress" && \
       echo "$LAST_TWO" | tail -1 | grep -qE "^[a-f0-9]+ (feature|bugfix|refactor|test|docs):"; then
        echo "{\"additionalContext\": \"⚠️ PRE-APPROVAL CHECK: RELATED COMMITS SHOULD BE SQUASHED (M224)\\n\\nFound separate commits that should be combined:\\n$(echo \"$LAST_TWO\" | sed 's/^/  /')\\n\\nThe implementation commit and STATE.md update should be in the SAME commit.\\nSquash these commits before approval.\"}"
        exit 0
    fi
fi

echo '{}'
