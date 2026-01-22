#!/bin/bash
# task-lock.sh - Task-level locking for concurrent CAT execution
#
# Provides atomic lock acquisition with persistent locks.
# Locks never expire automatically - user must explicitly release or force-release.
# Prevents multiple Claude instances from executing the same task simultaneously.
#
# Usage:
#   task-lock.sh acquire <task-id> <session-id>
#   task-lock.sh update <task-id> <session-id> <worktree>
#   task-lock.sh release <task-id> <session-id>
#   task-lock.sh force-release <task-id>
#   task-lock.sh check <task-id>
#   task-lock.sh list
#
# Lock file format (.claude/cat/locks/<task-id>.lock):
#   session_id=<uuid>
#   created_at=<timestamp>
#   worktree=<path>

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# ============================================================================
# CONFIGURATION
# ============================================================================

LOCK_DIR="${CLAUDE_PROJECT_DIR:-.}/.claude/cat/locks"

# ============================================================================
# FUNCTIONS
# ============================================================================

ensure_lock_dir() {
  mkdir -p "$LOCK_DIR"
}

get_lock_file() {
  local task_id="$1"
  # Sanitize task_id for filename (replace / with -)
  local safe_id="${task_id//\//-}"
  echo "${LOCK_DIR}/${safe_id}.lock"
}

current_timestamp() {
  date +%s
}

iso_timestamp() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

# Acquire lock atomically
# Returns: 0 if acquired, 1 if already locked by another session
acquire_lock() {
  local task_id="$1"
  local session_id="$2"
  local worktree="${3:-}"

  ensure_lock_dir
  local lock_file
  lock_file=$(get_lock_file "$task_id")

  # Check if lock exists
  if [[ -f "$lock_file" ]]; then
    local existing_session
    existing_session=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")

    # If same session, return success (idempotent)
    if [[ "$existing_session" == "$session_id" ]]; then
      echo '{"status":"acquired","message":"Lock already held by this session"}'
      return 0
    fi

    # Lock exists and belongs to different session - never auto-expire
    # CRITICAL: Output includes explicit guidance to prevent M084 pattern (investigating locks)

    # Fetch remote branch metadata for additional context
    local remote_author="unknown"
    local remote_email=""
    local remote_date="unknown"
    local remote_branch=""

    # Try to find remote branch matching the task_id pattern
    for branch_pattern in "origin/${task_id}" "origin/*-${task_id#*-}"; do
      remote_branch=$(git branch -r 2>/dev/null | grep -m1 "$branch_pattern" | tr -d ' ') || true
      [[ -n "$remote_branch" ]] && break
    done

    if [[ -n "$remote_branch" ]]; then
      remote_author=$(git log -1 --format='%an' "$remote_branch" 2>/dev/null) || remote_author="unknown"
      remote_email=$(git log -1 --format='%ae' "$remote_branch" 2>/dev/null) || remote_email=""
      remote_date=$(git log -1 --format='%cr' "$remote_branch" 2>/dev/null) || remote_date="unknown"
    fi

    cat << LOCKED_JSON
{"status":"locked","message":"Task locked by another session","owner":"$existing_session","action":"FIND_ANOTHER_TASK","guidance":"Do NOT investigate, remove, or question this lock. Execute a different task instead. If you believe this is a stale lock from a crashed session, ask the USER to run /cat:cleanup.","remote_author":"$remote_author","remote_email":"$remote_email","remote_date":"$remote_date"}
LOCKED_JSON
    return 1
  fi

  # Atomic lock creation using temp file + rename
  local temp_lock="${lock_file}.$$"
  local now
  now=$(current_timestamp)

  cat > "$temp_lock" << EOF
session_id=${session_id}
created_at=${now}
worktree=${worktree}
created_iso=$(iso_timestamp)
EOF

  # Atomic move (rename is atomic on same filesystem)
  if mv -n "$temp_lock" "$lock_file" 2>/dev/null; then
    echo '{"status":"acquired","message":"Lock acquired successfully"}'
    return 0
  else
    # Another process beat us - clean up temp file
    rm -f "$temp_lock"
    echo '{"status":"locked","message":"Lock acquired by another process during race"}'
    return 1
  fi
}

# Update lock metadata (only if owned by this session)
update_lock() {
  local task_id="$1"
  local session_id="$2"
  local worktree="$3"

  local lock_file
  lock_file=$(get_lock_file "$task_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"status":"error","message":"No lock exists to update"}'
    return 1
  fi

  local existing_session
  existing_session=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")

  if [[ "$existing_session" != "$session_id" ]]; then
    echo "{\"status\":\"error\",\"message\":\"Lock owned by different session: $existing_session\"}"
    return 1
  fi

  # Read existing values
  local created_at created_iso
  created_at=$(grep "^created_at=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")
  created_iso=$(grep "^created_iso=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")

  # Write updated lock file atomically
  local temp_lock="${lock_file}.$$"
  cat > "$temp_lock" << EOF
session_id=${session_id}
created_at=${created_at}
worktree=${worktree}
created_iso=${created_iso}
EOF

  mv -f "$temp_lock" "$lock_file"
  echo "{\"status\":\"updated\",\"message\":\"Lock updated with worktree\",\"worktree\":\"$worktree\"}"
  return 0
}

# Release lock (only if owned by this session)
release_lock() {
  local task_id="$1"
  local session_id="$2"

  local lock_file
  lock_file=$(get_lock_file "$task_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"status":"released","message":"No lock exists"}'
    return 0
  fi

  local existing_session
  existing_session=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")

  if [[ "$existing_session" != "$session_id" ]]; then
    echo "{\"status\":\"error\",\"message\":\"Lock owned by different session: $existing_session\"}"
    return 1
  fi

  rm -f "$lock_file"
  echo '{"status":"released","message":"Lock released successfully"}'
  return 0
}

# Check lock status
check_lock() {
  local task_id="$1"

  local lock_file
  lock_file=$(get_lock_file "$task_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"locked":false,"message":"Task not locked"}'
    return 0
  fi

  local session_id created_at worktree
  session_id=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "unknown")
  created_at=$(grep "^created_at=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")
  worktree=$(grep "^worktree=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")

  local now age
  now=$(current_timestamp)
  age=$((now - created_at))

  echo "{\"locked\":true,\"session_id\":\"$session_id\",\"age_seconds\":$age,\"worktree\":\"$worktree\"}"
  return 0
}

# Force release lock (user action - ignores session ownership)
force_release_lock() {
  local task_id="$1"

  local lock_file
  lock_file=$(get_lock_file "$task_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"status":"released","message":"No lock exists"}'
    return 0
  fi

  local existing_session
  existing_session=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "unknown")

  rm -f "$lock_file"
  echo "{\"status\":\"released\",\"message\":\"Lock forcibly released (was owned by $existing_session)\"}"
  return 0
}

# List all locks (optimized: single jq call instead of O(n))
list_locks() {
  ensure_lock_dir

  local now
  now=$(current_timestamp)

  # Collect all lock data in a simple format first (no jq in loop)
  local lock_data=""
  for lock_file in "$LOCK_DIR"/*.lock; do
    [[ ! -f "$lock_file" ]] && continue

    local task_id session_id created_at age
    task_id=$(basename "$lock_file" .lock)
    session_id=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "unknown")
    created_at=$(grep "^created_at=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")
    age=$((now - created_at))

    # Accumulate as tab-separated values
    lock_data+="${task_id}\t${session_id}\t${age}\n"
  done

  # Single jq call to construct entire JSON array
  if [[ -z "$lock_data" ]]; then
    echo "[]"
  else
    printf "%b" "$lock_data" | jq -R -s '
      split("\n") | map(select(length > 0)) | map(
        split("\t") | {task: .[0], session: .[1], age_seconds: (.[2] | tonumber)}
      )
    '
  fi

  return 0
}

# ============================================================================
# MAIN
# ============================================================================

usage() {
  cat << 'EOF'
Usage: task-lock.sh <command> [args]

Commands:
  acquire <task-id> <session-id> [worktree]  - Acquire lock for task
  update <task-id> <session-id> <worktree>   - Update lock with worktree path (must own lock)
  release <task-id> <session-id>             - Release lock (only if owned by session)
  force-release <task-id>                    - Force release lock (user action, any owner)
  check <task-id>                            - Check if task is locked
  list                                       - List all locks

Locks are persistent and never expire automatically.
Use 'force-release' to remove stale locks from crashed sessions.

Environment:
  CLAUDE_PROJECT_DIR  - Project root (default: current directory)

Lock files stored in: $LOCK_DIR/
EOF
}

case "${1:-}" in
  acquire)
    [[ $# -lt 3 ]] && { echo '{"status":"error","message":"Usage: acquire <task-id> <session-id> [worktree]"}'; exit 1; }
    acquire_lock "$2" "$3" "${4:-}"
    ;;
  update)
    [[ $# -lt 4 ]] && { echo '{"status":"error","message":"Usage: update <task-id> <session-id> <worktree>"}'; exit 1; }
    update_lock "$2" "$3" "$4"
    ;;
  release)
    [[ $# -lt 3 ]] && { echo '{"status":"error","message":"Usage: release <task-id> <session-id>"}'; exit 1; }
    release_lock "$2" "$3"
    ;;
  force-release)
    [[ $# -lt 2 ]] && { echo '{"status":"error","message":"Usage: force-release <task-id>"}'; exit 1; }
    force_release_lock "$2"
    ;;
  check)
    [[ $# -lt 2 ]] && { echo '{"status":"error","message":"Usage: check <task-id>"}'; exit 1; }
    check_lock "$2"
    ;;
  list)
    list_locks
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo '{"status":"error","message":"Unknown command. Use --help for usage."}'
    exit 1
    ;;
esac
