"""
Block merge commits to enforce linear git history.

See: Learning M047 - use ff-merge to maintain linear history
"""

import re
import subprocess
from . import register_handler


class BlockMergeCommitsHandler:
    """Enforce linear git history by blocking merge commits."""

    def check(self, command: str, context: dict) -> dict | None:
        # Skip if not a git merge command
        if not re.search(r'(^|;|&&|\|)\s*git\s+merge(?!-)', command):
            return None

        # BLOCK: git merge --no-ff (explicitly creates merge commit)
        if re.search(r'git\s+merge(?!-)\s+.*--no-ff|git\s+merge(?!-)\s+--no-ff', command):
            return {
                "decision": "block",
                "reason": """**BLOCKED: git merge --no-ff creates merge commits**

Linear history is required. Use one of:
- `git merge --ff-only <branch>` - Fast-forward only, fails if not possible
- `git rebase <branch>` - Rebase for linear history

Or use the `/cat:git-merge-linear` skill which handles this correctly.

**See**: Learning M047 - merge commits break linear history"""
            }

        # BLOCK: git merge without --ff-only or --squash
        if not re.search(r'--ff-only|--squash', command):
            return {
                "decision": "block",
                "reason": """**BLOCKED: git merge without --ff-only may create merge commits**

Linear history is required. Use one of:
- `git merge --ff-only <branch>` - Fast-forward only, fails if not possible
- `git merge --squash <branch>` - Squash commits into one
- `git rebase <branch>` - Rebase for linear history

Or use the `/cat:git-merge-linear` skill which handles this correctly.

**See**: Learning M047 - merge commits break linear history"""
            }

        return None


register_handler(BlockMergeCommitsHandler())
