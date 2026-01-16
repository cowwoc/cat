"""
Bash PostToolUse handlers module.

Each handler implements a check() method that receives:
- command: str - The bash command that was executed
- context: dict - Contains tool_input, tool_result, session_id, hook_data

Handlers can return:
- {"warning": "..."} - Warning message (stderr)
- None - No action
"""

from typing import Protocol


class BashPostToolHandler(Protocol):
    """Protocol for Bash PostToolUse handlers."""

    def check(self, command: str, context: dict) -> dict | None:
        """
        Check command result and optionally return warning.

        Args:
            command: The bash command that was executed
            context: Dict with tool_input, tool_result, session_id, hook_data

        Returns:
            {"warning": "..."} for warnings, None otherwise
        """
        ...


_HANDLERS: list["BashPostToolHandler"] = []


def register_handler(handler: "BashPostToolHandler") -> None:
    """Register a handler."""
    _HANDLERS.append(handler)


def get_all_handlers() -> list["BashPostToolHandler"]:
    """Get all registered handlers."""
    return _HANDLERS.copy()


# Import handlers to register them
from . import detect_failures
from . import detect_concatenated_commit
from . import validate_rebase_target
from . import verify_commit_type
