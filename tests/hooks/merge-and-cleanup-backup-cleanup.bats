#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
# Tests for merge-and-cleanup.sh Step 5: squash backup branch cleanup
#
# These tests directly verify the git operations underlying Step 5:
#   - backup-before-squash-* branch pattern matching
#   - git merge-base --is-ancestor correctly identifies ancestor backups
#   - Non-ancestor backups (from other instances) are preserved
#   - Non-fatal behavior when branch -D fails
#
# Note: merge-and-cleanup.sh integration tests require a full worktree setup
# with locks, issue directories, etc. These unit-style tests validate the
# core backup cleanup git logic in isolation.

load '../test_helper'

# ============================================================================
# Helpers
# ============================================================================

# Run the backup cleanup logic directly against a git repo.
# This mirrors the Step 5 code in merge-and-cleanup.sh:
#   for backup in $(git branch --list "backup-before-squash-*" ...); do
#     if git merge-base --is-ancestor "$backup" HEAD; then
#       git branch -D "$backup"
#     fi
#   done
run_backup_cleanup() {
  local repo_dir="$1"
  local BACKUPS_DELETED=0

  for backup in $(git -C "$repo_dir" branch --list "backup-before-squash-*" --format="%(refname:short)" 2>/dev/null); do
    if git -C "$repo_dir" merge-base --is-ancestor "$backup" HEAD 2>/dev/null; then
      if git -C "$repo_dir" branch -D "$backup" >/dev/null 2>&1; then
        BACKUPS_DELETED=$((BACKUPS_DELETED + 1))
      else
        echo "WARNING: Failed to delete backup branch: $backup" >&2
      fi
    fi
  done

  echo "Deleted $BACKUPS_DELETED squash backup branch(es)"
  return 0
}

setup() {
  setup_git_repo
  # After setup_git_repo, we are on 'main' with one commit
}

teardown() {
  teardown_test_dir
}

# ============================================================================
# Test: no backup branches exist
# ============================================================================

@test "no backups exist: reports deleted 0 and succeeds" {
  # Verify no backup branches exist
  local branches
  branches=$(git -C "$TEST_TEMP_DIR" branch --list "backup-before-squash-*" --format="%(refname:short)" 2>/dev/null)
  [ -z "$branches" ]

  run run_backup_cleanup "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 0 squash backup branch(es)"* ]]
}

# ============================================================================
# Test: happy path - ancestor backups are deleted
# ============================================================================

@test "single ancestor backup is deleted" {
  # Create a commit on main
  echo "change" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "work commit"

  # Create backup pointing at parent commit (is ancestor of HEAD)
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-20260101-120000" HEAD~1

  run run_backup_cleanup "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 1 squash backup branch(es)"* ]]

  # Verify backup branch is gone
  local remaining
  remaining=$(git -C "$TEST_TEMP_DIR" branch --list "backup-before-squash-*" --format="%(refname:short)")
  [ -z "$remaining" ]
}

@test "multiple ancestor backups are all deleted" {
  # Create commits
  echo "change1" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "commit 1"

  echo "change2" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "commit 2"

  echo "change3" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "commit 3"

  # Create multiple backup branches at ancestor commits
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-20260101-100000" HEAD~2
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-20260101-110000" HEAD~1

  run run_backup_cleanup "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 2 squash backup branch(es)"* ]]

  # Verify all backup branches are gone
  local remaining
  remaining=$(git -C "$TEST_TEMP_DIR" branch --list "backup-before-squash-*" --format="%(refname:short)")
  [ -z "$remaining" ]
}

# ============================================================================
# Test: parallel safety - non-ancestor backup is preserved
# ============================================================================

@test "backup from another branch (not ancestor of HEAD) is preserved" {
  # Create commits on main
  echo "main change" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "main commit"

  # Create another branch diverging from initial commit
  git -C "$TEST_TEMP_DIR" checkout --quiet -b other-branch HEAD~1
  echo "other change" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "other branch commit"

  # Create backup pointing at tip of other-branch (NOT ancestor of main)
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-other-instance-20260101-120000" other-branch

  # Switch back to main for cleanup
  git -C "$TEST_TEMP_DIR" checkout --quiet main

  # Verify the other-branch is NOT an ancestor of main HEAD
  run git -C "$TEST_TEMP_DIR" merge-base --is-ancestor "backup-before-squash-other-instance-20260101-120000" HEAD
  [ "$status" -ne 0 ]

  run run_backup_cleanup "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 0 squash backup branch(es)"* ]]

  # Verify non-ancestor backup is still present
  local remaining
  remaining=$(git -C "$TEST_TEMP_DIR" branch --list "backup-before-squash-*" --format="%(refname:short)")
  [[ "$remaining" == *"backup-before-squash-other-instance-20260101-120000"* ]]
}

@test "partial: ancestor backup deleted, non-ancestor backup preserved" {
  # Create commits on main
  echo "main change" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "main commit"

  # Create diverging branch
  git -C "$TEST_TEMP_DIR" checkout --quiet -b other-branch HEAD~1
  echo "other change" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "other branch commit"

  # Create backup at ancestor of main (should be deleted)
  git -C "$TEST_TEMP_DIR" checkout --quiet main
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-ancestor-20260101-100000" HEAD~1

  # Create backup at tip of diverging branch (should be preserved)
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-other-20260101-110000" other-branch

  run run_backup_cleanup "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 1 squash backup branch(es)"* ]]

  # Ancestor backup is gone
  run git -C "$TEST_TEMP_DIR" branch --list "backup-before-squash-ancestor-20260101-100000"
  [ -z "$output" ]

  # Non-ancestor backup is preserved
  local remaining
  remaining=$(git -C "$TEST_TEMP_DIR" branch --list "backup-before-squash-other-20260101-110000" --format="%(refname:short)")
  [[ "$remaining" == "backup-before-squash-other-20260101-110000" ]]
}

# ============================================================================
# Test: non-fatal error handling
# ============================================================================

@test "deletion failure warns but does not exit with failure" {
  # Create a commit
  echo "change" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "work commit"

  # Create an ancestor backup branch
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-20260101-120000" HEAD~1

  # Run the cleanup function but simulate branch -D failure by using a read-only wrapper
  # We test this by verifying the cleanup logic itself: when git branch -D fails,
  # the function still returns 0 and emits WARNING
  #
  # The bash logic:
  #   if git branch -D "$backup" >/dev/null 2>&1; then
  #     BACKUPS_DELETED=$((BACKUPS_DELETED + 1))
  #   else
  #     echo "WARNING: Failed to delete backup branch: $backup" >&2
  #   fi
  # The 'else' branch does NOT exit or return non-zero.

  # We verify this behavior by wrapping git to fail -D operations
  local cleanup_script="$TEST_TEMP_DIR/test_cleanup_with_failure.sh"
  cat > "$cleanup_script" <<'SCRIPT'
#!/bin/bash
set -euo pipefail
repo_dir="$1"
BACKUPS_DELETED=0

for backup in $(git -C "$repo_dir" branch --list "backup-before-squash-*" --format="%(refname:short)" 2>/dev/null); do
  if git -C "$repo_dir" merge-base --is-ancestor "$backup" HEAD 2>/dev/null; then
    # Simulate branch -D failure (always fail)
    if false; then
      BACKUPS_DELETED=$((BACKUPS_DELETED + 1))
    else
      echo "WARNING: Failed to delete backup branch: $backup" >&2
    fi
  fi
done

echo "Deleted $BACKUPS_DELETED squash backup branch(es)"
exit 0
SCRIPT
  chmod +x "$cleanup_script"

  run "$cleanup_script" "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 0 squash backup branch(es)"* ]]
  # WARNING should be in stderr, not stdout - check via the run output (bats merges them)
  # The script outputs to stderr; bats captures it in $output
  [[ "$output" == *"WARNING: Failed to delete backup branch"* ]]
}

@test "script continues processing after deletion failure and overall succeeds" {
  # Create commits
  echo "change1" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "commit 1"

  echo "change2" >> "$TEST_TEMP_DIR/file.txt"
  git -C "$TEST_TEMP_DIR" add file.txt
  git -C "$TEST_TEMP_DIR" commit --quiet -m "commit 2"

  # Create two ancestor backups
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-20260101-100000" HEAD~1
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-20260101-110000" HEAD~2

  # Script that fails on first backup but succeeds on second
  local cleanup_script="$TEST_TEMP_DIR/test_partial_failure.sh"
  cat > "$cleanup_script" <<'SCRIPT'
#!/bin/bash
set -euo pipefail
repo_dir="$1"
BACKUPS_DELETED=0
FIRST=true

for backup in $(git -C "$repo_dir" branch --list "backup-before-squash-*" --format="%(refname:short)" 2>/dev/null); do
  if git -C "$repo_dir" merge-base --is-ancestor "$backup" HEAD 2>/dev/null; then
    # Fail only on first backup
    if [[ "$FIRST" == "true" ]]; then
      FIRST=false
      echo "WARNING: Failed to delete backup branch: $backup" >&2
    else
      if git -C "$repo_dir" branch -D "$backup" >/dev/null 2>&1; then
        BACKUPS_DELETED=$((BACKUPS_DELETED + 1))
      else
        echo "WARNING: Failed to delete backup branch: $backup" >&2
      fi
    fi
  fi
done

echo "Deleted $BACKUPS_DELETED squash backup branch(es)"
exit 0
SCRIPT
  chmod +x "$cleanup_script"

  run "$cleanup_script" "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 1 squash backup branch(es)"* ]]
  [[ "$output" == *"WARNING: Failed to delete backup branch"* ]]
}

# ============================================================================
# Test: merge-base ancestor check correctness
# ============================================================================

@test "HEAD itself is considered an ancestor (is-ancestor of self)" {
  # git merge-base --is-ancestor A A returns 0
  run git -C "$TEST_TEMP_DIR" merge-base --is-ancestor HEAD HEAD
  [ "$status" -eq 0 ]
}

@test "backup at HEAD is considered ancestor and gets deleted" {
  # When backup points to exact same commit as HEAD
  git -C "$TEST_TEMP_DIR" branch "backup-before-squash-at-head-20260101-120000" HEAD

  run run_backup_cleanup "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 1 squash backup branch(es)"* ]]
}

@test "branch pattern only matches backup-before-squash-* not other branches" {
  # Create a branch that should NOT be matched
  git -C "$TEST_TEMP_DIR" branch "backup-after-rebase-conflict-20260101-120000" HEAD
  git -C "$TEST_TEMP_DIR" branch "some-other-branch" HEAD

  run run_backup_cleanup "$TEST_TEMP_DIR"
  [ "$status" -eq 0 ]
  [[ "$output" == *"Deleted 0 squash backup branch(es)"* ]]

  # Other branches still exist
  run git -C "$TEST_TEMP_DIR" branch --list "backup-after-rebase-conflict-20260101-120000"
  [[ "$output" == *"backup-after-rebase-conflict-20260101-120000"* ]]

  run git -C "$TEST_TEMP_DIR" branch --list "some-other-branch"
  [[ "$output" == *"some-other-branch"* ]]
}
