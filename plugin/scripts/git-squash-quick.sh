#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# git-squash-quick.sh - Deterministic squash via commit-tree with race condition prevention
# Usage: git-squash-quick.sh <BASE_BRANCH> <COMMIT_MESSAGE> [WORKTREE_PATH]
#
# This script implements the Quick Workflow from git-squash skill using commit-tree.
# It pins the base branch reference at the start to prevent race conditions where the
# base branch advances between the rebase and commit-tree operations.

BASE_BRANCH="${1:?ERROR: BASE_BRANCH required as first argument}"
COMMIT_MESSAGE="${2:?ERROR: COMMIT_MESSAGE required as second argument}"
WORKTREE_PATH="${3:-.}"

# Capture current branch name for scoped backup naming
CURRENT_BRANCH=$(git -C "$WORKTREE_PATH" branch --show-current)

# Clean up orphaned backup branches from previous failed squash attempts on THIS branch
for orphan in $(git -C "$WORKTREE_PATH" branch --list "backup-before-squash-${CURRENT_BRANCH}-*" --format='%(refname:short)' 2>/dev/null); do
  git -C "$WORKTREE_PATH" branch -D "$orphan" >/dev/null 2>&1 || true
done

# Cleanup handler: delete backup branch if script exits after creating it
BACKUP=""
cleanup_backup() {
  if [[ -n "$BACKUP" ]]; then
    git -C "$WORKTREE_PATH" branch -D "$BACKUP" >/dev/null 2>&1 || true
  fi
}
trap cleanup_backup EXIT

# Validate commit message format
if ! echo "$COMMIT_MESSAGE" | grep -qE '^(feature|bugfix|refactor|test|performance|config|planning|docs): \S'; then
  cat >&2 <<EOF
ERROR: Invalid commit message format.

Commit message must start with a valid type prefix:
  feature:     New capability
  bugfix:      Bug fix
  refactor:    Code restructure
  test:        Test addition/modification
  performance: Optimization
  config:      Configuration change
  planning:    Issue tracking
  docs:        Documentation

Received: $COMMIT_MESSAGE

Example: feature: add user authentication
EOF
  cat <<EOF
{
  "status": "INVALID_MESSAGE",
  "message": "Commit message must start with a valid type prefix",
  "received": $(echo "$COMMIT_MESSAGE" | python3 -c "import sys, json; print(json.dumps(sys.stdin.read().strip()))")
}
EOF
  exit 1
fi

# Pin base branch reference BEFORE rebase to prevent race conditions
BASE=$(git -C "$WORKTREE_PATH" rev-parse "$BASE_BRANCH")

# Rebase onto pinned base
REBASE_OUTPUT=$(git -C "$WORKTREE_PATH" rebase "$BASE" 2>&1)
REBASE_EXIT=$?

if [[ $REBASE_EXIT -ne 0 ]]; then
  # Rebase failed - check if it's a conflict
  CONFLICTING_FILES=$(git -C "$WORKTREE_PATH" diff --name-only --diff-filter=U 2>/dev/null || echo "")

  # Create backup before aborting
  BACKUP="backup-after-rebase-conflict-${CURRENT_BRANCH}-$(date +%Y%m%d-%H%M%S)"
  git -C "$WORKTREE_PATH" branch "$BACKUP" 2>/dev/null || true

  # Abort rebase to return to clean state
  git -C "$WORKTREE_PATH" rebase --abort 2>/dev/null || true

  # Preserve rebase conflict backup for caller recovery (clear so trap doesn't delete)
  REBASE_BACKUP="$BACKUP"
  BACKUP=""

  if [[ -n "$CONFLICTING_FILES" ]]; then
    # Format conflicting files as JSON array
    FILES_JSON=$(echo "$CONFLICTING_FILES" | python3 -c "import sys, json; print(json.dumps([line.strip() for line in sys.stdin if line.strip()]))")

    cat <<EOF
{
  "status": "REBASE_CONFLICT",
  "backup_branch": "$REBASE_BACKUP",
  "message": "Conflict during pre-squash rebase",
  "conflicting_files": $FILES_JSON
}
EOF
  else
    cat <<EOF
{
  "status": "ERROR",
  "backup_branch": "$REBASE_BACKUP",
  "message": "Rebase failed: $REBASE_OUTPUT"
}
EOF
  fi
  exit 1
fi

# Create timestamped backup branch
BACKUP="backup-before-squash-${CURRENT_BRANCH}-$(date +%Y%m%d-%H%M%S)"
git -C "$WORKTREE_PATH" branch "$BACKUP"

# Verify backup was created (fail-fast if missing)
if ! git -C "$WORKTREE_PATH" show-ref --verify --quiet "refs/heads/$BACKUP"; then
  echo "ERROR: Backup branch '$BACKUP' was not created" >&2
  echo "Do NOT proceed with squash without backup." >&2
  cat <<EOF
{
  "status": "BACKUP_FAILED",
  "message": "Backup branch was not created",
  "backup_branch": "$BACKUP"
}
EOF
  exit 1
fi

# Verify clean working directory
if [[ -n "$(git -C "$WORKTREE_PATH" status --porcelain)" ]]; then
  DIRTY_FILES=$(git -C "$WORKTREE_PATH" status --porcelain)
  echo "ERROR: Working directory is not clean" >&2
  echo "$DIRTY_FILES" >&2
  cat <<EOF
{
  "status": "DIRTY_WORKTREE",
  "message": "Working directory is not clean",
  "dirty_files": $(echo "$DIRTY_FILES" | python3 -c "import sys, json; print(json.dumps([line.strip() for line in sys.stdin if line.strip()]))")
}
EOF
  exit 1
fi

# Create squashed commit using commit-tree (M305)
# This uses COMMIT content directly, ignoring working directory state
TREE=$(git -C "$WORKTREE_PATH" rev-parse HEAD^{tree})
NEW_COMMIT=$(git -C "$WORKTREE_PATH" commit-tree "$TREE" -p "$BASE" -m "$COMMIT_MESSAGE")

# Move branch to new squashed commit
git -C "$WORKTREE_PATH" reset --hard "$NEW_COMMIT"

# Verify: diff with backup is empty
DIFF_OUTPUT=$(git -C "$WORKTREE_PATH" diff "$BACKUP" 2>&1)
if [[ -n "$DIFF_OUTPUT" ]]; then
  DIFF_STAT=$(git -C "$WORKTREE_PATH" diff "$BACKUP" --stat 2>&1)
  # Preserve backup for investigation (clear so trap doesn't delete)
  VERIFY_BACKUP="$BACKUP"
  BACKUP=""
  cat <<EOF
{
  "status": "VERIFY_FAILED",
  "backup_branch": "$VERIFY_BACKUP",
  "message": "Content changed during squash - backup preserved",
  "diff_stat": $(echo "$DIFF_STAT" | python3 -c "import sys, json; print(json.dumps(sys.stdin.read()))")
}
EOF
  exit 1
fi

# Verify: exactly 1 commit from base
COMMIT_COUNT=$(git -C "$WORKTREE_PATH" rev-list --count "$BASE..HEAD")
if [[ "$COMMIT_COUNT" -ne 1 ]]; then
  echo "ERROR: Expected 1 commit from base, got $COMMIT_COUNT" >&2
  echo "Restoring backup..." >&2
  git -C "$WORKTREE_PATH" reset --hard "$BACKUP"
  RESTORE_BACKUP="$BACKUP"
  BACKUP=""
  cat <<EOF
{
  "status": "COMMIT_COUNT_MISMATCH",
  "message": "Expected 1 commit from base, got $COMMIT_COUNT",
  "expected": 1,
  "actual": $COMMIT_COUNT,
  "backup_branch": "$RESTORE_BACKUP",
  "restored": true
}
EOF
  exit 1
fi

# Delete backup (clear variable so trap doesn't re-delete)
git -C "$WORKTREE_PATH" branch -D "$BACKUP" >/dev/null
BACKUP=""

# Output JSON for machine parsing
cat <<EOF
{
  "status": "OK",
  "commit": "$(git -C "$WORKTREE_PATH" rev-parse --short HEAD)",
  "commit_full": "$NEW_COMMIT",
  "backup_verified": true,
  "backup_deleted": true
}
EOF
