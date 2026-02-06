"""
Detect problematic shell operators that get escaped by Bash tool.

Warns when !=, ==, // operators are detected in inline Bash commands.
These operators get shell-escaped by the Bash tool, producing incorrect results.

See: MEMORY.md learnings M431, M449
"""

import re
from . import register_handler


class DetectShellOperatorsHandler:
    """Warn about shell operators that get escaped in inline Bash commands."""

    def check(self, command: str, context: dict) -> dict | None:
        # Don't warn for script file execution
        if self._is_script_execution(command):
            return None

        # Don't warn if already using Python
        if self._is_using_python(command):
            return None

        # Don't warn for git commit messages (they legitimately contain == etc.)
        if self._is_git_commit(command):
            return None

        # Check for problematic operators
        problematic = self._find_problematic_operators(command)
        if problematic:
            operators_str = ", ".join(f"'{op}'" for op in sorted(set(problematic)))
            return {
                "warning": f"""⚠️  WARNING: Shell operator escaping detected: {operators_str}

These operators get shell-escaped when passed inline via Bash tool, causing incorrect results.

Problematic patterns:
- [[ $var != "value" ]]  - != gets escaped in [[ ]] tests
- [[ $var == "value" ]]  - == gets escaped in [[ ]] tests
- jq '.field != "value"' - != gets escaped in jq expressions
- jq '.field // "default"' - // gets escaped in jq expressions

SOLUTION: Use Python instead:
  python3 -c "import sys; sys.exit(0 if 'value' != 'other' else 1)"

Or write logic to a script file first, then execute the script.

See MEMORY.md learnings M431, M449 for details."""
            }

        return None

    def _is_script_execution(self, command: str) -> bool:
        """Check if command is executing a script file."""
        # Match: bash script.sh, python3 script.py, ./script.sh, sh script.sh
        patterns = [
            r'^\s*bash\s+\S+\.sh',
            r'^\s*sh\s+\S+\.sh',
            r'^\s*python3?\s+\S+\.py',
            r'^\s*\./\S+\.(sh|py)',
        ]
        return any(re.search(pattern, command) for pattern in patterns)

    def _is_using_python(self, command: str) -> bool:
        """Check if command already uses Python for logic."""
        # Match: python3 -c "...", python -c "..."
        return bool(re.search(r'python3?\s+-c\s+', command))

    def _is_git_commit(self, command: str) -> bool:
        """Check if command is a git commit (messages legitimately contain operators)."""
        return bool(re.search(r'git\s+commit\s+', command))

    def _find_problematic_operators(self, command: str) -> list[str]:
        """Find problematic operators in command, excluding comments and safe contexts."""
        operators = []

        # Check for != operator
        # Look for != in contexts like [[ ]], if statements, jq
        if re.search(r'!=', command):
            # Exclude heredoc comments (lines starting with # after <<)
            if not self._is_only_in_comments(command, '!='):
                operators.append('!=')

        # Check for == operator
        if re.search(r'==', command):
            if not self._is_only_in_comments(command, '=='):
                operators.append('==')

        # Check for // operator (common in jq expressions)
        if re.search(r'//', command):
            # Exclude comment-only lines and URLs (http://, https://)
            if not self._is_only_in_comments(command, '//'):
                # Check if it's part of a URL
                if not re.search(r'https?://', command):
                    operators.append('//')

        return operators

    def _is_only_in_comments(self, command: str, operator: str) -> bool:
        """Check if operator appears only in comments."""
        # Split command into lines
        lines = command.split('\n')

        for line in lines:
            if operator in line:
                # Check if line is a comment (starts with # after whitespace)
                stripped = line.lstrip()
                if not stripped.startswith('#'):
                    # Operator found outside comment
                    return False

        # All occurrences are in comments
        return True


register_handler(DetectShellOperatorsHandler())
