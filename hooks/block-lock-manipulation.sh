#!/bin/bash
# block-lock-manipulation.sh - Prevent direct manipulation of CAT lock files
#
# M096: Agent deleted lock files without user permission, bypassing the task-lock.sh
# script and its safety guidance. This hook blocks direct rm/delete operations on
# the locks directory.
#
# Correct behavior: Use task-lock.sh script for all lock operations, or ask user
# to run /cat:cleanup for stale lock removal.

set -euo pipefail

# Only check Bash tool calls
TOOL_NAME="${TOOL_NAME:-}"
if [[ "$TOOL_NAME" != "Bash" ]]; then
  exit 0
fi

# Get the command being executed
COMMAND="${TOOL_INPUT_command:-}"
if [[ -z "$COMMAND" ]]; then
  exit 0
fi

# Check for rm commands targeting lock files
if echo "$COMMAND" | grep -qE 'rm\s+(-[frivI]+\s+)*.*\.claude/cat/locks'; then
  cat << 'EOF'
BLOCKED: Direct deletion of lock files is not allowed.

Lock files exist to prevent concurrent task execution. Deleting them directly
bypasses safety checks and could cause:
- Concurrent execution of the same task
- Merge conflicts
- Duplicate work
- Data corruption

CORRECT ACTIONS when encountering a lock:
1. Execute a DIFFERENT task instead (use /cat:status to find available tasks)
2. If you believe the lock is from a crashed session, ask the USER to run /cat:cleanup

NEVER delete lock files directly. The task-lock.sh script handles all lock operations safely.
EOF
  exit 2
fi

# Also block force removal of the entire locks directory
if echo "$COMMAND" | grep -qE 'rm\s+(-[frivI]+\s+)*.*\.claude/cat/locks/?(\s|$|")'; then
  cat << 'EOF'
BLOCKED: Cannot remove the locks directory.

Use /cat:cleanup to safely remove stale locks, or task-lock.sh for specific lock operations.
EOF
  exit 2
fi

exit 0
