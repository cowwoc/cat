"""
Validate commit message uses correct commit types.

Blocks commits with invalid types like 'feat:', 'fix:', etc.
Requires specific conventional commit types.

Also validates that commit type matches file patterns:
- docs: only for user-facing files (README, API docs)
- config: for Claude-facing files (CLAUDE.md, plugin/, skills/)
"""

import re
import subprocess
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

# Files that are Claude-facing and should use config:, not docs:
# Per M255, M306: plugin/, CLAUDE.md, .claude/, hooks/, skills/, etc.
CLAUDE_FACING_PATTERNS = [
    "CLAUDE.md",
    "plugin/",         # CAT plugin directory (M255, M306)
    ".claude/",        # Claude config directory
    "hooks/",          # Hook scripts
    "skills/",         # Skill definitions
    "concepts/",       # Concept documents
    "commands/",       # Command definitions
]


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

            # A007: Check for docs: used on Claude-facing files
            if commit_type == "docs":
                staged_files = self._get_staged_files()
                if staged_files:
                    for pattern in CLAUDE_FACING_PATTERNS:
                        for file_path in staged_files:
                            if pattern in file_path:
                                return {
                                    "decision": "block",
                                    "reason": f"""**BLOCKED: 'docs:' used for Claude-facing file**

Staged file '{file_path}' matches Claude-facing pattern '{pattern}'

Claude-facing files should use 'config:', not 'docs:':
- docs: = user-facing (README, API docs)
- config: = Claude-facing (CLAUDE.md, plugin/, skills/)

Fix: Change 'docs:' to 'config:' in your commit message

See CLAUDE.md "Commit Types" section for reference (M255, M306)."""
                                }

        # M382: Warn on compression commits without validation confirmation
        if self._is_compression_commit(message):
            return {
                "additionalContext": """⚠️ COMPRESSION COMMIT DETECTED (M382)

**MANDATORY VERIFICATION**: Before this commit proceeds, confirm:
1. Validation was run via /cat:shrink-doc workflow (NOT standalone /compare-docs)
2. **ALL files achieved score = 1.0** (not 0.95, not "close enough")
3. No validation was skipped or bypassed

**If validation was run directly via /compare-docs** (threshold 0.95):
- STOP: compare-docs defaults to 0.95, but shrink-doc requires 1.0
- Re-run validation through /cat:shrink-doc or explicitly with threshold 1.0

**M335 COMMIT GATE**: Score < 1.0 → MUST NOT commit. No exceptions.

If you cannot confirm all files scored exactly 1.0, abort this commit."""
            }

        return None

    def _get_staged_files(self) -> list:
        """Get list of staged files for commit."""
        try:
            result = subprocess.run(
                ["git", "diff", "--cached", "--name-only"],
                capture_output=True,
                text=True,
                timeout=5
            )
            if result.returncode == 0:
                return [f.strip() for f in result.stdout.strip().split('\n') if f.strip()]
        except (subprocess.TimeoutExpired, Exception):
            pass
        return []

    def _is_compression_commit(self, message: str) -> bool:
        """Check if commit message suggests document compression.

        M382: Compression commits need validation gate warning.
        """
        message_lower = message.lower()

        # Check for compression-related keywords
        compression_keywords = [
            "compress",
            "shrink",
            "reduction",
            "token reduction",
            "word reduction",
        ]

        for keyword in compression_keywords:
            if keyword in message_lower:
                # Also verify this is about skill/doc files
                staged = self._get_staged_files()
                for f in staged:
                    if "skill" in f.lower() or ".md" in f.lower():
                        return True

        return False


register_handler(ValidateCommitTypeHandler())
