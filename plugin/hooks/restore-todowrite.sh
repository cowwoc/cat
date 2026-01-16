#!/bin/bash
# Hook: restore-todowrite.sh
# Trigger: SessionStart
# Purpose: Restore TodoWrite state from backup on session start
#          to preserve task tracking across sessions.

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Read stdin
INPUT=$(cat)

SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null || echo "")

# Default backup directory - can be customized per project
# CAT files MUST be inside .claude/cat/ directory
BACKUP_DIR="${CLAUDE_PROJECT_DIR:?CLAUDE_PROJECT_DIR must be set}/.claude/cat/backups/todowrite"

# Check if backup directory exists
if [[ ! -d "$BACKUP_DIR" ]]; then
    exit 0
fi

# Find the most recently modified session file
SESSION_BACKUP=$(ls -t "${BACKUP_DIR}"/todowrite_session_*.json 2>/dev/null | head -1 || true)

if [[ -z "$SESSION_BACKUP" || ! -f "$SESSION_BACKUP" ]]; then
    exit 0
fi

# Check backup age (only restore if less than 24 hours old)
BACKUP_AGE=$(( $(date +%s) - $(stat -c %Y "$SESSION_BACKUP") ))
MAX_AGE=86400  # 24 hours

if [[ $BACKUP_AGE -gt $MAX_AGE ]]; then
    exit 0
fi

# Read the backup
TODOS=$(cat "$SESSION_BACKUP" 2>/dev/null || echo "")

if [[ -z "$TODOS" || "$TODOS" == "null" || "$TODOS" == "[]" ]]; then
    exit 0
fi

# Count pending items
PENDING_COUNT=$(echo "$TODOS" | jq '[.[] | select(.status == "pending" or .status == "in_progress")] | length' 2>/dev/null || echo "0")

if [[ "$PENDING_COUNT" -gt 0 ]]; then
    cat >&2 << EOF

================================================================================
  TODOWRITE STATE RECOVERED
================================================================================

  Found $PENDING_COUNT pending task(s) from previous session.
  Backup: $(basename "$SESSION_BACKUP")

  Pending items:
$(echo "$TODOS" | jq -r '.[] | select(.status == "pending" or .status == "in_progress") | "    - [\(.status)] \(.content)"' 2>/dev/null | head -5)

  To restore: Use TodoWrite tool with this state
  To ignore: Start fresh with new TodoWrite

================================================================================

EOF
fi

exit 0
