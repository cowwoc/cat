"""Read/Glob/Grep/WebFetch/WebSearch PostToolUse handlers module."""

from typing import Protocol


class ReadPostToolHandler(Protocol):
    """Protocol for Read/Glob/Grep/WebFetch/WebSearch PostToolUse handlers."""

    def check(self, tool_name: str, context: dict) -> dict | None:
        """
        Check after a Read/Glob/Grep/WebFetch/WebSearch tool completes.

        Args:
            tool_name: The tool that was called
            context: Dict with tool_input, tool_result, session_id, hook_data

        Returns:
            None to allow silently, or dict with optional keys:
            - "warning": str - Warning message (stderr)
        """
        ...


_HANDLERS: list["ReadPostToolHandler"] = []


def register_handler(handler: "ReadPostToolHandler") -> None:
    """Register a handler to be called for PostToolUse events."""
    _HANDLERS.append(handler)


def get_all_handlers() -> list["ReadPostToolHandler"]:
    """Get all registered handlers."""
    return _HANDLERS.copy()


# Import handlers to trigger registration
from . import detect_sequential_tools
