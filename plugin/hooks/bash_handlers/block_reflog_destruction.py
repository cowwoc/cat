"""
Block premature destruction of git reflog (recovery safety net).

ADDED: 2026-01-05 after agent ran "git reflog expire --expire=now --all && git gc --prune=now"
immediately after git filter-branch, permanently destroying recovery options.
"""

import re
from . import register_handler


class BlockReflogDestructionHandler:
    """Prevent premature destruction of git reflog."""

    def check(self, command: str, context: dict) -> dict | None:
        # Check for acknowledgment bypass
        if re.search(r'# ACKNOWLEDGED:.*([Rr]eflog|gc|prune)', command):
            return None

        # Check for reflog expire with --expire=now (dangerous)
        if re.search(r'git\s+reflog\s+expire.*--expire=(now|all|0)', command):
            return {
                "decision": "block",
                "reason": """**BLOCKED: Premature reflog destruction detected**

This command PERMANENTLY DESTROYS the git reflog, which is your PRIMARY RECOVERY
MECHANISM after history-rewriting operations like:
- git filter-branch
- git rebase
- git reset --hard
- git commit --amend

**Why this is dangerous:**
The reflog keeps references to ALL previous HEAD positions for ~90 days by default.
If something went wrong with filter-branch or rebase, you can recover using:
  git reflog
  git reset --hard HEAD@{N}

Once you run 'git reflog expire --expire=now', this recovery option is GONE FOREVER.

**RECOMMENDED APPROACH:**
1. Wait 24-48 hours after major operations
2. Verify everything works correctly
3. THEN (and only then) clean up if needed

To bypass (if user explicitly requests): Add comment # ACKNOWLEDGED: reflog"""
            }

        # Check for git gc --prune=now (also dangerous)
        if re.search(r'git\s+gc\s+.*--prune=(now|all)', command):
            return {
                "decision": "block",
                "reason": """**BLOCKED: Aggressive garbage collection detected**

This command with --prune=now permanently removes unreachable objects.
Combined with reflog expire, this destroys ALL recovery options.

**RECOMMENDED:** Let git gc run naturally with default 2-week prune period.

To bypass (if user explicitly requests): Add comment # ACKNOWLEDGED: gc prune"""
            }

        return None


register_handler(BlockReflogDestructionHandler())
