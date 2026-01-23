"""
Validate commit message uses correct commit types.

Blocks commits with invalid types like 'feat:', 'fix:', etc.
Requires specific conventional commit types.
"""

import re
from . import register_handler

VALID_COMMIT_TYPES = [
    "feature",      # New feature (NOT "feat")
    "bugfix",       # Bug fix (NOT "fix")
    "docs",         # Documentation
    "style",        # Formatting
    "refactor",     # Code restructuring
    "performance",  # Performance (NOT "perf")
    "test",         # Tests
    "config",       # Configuration changes
    "planning",     # Planning documents
    "revert",       # Revert commit
]

# Short forms that are NOT allowed (per commit-types.md)
# feat, fix, chore, build, ci, perf


class ValidateCommitTypeHandler:
    """Validate commit messages use correct types."""

    def check(self, command: str, context: dict) -> dict | None:
        # Only check git commit commands
        if not re.search(r'git\s+commit', command):
            return None

        # Extract commit message from -m flag
        # Pattern 1: -m "message" or -m 'message'
        match = re.search(r'-m\s+["\']([^"\']+)["\']', command)
        if match:
            message = match.group(1)
        else:
            # Pattern 2: HEREDOC format: -m "$(cat <<'EOF'\nmessage\nEOF\n)"
            heredoc_match = re.search(r'-m\s+"\$\(cat\s+<<[\'"]?EOF[\'"]?\s*\n(.+?)\nEOF', command, re.DOTALL)
            if heredoc_match:
                message = heredoc_match.group(1).strip()
            else:
                # No recognizable message format
                return None

        # Strip leading whitespace from each line (heredoc indentation)
        message = '\n'.join(line.strip() for line in message.split('\n'))

        # Check for conventional commit format
        type_match = re.match(r'^(\w+)(\(.+\))?:', message)
        if type_match:
            commit_type = type_match.group(1).lower()
            if commit_type not in VALID_COMMIT_TYPES:
                return {
                    "decision": "block",
                    "reason": f"""**BLOCKED: Invalid commit type '{commit_type}'**

Valid commit types: {', '.join(VALID_COMMIT_TYPES)}

Example: feature: add user authentication
         bugfix: resolve memory leak in parser"""
                }

        return None


register_handler(ValidateCommitTypeHandler())
