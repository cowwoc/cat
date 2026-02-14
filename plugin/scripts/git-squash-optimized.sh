#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# git-squash-optimized.sh - Atomic git squash with comprehensive safety checks
#
# This script performs ALL git-squash operations in a single atomic execution,
# reducing LLM round-trips from 11+ to 2-3 while preserving all safety checks.
#
# Usage:
#   git-squash-optimized.sh <base_commit> <last_commit> <message_file> [branch]
#
# Parameters:
#   base_commit    - Parent of first commit to squash (e.g., "abc123")
#   last_commit    - Last commit to include in squash (e.g., "def456")
#   message_file   - File containing commit message
#   branch         - Optional branch to update (default: current branch)

# ============================================================================
# CONFIGURATION
# ============================================================================

BASE_COMMIT="${1:?ERROR: BASE_COMMIT required (parent of first commit to squash)}"
EXPECTED_LAST_COMMIT="${2:?ERROR: EXPECTED_LAST_COMMIT required (last commit to include)}"
COMMIT_MESSAGE_FILE="${3:?ERROR: COMMIT_MESSAGE_FILE required (path to message file)}"
ORIGINAL_BRANCH="${4:-}"

# Read commit message from file (allows multi-line messages)
if [[ ! -f "$COMMIT_MESSAGE_FILE" ]]; then
  echo "ERROR: Commit message file not found: $COMMIT_MESSAGE_FILE" >&2
  exit 1
fi

# ============================================================================
# PROGRESS TRACKING
# ============================================================================

source "$(dirname "$0")/lib/progress.sh"
progress_init 11

# ============================================================================
# EXECUTION RESULT TRACKING
# ============================================================================

RESULT_JSON="/tmp/git-squash-result-$$.json"
START_TIME=$(date +%s)

# Function to output structured JSON result
output_result() {
  local status="$1"
  local message="$2"
  local end_time=$(date +%s)
  local duration=$((end_time - START_TIME))

  cat > "$RESULT_JSON" <<EOF
{
  "status": "$status",
  "message": "$message",
  "duration_seconds": $duration,
  "backup_branch": "${BACKUP_BRANCH:-none}",
  "squashed_commit": "${SQUASHED_COMMIT:-none}",
  "commits_squashed": ${COMMIT_COUNT:-0},
  "working_directory": "$(pwd)",
  "timestamp": "$(date -Iseconds)"
}
EOF

  cat "$RESULT_JSON"
  [[ "$status" == "success" ]] && exit 0 || exit 1
}

# ============================================================================
# STEP 1: POSITION HEAD
# ============================================================================

progress_step "Positioning HEAD at last commit to squash"
if ! git checkout "$EXPECTED_LAST_COMMIT" 2>&1; then
  output_result "error" "Failed to checkout $EXPECTED_LAST_COMMIT"
fi

CURRENT_HEAD=$(git rev-parse HEAD)
EXPECTED_HEAD=$(git rev-parse "$EXPECTED_LAST_COMMIT")

if [[ "$CURRENT_HEAD" != "$EXPECTED_HEAD" ]]; then
  output_result "error" "HEAD is not at expected last commit: $EXPECTED_LAST_COMMIT"
fi

progress_done "HEAD positioned at $(git log --oneline -1 HEAD)"

# ============================================================================
# STEP 2: CREATE BACKUP
# ============================================================================

progress_step "Creating backup branch"
BACKUP_BRANCH="backup-before-squash-$(date +%Y%m%d-%H%M%S)"

if ! git branch "$BACKUP_BRANCH"; then
  output_result "error" "Failed to create backup branch"
fi

if ! git rev-parse --verify "$BACKUP_BRANCH" >/dev/null 2>&1; then
  output_result "error" "Backup branch verification failed"
fi

progress_done "Backup created: $BACKUP_BRANCH"
echo "   Restore command: git reset --hard $BACKUP_BRANCH"

# ============================================================================
# STEP 3: VERIFY PRECONDITIONS
# ============================================================================

progress_step "Verifying preconditions"

# Check working directory clean
if [[ -n "$(git status --porcelain)" ]]; then
  output_result "error" "Working directory not clean - commit or stash changes first"
fi
echo "  Working directory clean"

# Verify base commit exists
if ! git rev-parse --verify "$BASE_COMMIT" >/dev/null 2>&1; then
  output_result "error" "Base commit $BASE_COMMIT does not exist"
fi

# Show commits to be squashed
echo ""
echo "  Commits to squash:"
git log --oneline "$BASE_COMMIT..HEAD"

COMMIT_COUNT=$(git rev-list --count "$BASE_COMMIT..HEAD")
progress_done "Will squash $COMMIT_COUNT commits into 1"

# ============================================================================
# STEP 4: RECORD ORIGINAL STATE
# ============================================================================

progress_step "Recording original state"
ORIGINAL_HEAD=$(git rev-parse HEAD)

# Record commits before base (must remain unchanged)
if git rev-parse "${BASE_COMMIT}~1" >/dev/null 2>&1; then
  COMMITS_BEFORE_BASE=$(git rev-list "${BASE_COMMIT}~1")
else
  COMMITS_BEFORE_BASE=""
fi

progress_done "Original state recorded"

# ============================================================================
# STEP 5: EXECUTE SQUASH
# ============================================================================

progress_step "Executing squash"
if ! git reset --soft "$BASE_COMMIT"; then
  output_result "error" "git reset --soft failed"
fi

progress_done "Soft reset to base commit complete"

# ============================================================================
# STEP 6: VERIFY NO CHANGES LOST OR ADDED
# ============================================================================

progress_step "Verifying no changes lost or added"
DIFF_OUTPUT=$(git diff --stat "$BACKUP_BRANCH")

if [[ -n "$DIFF_OUTPUT" ]]; then
  echo "ERROR: Staged changes don't match original commits!" >&2
  echo "$DIFF_OUTPUT" >&2
  output_result "error" "Staged changes verification failed. Rollback: git reset --hard $BACKUP_BRANCH"
fi

progress_done "Verification passed: No changes lost or added"

# ============================================================================
# STEP 7: CREATE SQUASHED COMMIT
# ============================================================================

progress_step "Creating squashed commit"
if ! git commit -F "$COMMIT_MESSAGE_FILE"; then
  output_result "error" "git commit failed"
fi

SQUASHED_COMMIT=$(git rev-parse HEAD)
progress_done "Squashed commit created: $SQUASHED_COMMIT"

# ============================================================================
# STEP 8: VERIFY NON-SQUASHED COMMITS UNCHANGED
# ============================================================================

progress_step "Verifying non-squashed commits unchanged"

if [[ -n "$COMMITS_BEFORE_BASE" ]]; then
  CURRENT_COMMITS_BEFORE_BASE=$(git rev-list "${BASE_COMMIT}~1")
  if [[ "$COMMITS_BEFORE_BASE" != "$CURRENT_COMMITS_BEFORE_BASE" ]]; then
    output_result "error" "Commits before base were modified. Rollback: git reset --hard $BACKUP_BRANCH"
  fi
  echo "  Commits before base unchanged"
fi

# Verify final working tree matches original HEAD
TREE_DIFF=$(git diff --stat "$ORIGINAL_HEAD")
if [[ -n "$TREE_DIFF" ]]; then
  echo "ERROR: Working tree doesn't match original HEAD!" >&2
  echo "$TREE_DIFF" >&2
  output_result "error" "Working tree verification failed. Rollback: git reset --hard $BACKUP_BRANCH"
fi

progress_done "Final working tree matches original HEAD"

# ============================================================================
# STEP 9: FINAL COUNT VERIFICATION
# ============================================================================

progress_step "Final count verification"
NEW_COMMIT_COUNT=$(git rev-list --count "$BASE_COMMIT..HEAD")

if [[ "$NEW_COMMIT_COUNT" -ne 1 ]]; then
  output_result "error" "Expected 1 commit after squash, got $NEW_COMMIT_COUNT"
fi

progress_done "Squash successful: $COMMIT_COUNT commits → 1 commit"
echo ""
git log --oneline -3

# ============================================================================
# STEP 10: UPDATE BRANCH (if detached HEAD)
# ============================================================================

progress_step "Updating original branch"
if [[ -n "$ORIGINAL_BRANCH" ]] && [[ "$(git rev-parse --abbrev-ref HEAD)" == "HEAD" ]]; then
  git branch -f "$ORIGINAL_BRANCH" HEAD
  git checkout "$ORIGINAL_BRANCH"
  progress_done "Updated $ORIGINAL_BRANCH to squashed commit"
else
  progress_done "Already on branch or no update needed"
fi

# ============================================================================
# STEP 11: CLEANUP BACKUP
# ============================================================================

progress_step "Removing backup"
if git branch -D "$BACKUP_BRANCH"; then
  progress_done "Backup removed after successful verification"
else
  echo "⚠️  Warning: Failed to delete backup branch $BACKUP_BRANCH" >&2
  echo "   You can manually delete it with: git branch -D $BACKUP_BRANCH" >&2
fi

# ============================================================================
# SUCCESS
# ============================================================================

output_result "success" "Squash completed successfully: $COMMIT_COUNT commits → 1 commit"
