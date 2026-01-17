#!/bin/bash
# block-lock-manipulation.sh - Prevent direct manipulation of CAT lock files
#
# TRIGGER: PreToolUse (Bash)
#
# M096: Agent deleted lock files without user permission, bypassing the task-lock.sh
# script and its safety guidance. This hook blocks direct rm/delete operations on
# the locks directory.
#
# Correct behavior: Use task-lock.sh script for all lock operations, or ask user
# to run /cat:cleanup for stale lock removal.

set -euo pipefail

# Source standard hook libraries
SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Initialize as Bash hook (reads stdin JSON, extracts command)
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

# Check for rm commands targeting lock files
if echo "$HOOK_COMMAND" | grep -qE 'rm\s+(-[frivI]+\s+)*.*\.claude/cat/locks'; then
    output_hook_block "BLOCKED: Direct deletion of lock files is not allowed.

Lock files exist to prevent concurrent task execution. Deleting them directly
bypasses safety checks and could cause:
- Concurrent execution of the same task
- Merge conflicts
- Duplicate work
- Data corruption

CORRECT ACTIONS when encountering a lock:
1. Execute a DIFFERENT task instead (use /cat:status to find available tasks)
2. If you believe the lock is from a crashed session, ask the USER to run /cat:cleanup

NEVER delete lock files directly. The task-lock.sh script handles all lock operations safely."
    exit 0
fi

# Also block force removal of the entire locks directory
if echo "$HOOK_COMMAND" | grep -qE 'rm\s+(-[frivI]+\s+)*.*\.claude/cat/locks/?(\s|$|")'; then
    output_hook_block "BLOCKED: Cannot remove the locks directory. Use /cat:cleanup to safely remove stale locks."
    exit 0
fi

echo '{}'
exit 0
