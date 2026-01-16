#!/bin/bash
# merge-and-cleanup.sh - Merge task branch and clean up worktree/branch/lock
#
# Handles the happy path of the merging phase for CAT's /cat:work command:
# 1. Fast-forward merge task branch to base branch (from worktree, no checkout required)
# 2. Remove the task worktree
# 3. Delete the task branch
# 4. Release the task lock
#
# Usage:
#   merge-and-cleanup.sh <task-id> <session-id> [--worktree <path>]
#
# Arguments:
#   task-id        Task identifier (e.g., "2.0-fix-parser")
#   session-id     Claude session UUID
#   --worktree     Optional worktree path (auto-detected if not provided)
#
# Exit Codes:
#   0: Success
#   1: Invalid arguments
#   2: Worktree not found
#   3: Base branch detection failed (cat-base missing)
#   4: Fast-forward merge failed (rebase needed)
#   5: Worktree removal failed (dirty or in use)
#   6: Branch deletion failed
#   7: Lock release failed

set -euo pipefail

# ============================================================================
# CONFIGURATION
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${CLAUDE_PROJECT_DIR:?CLAUDE_PROJECT_DIR must be set}"
PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must be set}"

# ============================================================================
# ERROR HANDLING
# ============================================================================

# Track if we've output JSON already (to prevent double output)
OUTPUT_SENT=false

# Output JSON error and exit
error_json() {
  local exit_code="$1"
  local message="$2"

  if [[ "$OUTPUT_SENT" == "false" ]]; then
    OUTPUT_SENT=true
    jq -n \
      --arg status "error" \
      --arg message "$message" \
      --argjson exit_code "$exit_code" \
      '{status: $status, message: $message, exit_code: $exit_code}'
  fi
  exit "$exit_code"
}

# Trap for unexpected errors
trap 'error_json 1 "Unexpected error at line $LINENO: $BASH_COMMAND"' ERR

# ============================================================================
# FUNCTIONS
# ============================================================================

usage() {
  cat << 'EOF'
Usage: merge-and-cleanup.sh <task-id> <session-id> [--worktree <path>]

Merge task branch to base branch and clean up resources.

Arguments:
  task-id        Task identifier (e.g., "2.0-fix-parser")
  session-id     Claude session UUID
  --worktree     Optional worktree path (auto-detected if not provided)

Operations (in order):
  1. Fast-forward merge task branch to base branch (using git push . HEAD:<base>)
  2. Remove task worktree
  3. Delete task branch
  4. Release task lock

Exit Codes:
  0: Success
  1: Invalid arguments
  2: Worktree not found
  3: Base branch detection failed (cat-base missing)
  4: Fast-forward merge failed (rebase needed)
  5: Worktree removal failed (dirty or in use)
  6: Branch deletion failed
  7: Lock release failed

Environment (required):
  CLAUDE_PROJECT_DIR   Project root
  CLAUDE_PLUGIN_ROOT   Plugin root
EOF
}

# Find worktree path for a given branch
find_worktree_for_branch() {
  local branch="$1"

  # Parse git worktree list --porcelain output
  # Format:
  # worktree /path/to/worktree
  # HEAD <sha>
  # branch refs/heads/<branch>
  # (blank line)

  local worktree_path=""
  local current_worktree=""

  while IFS= read -r line; do
    if [[ "$line" =~ ^worktree\ (.+)$ ]]; then
      current_worktree="${BASH_REMATCH[1]}"
    elif [[ "$line" =~ ^branch\ refs/heads/(.+)$ ]]; then
      if [[ "${BASH_REMATCH[1]}" == "$branch" ]]; then
        worktree_path="$current_worktree"
        break
      fi
    fi
  done < <(git -C "$PROJECT_DIR" worktree list --porcelain 2>/dev/null)

  echo "$worktree_path"
}

# Get base branch from worktree's cat-base file
get_base_branch() {
  local worktree_path="$1"
  local task_branch="$2"

  # Get the git directory for the main repo
  local main_git_dir
  main_git_dir=$(git -C "$PROJECT_DIR" rev-parse --git-dir 2>/dev/null)

  # The cat-base file location
  local cat_base_file="${main_git_dir}/worktrees/${task_branch}/cat-base"

  if [[ ! -f "$cat_base_file" ]]; then
    return 1
  fi

  cat "$cat_base_file"
}

# Check if worktree has uncommitted changes
is_worktree_dirty() {
  local worktree_path="$1"

  if [[ -n "$(git -C "$worktree_path" status --porcelain 2>/dev/null)" ]]; then
    return 0  # dirty
  fi
  return 1  # clean
}

# ============================================================================
# MAIN
# ============================================================================

# Parse arguments
TASK_ID=""
SESSION_ID=""
WORKTREE_PATH=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help|help)
      usage
      exit 0
      ;;
    --worktree)
      if [[ $# -lt 2 ]]; then
        error_json 1 "Missing value for --worktree"
      fi
      WORKTREE_PATH="$2"
      shift 2
      ;;
    -*)
      error_json 1 "Unknown option: $1"
      ;;
    *)
      if [[ -z "$TASK_ID" ]]; then
        TASK_ID="$1"
      elif [[ -z "$SESSION_ID" ]]; then
        SESSION_ID="$1"
      else
        error_json 1 "Unexpected argument: $1"
      fi
      shift
      ;;
  esac
done

# Validate required arguments
if [[ -z "$TASK_ID" ]]; then
  error_json 1 "Missing required argument: task-id"
fi

if [[ -z "$SESSION_ID" ]]; then
  error_json 1 "Missing required argument: session-id"
fi

# Start timing
START_TIME=$(date +%s)

# Read cat-config.json for settings
CAT_CONFIG="${PROJECT_DIR}/.claude/cat/cat-config.json"
AUTO_REMOVE_WORKTREES=true
if [[ -f "$CAT_CONFIG" ]]; then
  AUTO_REMOVE_WORKTREES=$(jq -r '.autoRemoveWorktrees // true' "$CAT_CONFIG")
fi

# Source progress library if available
if [[ -f "${SCRIPT_DIR}/lib/progress.sh" ]]; then
  source "${SCRIPT_DIR}/lib/progress.sh"
  progress_init 4
else
  # Stub functions if progress.sh not available
  progress_init() { :; }
  progress_step() { echo ">> $1"; }
  progress_done() { echo "   $1"; }
fi

# ============================================================================
# STEP 1: Detect worktree and base branch
# ============================================================================
progress_step "Detecting worktree and base branch"

# Task branch is typically same as task-id
TASK_BRANCH="$TASK_ID"

# Find worktree if not provided
if [[ -z "$WORKTREE_PATH" ]]; then
  WORKTREE_PATH=$(find_worktree_for_branch "$TASK_BRANCH")
fi

# FAIL-FAST: Worktree must exist
if [[ -z "$WORKTREE_PATH" ]] || [[ ! -d "$WORKTREE_PATH" ]]; then
  error_json 2 "Worktree not found for task branch: $TASK_BRANCH"
fi

# FAIL-FAST: Get base branch from cat-base file
BASE_BRANCH=$(get_base_branch "$WORKTREE_PATH" "$TASK_BRANCH") || error_json 3 "cat-base file missing for task branch: $TASK_BRANCH. Cannot determine base branch."

progress_done "Worktree: $WORKTREE_PATH, Base branch: $BASE_BRANCH"

# ============================================================================
# STEP 2: Fast-forward merge (from worktree, no checkout required)
# ============================================================================
progress_step "Fast-forward merging to $BASE_BRANCH"

# FAIL-FAST: Check if worktree is dirty
if is_worktree_dirty "$WORKTREE_PATH"; then
  error_json 5 "Worktree has uncommitted changes: $WORKTREE_PATH. Commit or stash changes first."
fi

# MANDATORY: Check for base branch divergence FIRST (M199)
# This prevents overwriting commits added to base after worktree creation
DIVERGED=$(git -C "$WORKTREE_PATH" rev-list --count "HEAD..${BASE_BRANCH}" 2>/dev/null || echo "0")
if [[ "$DIVERGED" -gt 0 ]]; then
  error_json 4 "Base branch has diverged: $BASE_BRANCH has $DIVERGED commit(s) not in HEAD. Rebase required before merge."
fi

# Verify fast-forward is possible (base must be ancestor of HEAD)
if ! git -C "$WORKTREE_PATH" merge-base --is-ancestor "$BASE_BRANCH" HEAD 2>/dev/null; then
  error_json 4 "Fast-forward merge not possible. Task branch has diverged from $BASE_BRANCH. Rebase required."
fi

# Perform the fast-forward merge using git push from within the worktree
# This updates the base branch to point to HEAD without checking it out
COMMIT_SHA=$(git -C "$WORKTREE_PATH" rev-parse --short HEAD)

if ! PUSH_ERROR=$(git -C "$WORKTREE_PATH" push . "HEAD:$BASE_BRANCH" 2>&1); then
  error_json 4 "Fast-forward merge failed: $PUSH_ERROR. Rebase may be required."
fi

progress_done "Merged commit $COMMIT_SHA to $BASE_BRANCH"

# ============================================================================
# STEP 3: Remove worktree (if autoRemoveWorktrees is true)
# ============================================================================
progress_step "Removing worktree"

# Must cd out of the worktree before removing it
cd "$PROJECT_DIR"

if [[ "$AUTO_REMOVE_WORKTREES" == "true" ]]; then
  if ! REMOVE_ERROR=$(git worktree remove "$WORKTREE_PATH" 2>&1); then
    error_json 5 "Failed to remove worktree: $REMOVE_ERROR"
  fi
  WORKTREE_REMOVED=true
  progress_done "Removed worktree: $WORKTREE_PATH"
else
  WORKTREE_REMOVED=false
  progress_done "Skipped worktree removal (autoRemoveWorktrees: false)"
fi

# ============================================================================
# STEP 4: Delete task branch (if autoRemoveWorktrees is true)
# ============================================================================
progress_step "Deleting task branch"

if [[ "$AUTO_REMOVE_WORKTREES" == "true" ]]; then
  if ! BRANCH_ERROR=$(git -C "$PROJECT_DIR" branch -d "$TASK_BRANCH" 2>&1); then
    error_json 6 "Failed to delete branch: $BRANCH_ERROR"
  fi
  BRANCH_DELETED=true
  progress_done "Deleted branch: $TASK_BRANCH"
else
  BRANCH_DELETED=false
  progress_done "Skipped branch deletion (autoRemoveWorktrees: false)"
fi

# ============================================================================
# STEP 5: Release task lock
# ============================================================================
progress_step "Releasing task lock"

LOCK_SCRIPT="${PLUGIN_ROOT}/scripts/task-lock.sh"

if [[ -x "$LOCK_SCRIPT" ]]; then
  LOCK_RESULT=$("$LOCK_SCRIPT" release "$TASK_ID" "$SESSION_ID" 2>&1) || {
    error_json 7 "Failed to release lock: $LOCK_RESULT"
  }
  LOCK_RELEASED=true
else
  # Lock script not found - warn but don't fail
  LOCK_RELEASED=false
fi

progress_done "Lock released"

# ============================================================================
# OUTPUT SUCCESS
# ============================================================================

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

OUTPUT_SENT=true
jq -n \
  --arg status "success" \
  --arg message "Merged and cleaned up task" \
  --arg task_id "$TASK_ID" \
  --arg base_branch "$BASE_BRANCH" \
  --arg commit_sha "$COMMIT_SHA" \
  --argjson worktree_removed "${WORKTREE_REMOVED:-false}" \
  --argjson branch_deleted "${BRANCH_DELETED:-false}" \
  --argjson lock_released "${LOCK_RELEASED:-false}" \
  --argjson duration_seconds "$DURATION" \
  '{
    status: $status,
    message: $message,
    task_id: $task_id,
    base_branch: $base_branch,
    commit_sha: $commit_sha,
    worktree_removed: $worktree_removed,
    branch_deleted: $branch_deleted,
    lock_released: $lock_released,
    duration_seconds: $duration_seconds
  }'
