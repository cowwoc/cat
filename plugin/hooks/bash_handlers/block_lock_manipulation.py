"""
Block direct manipulation of CAT lock files.

M096: Agent deleted lock files without user permission.
"""

import re
from . import register_handler


class BlockLockManipulationHandler:
    """Block rm commands targeting lock files."""

    def check(self, command: str, context: dict) -> dict | None:
        # Check for rm commands targeting lock files
        if re.search(r'rm\s+(-[frivI]+\s+)*.*\.claude/cat/locks', command):
            return {
                "decision": "block",
                "reason": """BLOCKED: Direct deletion of lock files is not allowed.

Lock files exist to prevent concurrent task execution. Deleting them directly
bypasses safety checks and could cause:
- Concurrent execution of the same task
- Merge conflicts
- Duplicate work
- Data corruption

CORRECT ACTIONS when encountering a lock:
1. Execute a DIFFERENT task instead (use /cat:status to find available tasks)
2. If you believe the lock is from a crashed session, ask the USER to run /cat:cleanup

NEVER delete lock files directly."""
            }

        # Also block force removal of the entire locks directory
        if re.search(r'rm\s+(-[frivI]+\s+)*.*\.claude/cat/locks/?(\s|$|")', command):
            return {
                "decision": "block",
                "reason": "BLOCKED: Cannot remove the locks directory. Use /cat:cleanup to safely remove stale locks."
            }

        return None


register_handler(BlockLockManipulationHandler())
