#!/bin/bash
# TodoWrite Save Hook
#
# ADDED: 2025-12-01
# PURPOSE: Save TodoWrite state before context compaction to preserve
#          task tracking across sessions.
#
# Trigger: PreCompact
# Output: Saves state to .claude/backups/todowrite/

set -euo pipefail
trap 'echo "ERROR in save-todowrite.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Read stdin (compact context)
INPUT=$(cat)

SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null || echo "unknown")

# Create backup directory
BACKUP_DIR="/workspace/.claude/backups/todowrite"
mkdir -p "$BACKUP_DIR"

# Primary backup: session-specific file that restore will read
# This file is overwritten each time to keep the latest state for this session
SESSION_BACKUP="${BACKUP_DIR}/todowrite_session_${SESSION_ID}.json"

# Check for existing TodoWrite state in temp
TODOWRITE_STATE="/tmp/todowrite_state_${SESSION_ID}.json"

if [[ -f "$TODOWRITE_STATE" ]]; then
    cp "$TODOWRITE_STATE" "$SESSION_BACKUP"
    echo "TodoWrite state saved to: $SESSION_BACKUP" >&2
fi

# Also try to extract TodoWrite from the compact context if available
TODOS=$(echo "$INPUT" | jq -r '.todos // empty' 2>/dev/null || echo "")

if [[ -n "$TODOS" && "$TODOS" != "null" ]]; then
    echo "$TODOS" > "$SESSION_BACKUP"
    echo "TodoWrite context saved to: $SESSION_BACKUP" >&2
fi

# Cleanup: Keep only last 5 session backups (by modification time)
ls -t "${BACKUP_DIR}/todowrite_session_"*.json 2>/dev/null | tail -n +6 | xargs -r rm -f

exit 0
