#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# git-merge-linear.sh - Deterministic linear merge with race condition prevention
# Usage: git-merge-linear.sh <WORKTREE_PATH> [COMMIT_MESSAGE]
#
# This script implements the deterministic merge workflow from git-merge-linear skill.
# It pins the base branch reference at the start to prevent race conditions where the
# base branch advances between operations.

WORKTREE_PATH="${1:?ERROR: WORKTREE_PATH required as first argument}"
COMMIT_MESSAGE="${2:-}"

# Detect main repo and task branch
MAIN_REPO=$(git worktree list | head -1 | awk '{print $1}')
TASK_BRANCH=$(git -C "$WORKTREE_PATH" rev-parse --abbrev-ref HEAD)

# Verify WORKTREE_PATH is not main repo
if [[ "$WORKTREE_PATH" == "$MAIN_REPO" ]]; then
  echo "ERROR: WORKTREE_PATH points to main repo, not issue worktree" >&2
  echo "Set to: /workspace/.claude/cat/worktrees/<issue-name>" >&2
  exit 1
fi

# Detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git -C "$WORKTREE_PATH" rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: cat-base file not found: $CAT_BASE_FILE" >&2
  echo "This worktree was not created properly. Recreate with /cat:work." >&2
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

# Check for uncommitted changes
if ! git -C "$WORKTREE_PATH" diff --quiet || ! git -C "$WORKTREE_PATH" diff --cached --quiet; then
  echo "ERROR: Uncommitted changes detected. Commit or stash before merging." >&2
  exit 1
fi

# Pin base branch reference to prevent race conditions (CRITICAL - M199)
BASE=$(git -C "$WORKTREE_PATH" rev-parse "$BASE_BRANCH")

# Check if base branch has commits not in our history
DIVERGED_COMMITS=$(git -C "$WORKTREE_PATH" rev-list --count "HEAD..$BASE")

if [[ "$DIVERGED_COMMITS" -gt 0 ]]; then
  echo "ERROR: Base branch has diverged!" >&2
  echo "" >&2
  echo "$BASE_BRANCH has $DIVERGED_COMMITS commit(s) not in your branch." >&2
  echo "These commits would be LOST if you squash now." >&2
  echo "" >&2
  echo "Commits on $BASE_BRANCH not in HEAD:" >&2
  git -C "$WORKTREE_PATH" log --oneline "HEAD..$BASE" >&2
  echo "" >&2
  echo "Solution: Rebase onto $BASE_BRANCH first:" >&2
  echo "  git -C $WORKTREE_PATH rebase $BASE_BRANCH" >&2
  exit 1
fi

# Check for suspicious file deletions (CRITICAL - M233)
DELETED_FILES=$(git -C "$WORKTREE_PATH" diff --name-status "$BASE..HEAD" | grep "^D" | cut -f2 || true)

if [[ -n "$DELETED_FILES" ]]; then
  SUSPICIOUS=$(echo "$DELETED_FILES" | grep -E "^(\.claude/cat/|plugin/)" || true)

  if [[ -n "$SUSPICIOUS" ]]; then
    echo "ERROR: Suspicious deletions detected in infrastructure paths:" >&2
    echo "$SUSPICIOUS" >&2
    echo "" >&2
    echo "These deletions are likely from incorrect rebase conflict resolution." >&2
    echo "" >&2
    echo "Solution: Re-rebase with correct conflict resolution:" >&2
    echo "  git -C $WORKTREE_PATH reset --hard origin/${TASK_BRANCH}  # If remote has clean state" >&2
    exit 1
  fi
fi

# Count commits ahead of base branch
COMMIT_COUNT=$(git -C "$WORKTREE_PATH" rev-list --count "$BASE..HEAD")

if [[ "$COMMIT_COUNT" -eq 0 ]]; then
  echo "ERROR: No commits to merge" >&2
  exit 1
fi

# Squash commits if needed
if [[ "$COMMIT_COUNT" -gt 1 ]]; then
  if [[ -n "$COMMIT_MESSAGE" ]]; then
    # Use provided commit message
    FIRST_MSG="$COMMIT_MESSAGE"
  else
    # Use first commit message
    FIRST_MSG=$(git -C "$WORKTREE_PATH" log --format="%s" "$BASE..HEAD" | tail -1)
  fi

  # Get combined commit message from all commits
  COMBINED_MSG=$(git -C "$WORKTREE_PATH" log --reverse --format="- %s" "$BASE..HEAD")

  # Soft reset to base and create single commit
  git -C "$WORKTREE_PATH" reset --soft "$BASE"
  git -C "$WORKTREE_PATH" commit -m "$FIRST_MSG

Changes:
$COMBINED_MSG

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
fi

# Verify merge-base to ensure linear history
MERGE_BASE=$(git -C "$WORKTREE_PATH" merge-base HEAD "$BASE")
if [[ "$MERGE_BASE" != "$BASE" ]]; then
  cat >&2 <<EOF
{
  "status": "NOT_LINEAR",
  "message": "History is not linear",
  "merge_base": "$MERGE_BASE",
  "expected": "$BASE",
  "recovery_hint": "rebase_needed"
}
EOF
  exit 1
fi

# Fast-forward base branch to current HEAD without checking out
FF_OUTPUT=$(git -C "$WORKTREE_PATH" push . "HEAD:${BASE_BRANCH}" 2>&1)
FF_EXIT=$?

if [[ $FF_EXIT -ne 0 ]]; then
  cat >&2 <<EOF
{
  "status": "FF_FAILED",
  "message": "Base branch advanced during merge",
  "base_branch": "$BASE_BRANCH",
  "recovery_hint": "rebase_needed"
}
EOF
  exit 1
fi

# Verify base branch was updated
UPDATED_BASE=$(git -C "$WORKTREE_PATH" rev-parse "$BASE_BRANCH")
HEAD_SHA=$(git -C "$WORKTREE_PATH" rev-parse HEAD)

if [[ "$UPDATED_BASE" != "$HEAD_SHA" ]]; then
  echo "ERROR: $BASE_BRANCH not at expected commit" >&2
  echo "$BASE_BRANCH: $UPDATED_BASE | HEAD: $HEAD_SHA" >&2
  exit 1
fi

# Count files changed
FILES_CHANGED=$(git -C "$WORKTREE_PATH" diff --name-only "$BASE" HEAD | wc -l)

# Cleanup: worktree, branch, empty directory
git -C "$MAIN_REPO" worktree remove "$WORKTREE_PATH" --force 2>/dev/null || true
git -C "$MAIN_REPO" branch -D "$TASK_BRANCH" 2>/dev/null || true
rmdir /workspace/.claude/cat/worktrees 2>/dev/null || true

# Output JSON for machine parsing
cat <<EOF
{
  "status": "OK",
  "base_branch": "$BASE_BRANCH",
  "commit": "$(git -C "$MAIN_REPO" rev-parse --short "$BASE_BRANCH")",
  "commit_full": "$HEAD_SHA",
  "files_changed": $FILES_CHANGED
}
EOF
