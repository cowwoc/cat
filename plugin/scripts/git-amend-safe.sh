#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# git-amend-safe.sh - Amend with pre-check and post-amend TOCTOU detection
# Usage: git-amend-safe.sh [--message "new message"] [--no-edit] [WORKTREE_PATH]
#
# This script implements safe amend with push status verification and race detection.

AMEND_MESSAGE=""
NO_EDIT=false
WORKTREE_PATH="."

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --message)
      AMEND_MESSAGE="$2"
      shift 2
      ;;
    --no-edit)
      NO_EDIT=true
      shift
      ;;
    *)
      WORKTREE_PATH="$1"
      shift
      ;;
  esac
done

# Record OLD_HEAD before amend
OLD_HEAD=$(git -C "$WORKTREE_PATH" rev-parse HEAD 2>&1)
if [[ $? -ne 0 ]]; then
  cat >&2 <<EOF
{
  "status": "ERROR",
  "message": "Failed to resolve HEAD: $OLD_HEAD"
}
EOF
  exit 1
fi

# Check push status: if commit already pushed, fail-fast
PUSH_STATUS=$(git -C "$WORKTREE_PATH" status --porcelain -b 2>/dev/null | head -1 || echo "")

# Check if branch is tracking a remote
if echo "$PUSH_STATUS" | grep -q "\["; then
  # Branch is tracking - check if ahead or up-to-date
  if echo "$PUSH_STATUS" | grep -q "ahead"; then
    # Branch is ahead - commit not pushed yet
    :
  elif echo "$PUSH_STATUS" | grep -q "behind\|up to date"; then
    # Commit appears to be pushed
    cat >&2 <<EOF
{
  "status": "ALREADY_PUSHED",
  "head": "$OLD_HEAD",
  "message": "Commit already pushed to remote. Amend would create divergent history."
}
EOF
    exit 1
  fi
fi

# Perform amend with appropriate flags
AMEND_CMD=(git -C "$WORKTREE_PATH" commit --amend)

if [[ "$NO_EDIT" == true ]]; then
  AMEND_CMD+=(--no-edit)
fi

if [[ -n "$AMEND_MESSAGE" ]]; then
  AMEND_CMD+=(-m "$AMEND_MESSAGE")
fi

AMEND_OUTPUT=$("${AMEND_CMD[@]}" 2>&1)
AMEND_EXIT=$?

if [[ $AMEND_EXIT -ne 0 ]]; then
  cat >&2 <<EOF
{
  "status": "ERROR",
  "old_head": "$OLD_HEAD",
  "message": "Amend failed: $AMEND_OUTPUT"
}
EOF
  exit 1
fi

# Record NEW_HEAD after amend
NEW_HEAD=$(git -C "$WORKTREE_PATH" rev-parse HEAD)

# Post-amend TOCTOU check: verify OLD_HEAD not pushed during amend
REMOTE_REF=$(git -C "$WORKTREE_PATH" rev-parse @{push} 2>/dev/null || echo "")
RACE_DETECTED=false

if [[ -n "$REMOTE_REF" ]]; then
  if git -C "$WORKTREE_PATH" merge-base --is-ancestor "$OLD_HEAD" "$REMOTE_REF" 2>/dev/null; then
    RACE_DETECTED=true
  fi
fi

if [[ "$RACE_DETECTED" == true ]]; then
  cat <<EOF
{
  "status": "RACE_DETECTED",
  "old_head": "$OLD_HEAD",
  "new_head": "$NEW_HEAD",
  "message": "Original commit was pushed during amend. Force-with-lease push needed.",
  "recovery": "git push --force-with-lease"
}
EOF
else
  cat <<EOF
{
  "status": "OK",
  "old_head": "$OLD_HEAD",
  "new_head": "$NEW_HEAD",
  "race_detected": false
}
EOF
fi
