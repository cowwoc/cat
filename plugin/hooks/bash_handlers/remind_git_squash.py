"""
Remind about git squash skill when interactive rebase is detected.
"""

import re
from . import register_handler


class RemindGitSquashHandler:
    """Suggest /cat:git-squash for interactive rebase."""

    def check(self, command: str, context: dict) -> dict | None:
        # Check for git rebase -i (interactive)
        if re.search(r'git\s+rebase\s+.*-i', command):
            return {
                "warning": """💡 SUGGESTION: Use /cat:git-squash instead of git rebase -i

The /cat:git-squash skill provides:
- Automatic backup before squashing
- Conflict recovery guidance
- Proper commit message formatting

To squash commits: /cat:git-squash"""
            }

        return None


register_handler(RemindGitSquashHandler())
