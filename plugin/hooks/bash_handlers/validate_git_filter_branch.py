"""
Validate git filter-branch and history-rewriting commands.

Prevents use of --all or --branches flags that would rewrite protected branches.
"""

import re
from . import register_handler


class ValidateGitFilterBranchHandler:
    """Validate git filter-branch commands."""

    def check(self, command: str, context: dict) -> dict | None:
        # WARN: git filter-branch is deprecated (but allow it)
        if re.search(r'(^|;|&&|\|)\s*git\s+filter-branch', command):
            # This is just a warning - return as warning, not block
            pass  # Warnings go to stderr, blocks are what we return

        # BLOCK: dangerous --all or --branches flags with history rewriting
        if re.search(r'(^|;|&&|\|)\s*git\s+(filter-branch|rebase)\s+.*\s+--(all|branches)(\s|$)', command):
            return {
                "decision": "block",
                "reason": """🚨 CRITICAL: DANGEROUS GIT HISTORY REWRITING DETECTED

**Blocked command**: git filter-branch/rebase with --all or --branches

This would rewrite history on ALL branches including:
- Version branches (v1.0, v2.0, etc.)
- Release branches
- Other protected branches

**WHAT TO DO INSTEAD:**
1. Target specific branches explicitly:
   git filter-branch --tree-filter 'command' main feature-branch

2. Use git-filter-repo with explicit refs:
   git filter-repo --refs main --refs feature-branch

**See**: /cat:git-rewrite-history skill for proper usage"""
            }

        return None


register_handler(ValidateGitFilterBranchHandler())
