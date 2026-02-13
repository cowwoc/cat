#!/usr/bin/env bash
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

# Pin base branch reference BEFORE rebase to prevent race conditions
BASE=$(git -C "$WORKTREE_PATH" rev-parse "$BASE_BRANCH")

# Rebase onto pinned base
REBASE_OUTPUT=$(git -C "$WORKTREE_PATH" rebase "$BASE" 2>&1)
REBASE_EXIT=$?

if [[ $REBASE_EXIT -ne 0 ]]; then
  # Rebase failed - check if it's a conflict
  CONFLICTING_FILES=$(git -C "$WORKTREE_PATH" diff --name-only --diff-filter=U 2>/dev/null || echo "")

  # Create backup before aborting
  BACKUP="backup-after-rebase-conflict-$(date +%Y%m%d-%H%M%S)"
  git -C "$WORKTREE_PATH" branch "$BACKUP" 2>/dev/null || true

  # Abort rebase to return to clean state
  git -C "$WORKTREE_PATH" rebase --abort 2>/dev/null || true

  if [[ -n "$CONFLICTING_FILES" ]]; then
    # Format conflicting files as JSON array
    FILES_JSON=$(echo "$CONFLICTING_FILES" | python3 -c "import sys, json; print(json.dumps([line.strip() for line in sys.stdin if line.strip()]))")

    cat >&2 <<EOF
{
  "status": "REBASE_CONFLICT",
  "backup_branch": "$BACKUP",
  "message": "Conflict during pre-squash rebase",
  "conflicting_files": $FILES_JSON
}
EOF
  else
    cat >&2 <<EOF
{
  "status": "ERROR",
  "backup_branch": "$BACKUP",
  "message": "Rebase failed: $REBASE_OUTPUT"
}
EOF
  fi
  exit 1
fi

# Create timestamped backup branch
BACKUP="backup-before-squash-$(date +%Y%m%d-%H%M%S)"
git -C "$WORKTREE_PATH" branch "$BACKUP"

# Verify backup was created (fail-fast if missing)
if ! git -C "$WORKTREE_PATH" show-ref --verify --quiet "refs/heads/$BACKUP"; then
  echo "ERROR: Backup branch '$BACKUP' was not created" >&2
  echo "Do NOT proceed with squash without backup." >&2
  exit 1
fi

# Verify clean working directory
if [[ -n "$(git -C "$WORKTREE_PATH" status --porcelain)" ]]; then
  echo "ERROR: Working directory is not clean" >&2
  git -C "$WORKTREE_PATH" status --porcelain >&2
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
  cat >&2 <<EOF
{
  "status": "VERIFY_FAILED",
  "backup_branch": "$BACKUP",
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
  exit 1
fi

# Delete backup
git -C "$WORKTREE_PATH" branch -D "$BACKUP" >/dev/null

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
