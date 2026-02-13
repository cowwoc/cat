#!/usr/bin/env bash
set -euo pipefail

# git-rebase-safe.sh - Rebase with backup and fail-fast on conflicts
# Usage: git-rebase-safe.sh <WORKTREE_PATH> [TARGET_BRANCH]
#
# This script implements safe rebase with automatic backup and conflict detection.
# If TARGET_BRANCH not provided, reads from cat-base file.

WORKTREE_PATH="${1:?ERROR: WORKTREE_PATH required as first argument}"
TARGET_BRANCH="${2:-}"

# If TARGET_BRANCH not provided, read from cat-base file
if [[ -z "$TARGET_BRANCH" ]]; then
  CAT_BASE_FILE="$(git -C "$WORKTREE_PATH" rev-parse --git-dir)/cat-base"
  if [[ ! -f "$CAT_BASE_FILE" ]]; then
    cat >&2 <<EOF
{
  "status": "ERROR",
  "message": "cat-base file not found: $CAT_BASE_FILE. Recreate worktree with /cat:work.",
  "backup_branch": null
}
EOF
    exit 1
  fi
  TARGET_BRANCH=$(cat "$CAT_BASE_FILE")
fi

# Pin target ref to prevent race conditions
BASE=$(git -C "$WORKTREE_PATH" rev-parse "$TARGET_BRANCH" 2>&1)
if [[ $? -ne 0 ]]; then
  cat >&2 <<EOF
{
  "status": "ERROR",
  "message": "Failed to resolve target branch: $TARGET_BRANCH",
  "backup_branch": null
}
EOF
  exit 1
fi

# Create timestamped backup branch
BACKUP="backup-before-rebase-$(date +%Y%m%d-%H%M%S)"
git -C "$WORKTREE_PATH" branch "$BACKUP"

# Verify backup exists (fail-fast)
if ! git -C "$WORKTREE_PATH" show-ref --verify --quiet "refs/heads/$BACKUP"; then
  cat >&2 <<EOF
{
  "status": "ERROR",
  "message": "Backup branch '$BACKUP' was not created. Do NOT proceed with rebase without backup.",
  "backup_branch": null
}
EOF
  exit 1
fi

# Attempt rebase
REBASE_OUTPUT=$(git -C "$WORKTREE_PATH" rebase "$BASE" 2>&1 || true)
REBASE_EXIT=$?

if [[ $REBASE_EXIT -ne 0 ]]; then
  # Rebase failed - check if it's a conflict
  CONFLICTING_FILES=$(git -C "$WORKTREE_PATH" diff --name-only --diff-filter=U 2>/dev/null || echo "")

  # Abort rebase to return to clean state
  git -C "$WORKTREE_PATH" rebase --abort 2>/dev/null || true

  if [[ -n "$CONFLICTING_FILES" ]]; then
    # Format conflicting files as JSON array
    FILES_JSON=$(echo "$CONFLICTING_FILES" | python3 -c "import sys, json; print(json.dumps([line.strip() for line in sys.stdin if line.strip()]))")

    cat >&2 <<EOF
{
  "status": "CONFLICT",
  "target": "$BASE",
  "backup_branch": "$BACKUP",
  "conflicting_files": $FILES_JSON,
  "message": "Rebase conflict - backup preserved at $BACKUP"
}
EOF
  else
    cat >&2 <<EOF
{
  "status": "ERROR",
  "target": "$BASE",
  "backup_branch": "$BACKUP",
  "message": "Rebase failed: $REBASE_OUTPUT"
}
EOF
  fi
  exit 1
fi

# Rebase succeeded - verify no content changes vs backup
DIFF_STAT=$(git -C "$WORKTREE_PATH" diff "$BACKUP" --stat 2>&1 || echo "")
if [[ -n "$DIFF_STAT" ]]; then
  cat >&2 <<EOF
{
  "status": "ERROR",
  "target": "$BASE",
  "backup_branch": "$BACKUP",
  "message": "Content changed during rebase - backup preserved for investigation",
  "diff_stat": $(echo "$DIFF_STAT" | python3 -c "import sys, json; print(json.dumps(sys.stdin.read()))")
}
EOF
  exit 1
fi

# Count commits rebased
COMMITS_REBASED=$(git -C "$WORKTREE_PATH" rev-list --count "$BASE..HEAD")

# Delete backup
git -C "$WORKTREE_PATH" branch -D "$BACKUP" >/dev/null

# Output success JSON
cat <<EOF
{
  "status": "OK",
  "target": "$BASE",
  "commits_rebased": $COMMITS_REBASED,
  "backup_cleaned": true
}
EOF
