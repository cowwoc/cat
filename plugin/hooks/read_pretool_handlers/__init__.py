"""Read/Glob/Grep PreToolUse handlers module."""

from typing import Protocol


class ReadPreToolHandler(Protocol):
    """Protocol for Read/Glob/Grep PreToolUse handlers."""

    def check(self, tool_name: str, context: dict) -> dict | None:
        """
        Check a Read/Glob/Grep tool call before execution.

        Args:
            tool_name: The tool being called (Read, Glob, or Grep)
            context: Dict with tool_input, session_id, hook_data

        Returns:
            None to allow, or dict with optional keys:
            - "warning": str - Warning message (stderr)
            - "block": bool - If True, block the operation
            - "message": str - Block reason (if blocking)
        """
        ...


_HANDLERS: list["ReadPreToolHandler"] = []


def register_handler(handler: "ReadPreToolHandler") -> None:
    """Register a handler to be called for Read/Glob/Grep PreToolUse events."""
    _HANDLERS.append(handler)


def get_all_handlers() -> list["ReadPreToolHandler"]:
    """Get all registered handlers."""
    return _HANDLERS.copy()


# Import handlers to trigger registration
from . import predict_batch_opportunity
