#!/bin/bash
# issue-lock.sh - Issue-level locking for concurrent CAT execution
#
# Provides atomic lock acquisition with persistent locks.
# Locks never expire automatically - user must explicitly release or force-release.
# Prevents multiple Claude instances from executing the same issue simultaneously.
#
# Usage:
#   issue-lock.sh acquire <project-dir> <issue-id> <session-id> [worktree]
#   issue-lock.sh update <project-dir> <issue-id> <session-id> <worktree>
#   issue-lock.sh release <project-dir> <issue-id> <session-id>
#   issue-lock.sh force-release <project-dir> <issue-id>
#   issue-lock.sh check <project-dir> <issue-id>
#   issue-lock.sh list <project-dir>
#
# Lock file format (.claude/cat/locks/<issue-id>.lock):
#   JSON format with keys: session_id, created_at, worktree, created_iso

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# ============================================================================
# CONFIGURATION
# ============================================================================

PROJECT_DIR=""  # Required: set via --project-dir

# ============================================================================
# FUNCTIONS
# ============================================================================

ensure_lock_dir() {
  mkdir -p "$LOCK_DIR"
}

get_lock_file() {
  local issue_id="$1"
  # Sanitize issue_id for filename (replace / with -)
  local safe_id="${issue_id//\//-}"
  echo "${LOCK_DIR}/${safe_id}.lock"
}

current_timestamp() {
  date +%s
}

iso_timestamp() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

# Validate session_id looks like a UUID (prevents arg order mistakes - M204)
validate_session_id() {
  local session_id="$1"
  # Session IDs should be UUIDs: 8-4-4-4-12 hex pattern
  if [[ ! "$session_id" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
    echo "{\"status\":\"error\",\"message\":\"Invalid session_id format: '$session_id'. Expected UUID. Did you swap issue_id and session_id arguments?\"}"
    return 1
  fi
  return 0
}

# Acquire lock atomically
# Returns: 0 if acquired, 1 if already locked by another session
acquire_lock() {
  local issue_id="$1"
  local session_id="$2"
  local worktree="${3:-}"

  # Validate session_id format to catch argument order mistakes (M204)
  if ! validate_session_id "$session_id"; then
    return 1
  fi

  ensure_lock_dir
  local lock_file
  lock_file=$(get_lock_file "$issue_id")

  # Check if lock exists
  if [[ -f "$lock_file" ]]; then
    local existing_session
    existing_session=$(jq -r '.session_id' "$lock_file" 2>/dev/null || echo "")

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

    # Try to find remote branch matching the issue_id pattern
    for branch_pattern in "origin/${issue_id}" "origin/*-${issue_id#*-}"; do
      remote_branch=$(git branch -r 2>/dev/null | grep -m1 "$branch_pattern" | tr -d ' ') || true
      [[ -n "$remote_branch" ]] && break
    done

    if [[ -n "$remote_branch" ]]; then
      remote_author=$(git log -1 --format='%an' "$remote_branch" 2>/dev/null) || remote_author="unknown"
      remote_email=$(git log -1 --format='%ae' "$remote_branch" 2>/dev/null) || remote_email=""
      remote_date=$(git log -1 --format='%cr' "$remote_branch" 2>/dev/null) || remote_date="unknown"
    fi

    cat << LOCKED_JSON
{"status":"locked","message":"Issue locked by another session","owner":"$existing_session","action":"FIND_ANOTHER_ISSUE","guidance":"Do NOT investigate, remove, or question this lock. Execute a different issue instead. If you believe this is a stale lock from a crashed session, ask the USER to run /cat:cleanup.","remote_author":"$remote_author","remote_email":"$remote_email","remote_date":"$remote_date"}
LOCKED_JSON
    return 1
  fi

  # Atomic lock creation using temp file + rename
  local temp_lock="${lock_file}.$$"
  local now
  now=$(current_timestamp)
  local created_iso
  created_iso=$(iso_timestamp)

  # Write JSON format
  cat > "$temp_lock" << EOF
{
  "session_id": "${session_id}",
  "created_at": ${now},
  "worktree": "${worktree}",
  "created_iso": "${created_iso}"
}
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
  local issue_id="$1"
  local session_id="$2"
  local worktree="$3"

  # Validate session_id format (M204)
  if ! validate_session_id "$session_id"; then
    return 1
  fi

  local lock_file
  lock_file=$(get_lock_file "$issue_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"status":"error","message":"No lock exists to update"}'
    return 1
  fi

  local existing_session
  existing_session=$(jq -r '.session_id' "$lock_file" 2>/dev/null || echo "")

  if [[ "$existing_session" != "$session_id" ]]; then
    echo "{\"status\":\"error\",\"message\":\"Lock owned by different session: $existing_session\"}"
    return 1
  fi

  # Read existing values
  local created_at created_iso
  created_at=$(jq -r '.created_at' "$lock_file" 2>/dev/null || echo "0")
  created_iso=$(jq -r '.created_iso // ""' "$lock_file" 2>/dev/null || echo "")

  # Write updated lock file atomically in JSON format
  local temp_lock="${lock_file}.$$"
  cat > "$temp_lock" << EOF
{
  "session_id": "${session_id}",
  "created_at": ${created_at},
  "worktree": "${worktree}",
  "created_iso": "${created_iso}"
}
EOF

  mv -f "$temp_lock" "$lock_file"
  echo "{\"status\":\"updated\",\"message\":\"Lock updated with worktree\",\"worktree\":\"$worktree\"}"
  return 0
}

# Release lock (only if owned by this session)
release_lock() {
  local issue_id="$1"
  local session_id="$2"

  # Validate session_id format (M204)
  if ! validate_session_id "$session_id"; then
    return 1
  fi

  local lock_file
  lock_file=$(get_lock_file "$issue_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"status":"released","message":"No lock exists"}'
    return 0
  fi

  local existing_session
  existing_session=$(jq -r '.session_id' "$lock_file" 2>/dev/null || echo "")

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
  local issue_id="$1"

  local lock_file
  lock_file=$(get_lock_file "$issue_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"locked":false,"message":"Issue not locked"}'
    return 0
  fi

  local session_id created_at worktree
  session_id=$(jq -r '.session_id' "$lock_file" 2>/dev/null || echo "unknown")
  created_at=$(jq -r '.created_at' "$lock_file" 2>/dev/null || echo "0")
  worktree=$(jq -r '.worktree // ""' "$lock_file" 2>/dev/null || echo "")

  local now age
  now=$(current_timestamp)
  age=$((now - created_at))

  echo "{\"locked\":true,\"session_id\":\"$session_id\",\"age_seconds\":$age,\"worktree\":\"$worktree\"}"
  return 0
}

# Force release lock (user action - ignores session ownership)
force_release_lock() {
  local issue_id="$1"

  local lock_file
  lock_file=$(get_lock_file "$issue_id")

  if [[ ! -f "$lock_file" ]]; then
    echo '{"status":"released","message":"No lock exists"}'
    return 0
  fi

  local existing_session
  existing_session=$(jq -r '.session_id' "$lock_file" 2>/dev/null || echo "unknown")

  rm -f "$lock_file"
  echo "{\"status\":\"released\",\"message\":\"Lock forcibly released (was owned by $existing_session)\"}"
  return 0
}

# List all locks (optimized: single jq call instead of O(n))
list_locks() {
  ensure_lock_dir

  local now
  now=$(current_timestamp)

  # Collect all lock data in a simple format first
  local lock_data=""
  for lock_file in "$LOCK_DIR"/*.lock; do
    [[ ! -f "$lock_file" ]] && continue

    local issue_id session_id created_at age
    issue_id=$(basename "$lock_file" .lock)

    session_id=$(jq -r '.session_id' "$lock_file" 2>/dev/null || echo "unknown")
    created_at=$(jq -r '.created_at' "$lock_file" 2>/dev/null || echo "0")

    age=$((now - created_at))

    # Accumulate as tab-separated values
    lock_data+="${issue_id}\t${session_id}\t${age}\n"
  done

  # Single jq call to construct entire JSON array
  if [[ -z "$lock_data" ]]; then
    echo "[]"
  else
    printf "%b" "$lock_data" | jq -R -s '
      split("\n") | map(select(length > 0)) | map(
        split("\t") | {issue: .[0], session: .[1], age_seconds: (.[2] | tonumber)}
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
Usage: issue-lock.sh <command> <project-dir> [args]

Commands:
  acquire <project-dir> <issue-id> <session-id> [worktree]  - Acquire lock for issue
  update <project-dir> <issue-id> <session-id> <worktree>   - Update lock with worktree path
  release <project-dir> <issue-id> <session-id>             - Release lock (only if owned)
  force-release <project-dir> <issue-id>                    - Force release lock (any owner)
  check <project-dir> <issue-id>                            - Check if issue is locked
  list <project-dir>                                        - List all locks

Arguments:
  project-dir    Project root directory (contains .claude/cat/)
  issue-id       Issue identifier (e.g., "2.0-fix-parser")
  session-id     Claude session UUID
  worktree       Optional worktree path

Locks are persistent and never expire automatically.
Use 'force-release' to remove stale locks from crashed sessions.
EOF
}

# Helper to validate project directory
validate_project_dir() {
  local dir="$1"
  if [[ -z "$dir" ]]; then
    echo '{"status":"error","message":"project-dir is required"}'
    exit 1
  fi
  if [[ ! -d "$dir/.claude/cat" ]]; then
    echo '{"status":"error","message":"Not a CAT project: '"$dir"' (no .claude/cat directory)"}'
    exit 1
  fi
  PROJECT_DIR="$dir"
  LOCK_DIR="$PROJECT_DIR/.claude/cat/locks"
}

case "${1:-}" in
  acquire)
    [[ $# -lt 4 ]] && { echo '{"status":"error","message":"Usage: acquire <project-dir> <issue-id> <session-id> [worktree]"}'; exit 1; }
    validate_project_dir "$2"
    acquire_lock "$3" "$4" "${5:-}"
    ;;
  update)
    [[ $# -lt 5 ]] && { echo '{"status":"error","message":"Usage: update <project-dir> <issue-id> <session-id> <worktree>"}'; exit 1; }
    validate_project_dir "$2"
    update_lock "$3" "$4" "$5"
    ;;
  release)
    [[ $# -lt 4 ]] && { echo '{"status":"error","message":"Usage: release <project-dir> <issue-id> <session-id>"}'; exit 1; }
    validate_project_dir "$2"
    release_lock "$3" "$4"
    ;;
  force-release)
    [[ $# -lt 3 ]] && { echo '{"status":"error","message":"Usage: force-release <project-dir> <issue-id>"}'; exit 1; }
    validate_project_dir "$2"
    force_release_lock "$3"
    ;;
  check)
    [[ $# -lt 3 ]] && { echo '{"status":"error","message":"Usage: check <project-dir> <issue-id>"}'; exit 1; }
    validate_project_dir "$2"
    check_lock "$3"
    ;;
  list)
    [[ $# -lt 2 ]] && { echo '{"status":"error","message":"Usage: list <project-dir>"}'; exit 1; }
    validate_project_dir "$2"
    list_locks
    ;;
  -h|--help|help)
    usage
    exit 0
    ;;
  *)
    echo '{"status":"error","message":"Unknown command. Use --help for usage."}'
    exit 1
    ;;
esac
