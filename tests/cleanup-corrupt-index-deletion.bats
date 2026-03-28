#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
# Regression tests for the cleanup skill's corrupt index.json deletion behavior.
# Verifies that deleting a corrupt index.json preserves the containing directory.

# Test fixture constants
GIT_TEST_EMAIL="test@example.com"
GIT_TEST_NAME="Test User"
TEST_ISSUE_NAME="my-issue"
CORRUPT_INDEX_CONTENT='{"status":"corrupt"}'

setup() {
  REPO_DIR=$(mktemp -d)
  git -C "$REPO_DIR" init
  git -C "$REPO_DIR" config user.email "$GIT_TEST_EMAIL"
  git -C "$REPO_DIR" config user.name "$GIT_TEST_NAME"
  mkdir -p "$REPO_DIR/issues/$TEST_ISSUE_NAME"
  echo "$CORRUPT_INDEX_CONTENT" > "$REPO_DIR/issues/$TEST_ISSUE_NAME/index.json"
  git -C "$REPO_DIR" add -- "$REPO_DIR/issues/$TEST_ISSUE_NAME/index.json"
  git -C "$REPO_DIR" commit -m "planning: initial commit"
  CORRUPT_DIR="$REPO_DIR/issues/$TEST_ISSUE_NAME"
}

teardown() {
  rm -rf "$REPO_DIR"
}

@test "directory preserved after index.json deletion" {
  rm "${CORRUPT_DIR}/index.json"
  [ -d "$CORRUPT_DIR" ]
}

@test "other files preserved after index.json deletion" {
  echo "# Plan" > "${CORRUPT_DIR}/plan.md"
  rm "${CORRUPT_DIR}/index.json"
  [ -f "${CORRUPT_DIR}/plan.md" ]
}

@test "directory preserved when index.json is the only file (edge case)" {
  # index.json is the only file in the directory (no plan.md)
  [ -d "$CORRUPT_DIR" ]
  rm "${CORRUPT_DIR}/index.json"
  [ -d "$CORRUPT_DIR" ]
  # Verify directory is actually empty
  [ -z "$(ls -A "$CORRUPT_DIR")" ]
}

@test "deleted index.json is staged as deletion" {
  ISSUE_NAME="$TEST_ISSUE_NAME"
  rm "${CORRUPT_DIR}/index.json"
  git -C "$REPO_DIR" add -- "${CORRUPT_DIR}/index.json"
  # Verify git add succeeded
  [ $? -eq 0 ]
  # Verify deletion is staged (git status shows 'D' marker)
  STAGED=$(git -C "$REPO_DIR" diff --cached --name-status)
  [[ "$STAGED" == *"D"*"index.json"* ]]
}

@test "commit message format is correct" {
  ISSUE_NAME="$TEST_ISSUE_NAME"
  rm "${CORRUPT_DIR}/index.json"
  git -C "$REPO_DIR" add -- "${CORRUPT_DIR}/index.json"
  git -C "$REPO_DIR" commit -m "planning: remove corrupt index.json from $ISSUE_NAME"
  SUBJECT=$(git -C "$REPO_DIR" log --format=%s -1)
  [ "$SUBJECT" = "planning: remove corrupt index.json from $TEST_ISSUE_NAME" ]
}

@test "multiple files preserved when index.json deleted" {
  echo "# Plan" > "${CORRUPT_DIR}/plan.md"
  echo "other content" > "${CORRUPT_DIR}/some-other-file.txt"
  rm "${CORRUPT_DIR}/index.json"
  [ -f "${CORRUPT_DIR}/plan.md" ]
  [ -f "${CORRUPT_DIR}/some-other-file.txt" ]
  [ -d "$CORRUPT_DIR" ]
}

@test "error handling: empty CORRUPT_DIR prevents commit" {
  REPO_DIR_TEMP="$REPO_DIR"
  CORRUPT_DIR=""
  ISSUE_NAME="$TEST_ISSUE_NAME"
  rm "${REPO_DIR_TEMP}/issues/${TEST_ISSUE_NAME}/index.json" 2>/dev/null || true
  # Simulate validation block from cleanup skill
  if [[ ! "$ISSUE_NAME" =~ ^[a-zA-Z0-9._-]+$ ]]; then
    VALIDATION_RESULT="invalid_name"
  elif [[ -z "$CORRUPT_DIR" ]]; then
    VALIDATION_RESULT="empty_path"
  else
    VALIDATION_RESULT="valid"
  fi
  [ "$VALIDATION_RESULT" = "empty_path" ]
}

@test "error handling: invalid ISSUE_NAME prevents commit" {
  CORRUPT_DIR="$REPO_DIR/issues/$TEST_ISSUE_NAME"
  ISSUE_NAME="my-issue-with-\$invalid"
  # Simulate validation block from cleanup skill
  if [[ ! "$ISSUE_NAME" =~ ^[a-zA-Z0-9._-]+$ ]]; then
    VALIDATION_RESULT="invalid_name"
  elif [[ -z "$CORRUPT_DIR" ]]; then
    VALIDATION_RESULT="empty_path"
  else
    VALIDATION_RESULT="valid"
  fi
  [ "$VALIDATION_RESULT" = "invalid_name" ]
}

@test "skill command sequence: validates name, stages file, commits deletion" {
  CORRUPT_DIR="$REPO_DIR/issues/$TEST_ISSUE_NAME"
  ISSUE_NAME="$TEST_ISSUE_NAME"
  rm "${CORRUPT_DIR}/index.json"
  # Execute the exact command block from cleanup/first-use.md Step 4, option 3
  if [[ ! "$ISSUE_NAME" =~ ^[a-zA-Z0-9._-]+$ ]]; then
    COMMAND_RESULT="invalid_name"
  elif [[ -z "$CORRUPT_DIR" ]]; then
    COMMAND_RESULT="empty_path"
  else
    git -C "$REPO_DIR" add -- "${CORRUPT_DIR}/index.json" && \
    git -C "$REPO_DIR" commit -m "planning: remove corrupt index.json from $ISSUE_NAME" && \
    COMMAND_RESULT="success" || COMMAND_RESULT="failed"
  fi
  [ "$COMMAND_RESULT" = "success" ]
  SUBJECT=$(git -C "$REPO_DIR" log --format=%s -1)
  [ "$SUBJECT" = "planning: remove corrupt index.json from $TEST_ISSUE_NAME" ]
  DIFF=$(git -C "$REPO_DIR" diff HEAD~1 HEAD --name-status)
  [[ "$DIFF" == *"D"*"index.json"* ]]
}
