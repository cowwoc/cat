"""
Validate dangerous git operations.

Warns or blocks commands like git push --force, reset --hard, etc.
"""

import re
from . import register_handler


class ValidateGitOperationsHandler:
    """Warn or block dangerous git commands."""

    def check(self, command: str, context: dict) -> dict | None:
        # Block: git push --force to main/master
        if re.search(r'git\s+push\s+.*--force.*\s+(main|master)(\s|$)', command, re.IGNORECASE):
            return {
                "decision": "block",
                "reason": """**BLOCKED: Force push to main/master**

Force pushing to main/master rewrites shared history and can cause:
- Lost commits from other contributors
- Broken references
- Confused collaborators

Use --force-with-lease instead, or ask the user if they really want this."""
            }

        # Block: git reset --hard without explicit acknowledgment
        if re.search(r'git\s+reset\s+--hard', command):
            # Allow if in a worktree or has acknowledgment
            if '# ACKNOWLEDGED' in command or 'worktrees' in command:
                return None
            return {
                "decision": "block",
                "reason": """**BLOCKED: git reset --hard can lose uncommitted work**

This command discards all uncommitted changes permanently.

If you're sure:
- In a worktree: Use /cat:git-rebase skill
- Main worktree: Add # ACKNOWLEDGED comment

Consider: git stash to save work before reset."""
            }

        return None


register_handler(ValidateGitOperationsHandler())
