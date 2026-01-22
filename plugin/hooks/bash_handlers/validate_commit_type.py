"""
Validate commit message uses correct commit types.

Blocks commits with invalid types like 'feat:', 'fix:', etc.
Requires specific conventional commit types.
"""

import re
from . import register_handler

VALID_COMMIT_TYPES = [
    "feat",      # New feature
    "fix",       # Bug fix
    "docs",      # Documentation
    "style",     # Formatting
    "refactor",  # Code restructuring
    "perf",      # Performance
    "test",      # Tests
    "build",     # Build system
    "ci",        # CI configuration
    "chore",     # Maintenance
    "revert",    # Revert commit
    "config",    # Configuration changes
    "planning",  # Planning documents
    "bugfix",    # Alternative for fix
]


class ValidateCommitTypeHandler:
    """Validate commit messages use correct types."""

    def check(self, command: str, context: dict) -> dict | None:
        # Only check git commit commands
        if not re.search(r'git\s+commit', command):
            return None

        # Extract commit message from -m flag
        match = re.search(r'-m\s+["\']([^"\']+)["\']', command)
        if not match:
            # Could be using heredoc or other method
            return None

        message = match.group(1)

        # Check for conventional commit format
        type_match = re.match(r'^(\w+)(\(.+\))?:', message)
        if type_match:
            commit_type = type_match.group(1).lower()
            if commit_type not in VALID_COMMIT_TYPES:
                return {
                    "decision": "block",
                    "reason": f"""**BLOCKED: Invalid commit type '{commit_type}'**

Valid commit types: {', '.join(VALID_COMMIT_TYPES)}

Example: feat: add user authentication
         fix: resolve memory leak in parser"""
                }

        return None


register_handler(ValidateCommitTypeHandler())
