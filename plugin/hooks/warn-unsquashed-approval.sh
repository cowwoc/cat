#!/bin/bash
# Hook: warn-unsquashed-approval.sh
# Type: PreToolUse (AskUserQuestion)
# Purpose: Warn when presenting approval gate with unsquashed commits (M199)
#
# This hook detects when an approval gate is being presented during /cat:work
# and warns if commits haven't been squashed yet.

set -euo pipefail

# Only run for AskUserQuestion
TOOL_NAME="${TOOL_NAME:-}"
if [[ "$TOOL_NAME" != "AskUserQuestion" ]]; then
    echo '{}'
    exit 0
fi

# Check if we're in a task worktree
if [[ ! -f "$(git rev-parse --git-dir 2>/dev/null)/cat-base" ]]; then
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

# Get base branch from worktree metadata
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
BASE_BRANCH=$(cat "$CAT_BASE_FILE" 2>/dev/null || echo "")

if [[ -z "$BASE_BRANCH" ]]; then
    echo '{}'
    exit 0
fi

# Count commits on task branch
COMMIT_COUNT=$(git rev-list --count "${BASE_BRANCH}..HEAD" 2>/dev/null || echo "0")

# If more than 2 commits, warn about squashing
if [[ "$COMMIT_COUNT" -gt 2 ]]; then
    output_hook_message "PreToolUse" "$(cat << EOF
⚠️ PRE-APPROVAL CHECK FAILED: UNSQUASHED COMMITS (M199)

Found ${COMMIT_COUNT} commits on task branch (expected 1-2 after squashing).

Commits on branch:
$(git log --oneline "${BASE_BRANCH}..HEAD")

BLOCKING: Run /cat:git-squash BEFORE presenting approval gate.
Per work.md Pre-Approval Checklist, commits must be squashed by type first.
EOF
)"
    exit 0
fi

echo '{}'
