#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# check-existing-work.sh - Check if a task branch has existing commits (M362)
#
# This is a deterministic check that does NOT require LLM decision-making.
# It should be called by work-prepare after worktree creation to detect
# if previous work exists on the branch.
#
# Usage:
#   check-existing-work.sh --worktree PATH --base-branch BRANCH
#
# Output (JSON):
#   {"has_existing_work":true,"existing_commits":3,"commit_summary":"abc1234 message..."}
#   {"has_existing_work":false,"existing_commits":0,"commit_summary":""}

set -uo pipefail

# =============================================================================
# ARGUMENT PARSING
# =============================================================================

WORKTREE_PATH=""
BASE_BRANCH=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --worktree)
            WORKTREE_PATH="$2"
            shift 2
            ;;
        --base-branch)
            BASE_BRANCH="$2"
            shift 2
            ;;
        *)
            echo '{"error":"Unknown argument: '"$1"'"}' >&2
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$WORKTREE_PATH" ]]; then
    echo '{"error":"--worktree is required"}' >&2
    exit 1
fi

if [[ -z "$BASE_BRANCH" ]]; then
    echo '{"error":"--base-branch is required"}' >&2
    exit 1
fi

# =============================================================================
# CHECK FOR EXISTING WORK
# =============================================================================

# Verify worktree exists
if [[ ! -d "$WORKTREE_PATH" ]]; then
    echo '{"error":"Cannot access worktree: '"$WORKTREE_PATH"'"}' >&2
    exit 1
fi

# Count commits ahead of base branch
COMMIT_COUNT=$(git -C "$WORKTREE_PATH" rev-list --count "${BASE_BRANCH}..HEAD" 2>/dev/null || echo "0")

if [[ "$COMMIT_COUNT" -gt 0 ]]; then
    # Get commit summary (first 5 commits, one line each)
    COMMIT_SUMMARY=$(git -C "$WORKTREE_PATH" log --oneline "${BASE_BRANCH}..HEAD" -5 2>/dev/null | head -5 | tr '\n' '|' | sed 's/|$//')

    # Output JSON
    cat <<EOF
{
  "has_existing_work": true,
  "existing_commits": ${COMMIT_COUNT},
  "commit_summary": "$(echo "$COMMIT_SUMMARY" | sed 's/"/\\"/g')"
}
EOF
else
    # No existing work
    cat <<EOF
{
  "has_existing_work": false,
  "existing_commits": 0,
  "commit_summary": ""
}
EOF
fi
