"""
General PostToolUse handlers module (runs for ALL tools).

Each handler implements a check() method that receives:
- tool_name: str - The tool that was executed
- tool_result: dict - The tool's result
- context: dict - Contains session_id, hook_data

Handlers can return:
- {"warning": "...", "additionalContext": "..."} - Warning with context injection
- None - No action
"""

from typing import Protocol


class PostToolHandler(Protocol):
    """Protocol for general PostToolUse handlers."""

    def check(self, tool_name: str, tool_result: dict, context: dict) -> dict | None:
        """
        Check tool result and optionally return warning/context.

        Args:
            tool_name: The tool that was executed
            tool_result: The tool's result dict
            context: Dict with session_id, hook_data

        Returns:
            {"warning": "...", "additionalContext": "..."} for warnings, None otherwise
        """
        ...


_HANDLERS: list["PostToolHandler"] = []


def register_handler(handler: "PostToolHandler") -> None:
    """Register a handler."""
    _HANDLERS.append(handler)


def get_all_handlers() -> list["PostToolHandler"]:
    """Get all registered handlers."""
    return _HANDLERS.copy()


# Import handlers to register them
from . import auto_learn_mistakes
from . import skill_precompute
from . import detect_manual_boxes
