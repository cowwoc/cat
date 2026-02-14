#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Write and Commit Script
# Creates a file and commits it atomically (60-75% faster than step-by-step)
#
# Usage: write-and-commit.sh <file-path> <content-file> <commit-msg-file> [--executable]
#
# Arguments:
#   file-path       Path to create the file at (relative to repo root)
#   content-file    Path to temp file containing the file content
#   commit-msg-file Path to temp file containing the commit message
#   --executable    Optional flag to make the file executable
#
# Output: JSON with status, commit SHA, and timing

set -euo pipefail
trap 'echo "{\"status\": \"error\", \"message\": \"ERROR at line $LINENO: $BASH_COMMAND\"}" >&2; exit 1' ERR

# Progress tracking
source "$(dirname "$0")/lib/progress.sh"
progress_init 6

START_TIME=$(date +%s)

# Parse arguments
FILE_PATH="${1:-}"
CONTENT_FILE="${2:-}"
COMMIT_MSG_FILE="${3:-}"
EXECUTABLE="false"

if [[ "${4:-}" == "--executable" ]]; then
    EXECUTABLE="true"
fi

# Step 1: Validate arguments
progress_step "Validating arguments"
if [[ -z "$FILE_PATH" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Missing required argument: file-path\"}"
    exit 1
fi

if [[ -z "$CONTENT_FILE" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Missing required argument: content-file\"}"
    exit 1
fi

if [[ -z "$COMMIT_MSG_FILE" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Missing required argument: commit-msg-file\"}"
    exit 1
fi

if [[ ! -f "$CONTENT_FILE" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Content file not found: $CONTENT_FILE\"}"
    exit 1
fi

if [[ ! -f "$COMMIT_MSG_FILE" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Commit message file not found: $COMMIT_MSG_FILE\"}"
    exit 1
fi
progress_done "All arguments valid"

# Step 2: Verify git repository
progress_step "Verifying git repository"
if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "{\"status\": \"error\", \"message\": \"Not in a git repository\"}"
    exit 1
fi
WORKING_DIR=$(pwd)
progress_done "In git repository: $WORKING_DIR"

# Step 3: Create parent directories
progress_step "Creating parent directories"
PARENT_DIR=$(dirname "$FILE_PATH")
if [[ "$PARENT_DIR" != "." ]]; then
    mkdir -p "$PARENT_DIR"
    progress_done "Created: $PARENT_DIR"
else
    progress_done "No parent directory needed"
fi

# Step 4: Write file content
progress_step "Writing file content"
FILE_EXISTS="false"
if [[ -f "$FILE_PATH" ]]; then
    FILE_EXISTS="true"
fi
cp "$CONTENT_FILE" "$FILE_PATH"
if [[ "$EXECUTABLE" == "true" ]]; then
    chmod +x "$FILE_PATH"
    progress_done "Wrote $FILE_PATH (executable)"
else
    progress_done "Wrote $FILE_PATH"
fi

# Step 5: Stage and commit
progress_step "Staging and committing"
git add "$FILE_PATH"
COMMIT_MSG=$(cat "$COMMIT_MSG_FILE")
git commit -m "$COMMIT_MSG"
COMMIT_SHA=$(git rev-parse --short HEAD)
progress_done "Committed: $COMMIT_SHA"

# Step 6: Cleanup
progress_step "Cleaning up temp files"
rm -f "$CONTENT_FILE" "$COMMIT_MSG_FILE"
progress_done "Temp files removed"

# Calculate duration
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Output success JSON
jq -n \
    --arg status "success" \
    --arg message "File created and committed successfully" \
    --argjson duration "$DURATION" \
    --arg file_path "$FILE_PATH" \
    --argjson executable "$EXECUTABLE" \
    --argjson file_existed "$FILE_EXISTS" \
    --arg commit_sha "$COMMIT_SHA" \
    --arg working_directory "$WORKING_DIR" \
    --arg timestamp "$(date -Iseconds)" \
    '{
        status: $status,
        message: $message,
        duration_seconds: $duration,
        file_path: $file_path,
        executable: $executable,
        file_existed: $file_existed,
        commit_sha: $commit_sha,
        working_directory: $working_directory,
        timestamp: $timestamp
    }'
