"""
Bash command handlers for PreToolUse validation.

These handlers check bash commands before execution and can:
- Block dangerous commands (return decision="block")
- Warn about risky commands (return warning message)
- Allow commands (return None)
"""

from typing import Protocol, runtime_checkable

_HANDLERS: list["BashHandler"] = []


@runtime_checkable
class BashHandler(Protocol):
    """Protocol for bash command handlers."""

    def check(self, command: str, context: dict) -> dict | None:
        """
        Check a bash command before execution.

        Args:
            command: The bash command to check
            context: Additional context (tool_input, session_id, etc.)

        Returns:
            None to allow the command, or a dict with:
            - {"decision": "block", "reason": "..."} to block
            - {"warning": "..."} to warn but allow
        """
        ...


def register_handler(handler: "BashHandler") -> None:
    """Register a bash handler."""
    _HANDLERS.append(handler)


def get_all_handlers() -> list["BashHandler"]:
    """Get all registered handlers."""
    return _HANDLERS.copy()


# Import handlers to register them
from . import block_lock_manipulation
from . import block_main_rebase
from . import block_merge_commits
from . import block_reflog_destruction
from . import validate_commit_type
from . import validate_git_operations
from . import validate_git_filter_branch
from . import remind_git_squash
from . import warn_file_extraction
from . import compute_box_lines
from . import validate_worktree_remove
