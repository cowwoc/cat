#!/bin/bash
# Hook: session-unlock.sh
# Trigger: SessionEnd
# Purpose: Release locks on session end, clean up any lock files

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Get project directory from environment or use current directory
PROJECT_DIR="${CLAUDE_PROJECT_DIR:?CLAUDE_PROJECT_DIR must be set}"

# Configuration
# CAT files MUST be inside .claude/cat/ directory
LOCK_DIR="${PROJECT_DIR}/.claude/cat/locks"
PROJECT_NAME="${PROJECT_DIR##*/}"
LOCK_FILE="$LOCK_DIR/${PROJECT_NAME}.lock"

# Remove project lock file if it exists
if [[ -f "$LOCK_FILE" ]]; then
    rm -f "$LOCK_FILE"
    echo "Session lock released: $LOCK_FILE"
fi

# Read session_id from stdin if available (used for lock cleanup)
SESSION_ID=""
if [[ ! -t 0 ]]; then
    INPUT=$(cat 2>/dev/null || echo "{}")
    SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null || echo "")
fi

# Clean up task locks owned by this session
TASK_LOCK_DIR="${PROJECT_DIR}/.claude/cat/locks"
if [[ -d "$TASK_LOCK_DIR" ]] && [[ -n "$SESSION_ID" ]]; then
    find "$TASK_LOCK_DIR" -name "*.lock" -exec sh -c '
        if grep -q "session_id='"$SESSION_ID"'" "$1" 2>/dev/null; then
            rm -f "$1"
            echo "Task lock released: $1"
        fi
    ' _ {} \;
fi

# Clean up any worktree locks (legacy)
WORKTREE_LOCK_DIR="${PROJECT_DIR}/.claude/cat/worktree-locks"
if [[ -d "$WORKTREE_LOCK_DIR" ]] && [[ -n "$SESSION_ID" ]]; then
    find "$WORKTREE_LOCK_DIR" -name "*.lock" -exec sh -c '
        if grep -q "'"$SESSION_ID"'" "$1" 2>/dev/null; then
            rm -f "$1"
            echo "Worktree lock released: $1"
        fi
    ' _ {} \;
fi

# Clean up stale lock files (older than 24 hours)
if [[ -d "$LOCK_DIR" ]]; then
    find "$LOCK_DIR" -name "*.lock" -mtime +1 -delete 2>/dev/null || true
fi

exit 0
