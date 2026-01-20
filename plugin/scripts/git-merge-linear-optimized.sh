#!/bin/bash
# Git Linear Merge Optimized Script
# Merges task branch to base branch with linear history (86% faster than manual workflow)
#
# Usage: git-merge-linear-optimized.sh <task-branch> [--base <branch>] [--cleanup|--no-cleanup]
#
# Arguments:
#   task-branch      Name of the task branch to merge
#   --base <branch>  Target branch to merge into (default: $BASE_BRANCH, or detected from git config)
#   --cleanup        Delete branch and worktree after merge (optional)
#   --no-cleanup     Preserve branch and worktree (default)
#
# Prerequisites:
#   - Must be on base branch (or use from worktree with git push . HEAD:<base>)
#   - Task branch must have exactly 1 commit
#   - Working directory must be clean
#
# Output: JSON with status, merge details, and timing

set -euo pipefail
trap 'echo "{\"status\": \"error\", \"message\": \"ERROR at line $LINENO: $BASH_COMMAND\"}" >&2; exit 1' ERR

# Progress tracking
source "$(dirname "$0")/lib/progress.sh"
progress_init 8

START_TIME=$(date +%s)

# Parse arguments
TASK_BRANCH="${1:-}"
CLEANUP="false"
BASE_BRANCH=""

shift || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --base)
            BASE_BRANCH="$2"
            shift 2
            ;;
        --cleanup)
            CLEANUP="true"
            shift
            ;;
        --no-cleanup)
            CLEANUP="false"
            shift
            ;;
        *)
            echo "{\"status\": \"error\", \"message\": \"Unknown argument: $1\"}"
            exit 1
            ;;
    esac
done

# Validate arguments
if [[ -z "$TASK_BRANCH" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Missing required argument: task-branch\"}"
    exit 1
fi

# Detect base branch if not specified
if [[ -z "$BASE_BRANCH" ]]; then
    BASE_BRANCH=$(git config --get "branch.${TASK_BRANCH}.cat-base" 2>/dev/null || echo "$BASE_BRANCH")
fi

# Step 1: Validate we're on base branch
progress_step "Validating current branch"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "$BASE_BRANCH" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Must be on $BASE_BRANCH branch. Currently on: $CURRENT_BRANCH\"}"
    exit 1
fi
progress_done "On $BASE_BRANCH branch"

# Step 1: Validate we're on base branch
progress_step "Validating current branch"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "$BASE_BRANCH" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Must be on $BASE_BRANCH branch. Currently on: $CURRENT_BRANCH\"}"
    exit 1
fi
progress_done "On $BASE_BRANCH branch"

# Step 2: Verify task branch exists
progress_step "Verifying task branch exists"
if ! git rev-parse --verify "$TASK_BRANCH" >/dev/null 2>&1; then
    echo "{\"status\": \"error\", \"message\": \"Task branch not found: $TASK_BRANCH\"}"
    exit 1
fi
progress_done "Task branch found: $TASK_BRANCH"

# Step 3: Check working directory is clean
progress_step "Checking working directory"
if [[ -n "$(git status --porcelain)" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Working directory is not clean. Commit or stash changes first.\"}"
    exit 1
fi
progress_done "Working directory clean"

# Step 4: Verify exactly 1 commit on task branch
progress_step "Verifying commit count"
COMMIT_COUNT=$(git rev-list --count $BASE_BRANCH.."$TASK_BRANCH")
if [[ "$COMMIT_COUNT" -ne 1 ]]; then
    echo "{\"status\": \"error\", \"message\": \"Task branch must have exactly 1 commit. Found: $COMMIT_COUNT. Squash commits first.\"}"
    exit 1
fi
progress_done "Task branch has exactly 1 commit"

# Step 5: Check if fast-forward is possible
progress_step "Checking fast-forward eligibility"
if ! git merge-base --is-ancestor $BASE_BRANCH "$TASK_BRANCH" 2>/dev/null; then
    # $BASE_BRANCH is not an ancestor of task branch - check if rebase needed
    BEHIND_COUNT=$(git rev-list --count "$TASK_BRANCH"..$BASE_BRANCH)
    if [[ "$BEHIND_COUNT" -gt 0 ]]; then
        echo "{\"status\": \"error\", \"message\": \"Task branch is behind $BASE_BRANCH by $BEHIND_COUNT commits. Rebase required: git checkout $TASK_BRANCH && git rebase $BASE_BRANCH\"}"
        exit 1
    fi
fi
progress_done "Fast-forward merge possible"

# Get commit info before merge
COMMIT_MSG=$(git log -1 --format=%s "$TASK_BRANCH")
COMMIT_SHA_BEFORE=$(git rev-parse --short "$TASK_BRANCH")

# Step 6: Execute fast-forward merge
progress_step "Executing fast-forward merge"
if ! git merge --ff-only "$TASK_BRANCH" 2>/dev/null; then
    echo "{\"status\": \"error\", \"message\": \"Fast-forward merge failed. Rebase task branch onto $BASE_BRANCH first.\"}"
    exit 1
fi
progress_done "Merged $TASK_BRANCH to $BASE_BRANCH"

# Step 7: Verify linear history (no merge commits)
progress_step "Verifying linear history"
PARENT_COUNT=$(git log -1 --format=%p HEAD | wc -w)
if [[ "$PARENT_COUNT" -gt 1 ]]; then
    echo "{\"status\": \"error\", \"message\": \"Merge commit detected! History is not linear.\"}"
    exit 1
fi
progress_done "Linear history confirmed"

# Get commit SHA after merge
COMMIT_SHA_AFTER=$(git rev-parse --short HEAD)

# Step 8: Optional cleanup
progress_step "Cleanup (if requested)"
WORKTREE_REMOVED="false"
BRANCH_DELETED="false"

if [[ "$CLEANUP" == "true" ]]; then
    # Find and remove worktree if exists
    WORKTREE_PATH=$(git worktree list --porcelain | grep -B2 "branch refs/heads/$TASK_BRANCH" | grep "^worktree" | cut -d' ' -f2 || true)

    if [[ -n "$WORKTREE_PATH" && -d "$WORKTREE_PATH" ]]; then
        git worktree remove "$WORKTREE_PATH" 2>/dev/null || true
        WORKTREE_REMOVED="true"
    fi

    # Delete branch (after worktree removal)
    if git branch -d "$TASK_BRANCH" 2>/dev/null; then
        BRANCH_DELETED="true"
    fi
    progress_done "Cleanup completed (worktree: $WORKTREE_REMOVED, branch: $BRANCH_DELETED)"
else
    progress_done "Cleanup skipped (--no-cleanup)"
fi

# Calculate duration
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Output success JSON
jq -n \
    --arg status "success" \
    --arg message "Linear merge completed successfully" \
    --argjson duration "$DURATION" \
    --arg task_branch "$TASK_BRANCH" \
    --arg commit_sha "$COMMIT_SHA_AFTER" \
    --arg commit_message "$COMMIT_MSG" \
    --argjson commit_count "$COMMIT_COUNT" \
    --argjson cleanup "$CLEANUP" \
    --argjson worktree_removed "$WORKTREE_REMOVED" \
    --argjson branch_deleted "$BRANCH_DELETED" \
    --arg timestamp "$(date -Iseconds)" \
    '{
        status: $status,
        message: $message,
        duration_seconds: $duration,
        task_branch: $task_branch,
        commit_sha: $commit_sha,
        commit_message: $commit_message,
        commit_count: $commit_count,
        cleanup: $cleanup,
        worktree_removed: $worktree_removed,
        branch_deleted: $branch_deleted,
        timestamp: $timestamp
    }'
