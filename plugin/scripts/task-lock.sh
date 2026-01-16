#!/bin/bash
# task-lock.sh - Task-level locking for concurrent CAT execution
#
# Provides atomic lock acquisition with heartbeat-based lease management.
# Prevents multiple Claude instances from executing the same task simultaneously.
#
# Usage:
#   task-lock.sh acquire <task-id> <session-id>
#   task-lock.sh release <task-id> <session-id>
#   task-lock.sh check <task-id>
#   task-lock.sh heartbeat <task-id> <session-id>
#   task-lock.sh cleanup [--stale-minutes N]
#
# Lock file format (.claude/cat/locks/<task-id>.lock):
#   session_id=<uuid>
#   created_at=<timestamp>
#   heartbeat=<timestamp>
#   worktree=<path>

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# ============================================================================
# CONFIGURATION
# ============================================================================

LOCK_DIR="${CLAUDE_PROJECT_DIR:-.}/.claude/cat/locks"
STALE_MINUTES=5  # Lock considered stale if no heartbeat for this long
HEARTBEAT_INTERVAL=120  # Seconds between heartbeats (2 minutes)

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

  # Check if lock exists and is still valid
  if [[ -f "$lock_file" ]]; then
    local existing_session existing_heartbeat
    existing_session=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")
    existing_heartbeat=$(grep "^heartbeat=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")

    # If same session, refresh and return success
    if [[ "$existing_session" == "$session_id" ]]; then
      update_heartbeat "$lock_file"
      echo '{"status":"acquired","message":"Lock refreshed (same session)"}'
      return 0
    fi

    # Check if existing lock is stale
    local now stale_threshold
    now=$(current_timestamp)
    stale_threshold=$((STALE_MINUTES * 60))

    if [[ $((now - existing_heartbeat)) -lt $stale_threshold ]]; then
      # Lock is still valid - another instance owns it
      echo "{\"status\":\"locked\",\"message\":\"Task locked by session $existing_session\",\"owner\":\"$existing_session\"}"
      return 1
    fi

    # Lock is stale - remove and acquire
    rm -f "$lock_file"
  fi

  # Atomic lock creation using mkdir (atomic on POSIX)
  local temp_lock="${lock_file}.$$"
  local now
  now=$(current_timestamp)

  cat > "$temp_lock" << EOF
session_id=${session_id}
created_at=${now}
heartbeat=${now}
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

  local session_id heartbeat created_at worktree
  session_id=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "unknown")
  heartbeat=$(grep "^heartbeat=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")
  created_at=$(grep "^created_at=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")
  worktree=$(grep "^worktree=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")

  local now stale_threshold is_stale
  now=$(current_timestamp)
  stale_threshold=$((STALE_MINUTES * 60))

  if [[ $((now - heartbeat)) -ge $stale_threshold ]]; then
    is_stale="true"
  else
    is_stale="false"
  fi

  local age=$((now - created_at))
  local since_heartbeat=$((now - heartbeat))

  echo "{\"locked\":true,\"session_id\":\"$session_id\",\"age_seconds\":$age,\"heartbeat_age_seconds\":$since_heartbeat,\"stale\":$is_stale,\"worktree\":\"$worktree\"}"
  return 0
}

# Update heartbeat timestamp
update_heartbeat() {
  local lock_file="$1"
  local now
  now=$(current_timestamp)

  if [[ -f "$lock_file" ]]; then
    # Update heartbeat line in place
    sed -i "s/^heartbeat=.*/heartbeat=${now}/" "$lock_file"
  fi
}

# Heartbeat command - refresh lock timestamp
heartbeat() {
  local task_id="$1"
  local session_id="$2"

  local lock_file
  lock_file=$(get_lock_file "$task_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"status":"error","message":"No lock to refresh"}'
    return 1
  fi

  local existing_session
  existing_session=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "")

  if [[ "$existing_session" != "$session_id" ]]; then
    echo "{\"status\":\"error\",\"message\":\"Lock owned by different session: $existing_session\"}"
    return 1
  fi

  update_heartbeat "$lock_file"
  echo '{"status":"refreshed","message":"Heartbeat updated"}'
  return 0
}

# Cleanup stale locks
cleanup() {
  local stale_minutes="${1:-$STALE_MINUTES}"

  ensure_lock_dir

  local now cleaned_count
  now=$(current_timestamp)
  cleaned_count=0
  stale_threshold=$((stale_minutes * 60))

  local cleaned_locks=()

  for lock_file in "$LOCK_DIR"/*.lock; do
    [[ ! -f "$lock_file" ]] && continue

    local heartbeat
    heartbeat=$(grep "^heartbeat=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")

    if [[ $((now - heartbeat)) -ge $stale_threshold ]]; then
      local task_id
      task_id=$(basename "$lock_file" .lock)
      cleaned_locks+=("$task_id")
      rm -f "$lock_file"
      cleaned_count=$((cleaned_count + 1))
    fi
  done

  if [[ $cleaned_count -gt 0 ]]; then
    local locks_json
    locks_json=$(printf '%s\n' "${cleaned_locks[@]}" | jq -R . | jq -s .)
    echo "{\"status\":\"cleaned\",\"count\":$cleaned_count,\"locks\":$locks_json}"
  else
    echo '{"status":"clean","count":0,"message":"No stale locks found"}'
  fi
  return 0
}

# List all locks
list_locks() {
  ensure_lock_dir

  local locks_json="[]"
  local now
  now=$(current_timestamp)

  for lock_file in "$LOCK_DIR"/*.lock; do
    [[ ! -f "$lock_file" ]] && continue

    local task_id session_id heartbeat created_at
    task_id=$(basename "$lock_file" .lock)
    session_id=$(grep "^session_id=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "unknown")
    heartbeat=$(grep "^heartbeat=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")
    created_at=$(grep "^created_at=" "$lock_file" 2>/dev/null | cut -d= -f2 || echo "0")

    local stale_threshold is_stale
    stale_threshold=$((STALE_MINUTES * 60))
    if [[ $((now - heartbeat)) -ge $stale_threshold ]]; then
      is_stale="true"
    else
      is_stale="false"
    fi

    local entry
    entry=$(jq -n \
      --arg task "$task_id" \
      --arg session "$session_id" \
      --argjson age "$((now - created_at))" \
      --argjson heartbeat_age "$((now - heartbeat))" \
      --argjson stale "$is_stale" \
      '{task: $task, session: $session, age_seconds: $age, heartbeat_age_seconds: $heartbeat_age, stale: $stale}')

    locks_json=$(echo "$locks_json" | jq --argjson entry "$entry" '. += [$entry]')
  done

  echo "$locks_json"
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
  release <task-id> <session-id>             - Release lock for task
  check <task-id>                            - Check if task is locked
  heartbeat <task-id> <session-id>           - Refresh lock heartbeat
  cleanup [--stale-minutes N]                - Remove stale locks (default 5 min)
  list                                       - List all locks

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
  release)
    [[ $# -lt 3 ]] && { echo '{"status":"error","message":"Usage: release <task-id> <session-id>"}'; exit 1; }
    release_lock "$2" "$3"
    ;;
  check)
    [[ $# -lt 2 ]] && { echo '{"status":"error","message":"Usage: check <task-id>"}'; exit 1; }
    check_lock "$2"
    ;;
  heartbeat)
    [[ $# -lt 3 ]] && { echo '{"status":"error","message":"Usage: heartbeat <task-id> <session-id>"}'; exit 1; }
    heartbeat "$2" "$3"
    ;;
  cleanup)
    stale_mins="$STALE_MINUTES"
    if [[ "${2:-}" == "--stale-minutes" ]] && [[ -n "${3:-}" ]]; then
      stale_mins="$3"
    fi
    cleanup "$stale_mins"
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
