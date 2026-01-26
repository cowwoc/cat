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
        # Check HEREDOC first (more specific pattern)
        # Pattern 1: HEREDOC format: -m "$(cat <<'EOF'\nmessage\nEOF\n)"
        heredoc_match = re.search(r'-m\s+"\$\(cat\s+<<[\'"]?EOF[\'"]?\s*\n(.+?)\nEOF', command, re.DOTALL)
        if heredoc_match:
            message = heredoc_match.group(1).strip()
        else:
            # Pattern 2: Simple -m "message" or -m 'message'
            match = re.search(r'-m\s+["\']([^"\']+)["\']', command)
            if match:
                message = match.group(1)
            else:
                # -m flag present but couldn't parse - suspicious in Claude Code context
                # Check if there's actually a -m flag we failed to parse
                if re.search(r'-m\s+', command):
                    return {
                        "decision": "block",
                        "reason": """**BLOCKED: Could not parse commit message format**

The commit uses -m flag but the message format is not recognized.
Claude Code should use the standard HEREDOC format:

```
git commit -m "$(cat <<'EOF'
type: description
EOF
)"
```

If this is a legitimate format that should be supported, update
validate_commit_type.py to handle it."""
                    }
                # No -m flag at all (interactive mode) - unusual for Claude Code
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

            # Check for docs: on plugin/ files (should be config:)
            # M255: plugin/ files are Claude-facing, not user-facing
            if commit_type == "docs":
                # Check if command mentions plugin/ files via git add or staged files
                if re.search(r'plugin/', command):
                    return {
                        "decision": "block",
                        "reason": """**BLOCKED: Wrong commit type for plugin/ files (M255)**

`docs:` is for **user-facing** documentation (README.md, API docs).
`plugin/` files are **Claude-facing** instructions.

Use `config:` for plugin/ files:
- plugin/concepts/*.md → config: (Claude reference docs)
- plugin/commands/*.md → config: (Claude commands)
- plugin/skills/*/*.md → config: (Claude skills)

Example: config: update version-scheme.md documentation"""
                    }

        return None


register_handler(ValidateCommitTypeHandler())
