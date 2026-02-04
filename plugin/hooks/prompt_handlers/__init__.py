"""
Prompt handlers for UserPromptSubmit pattern checking.

These handlers analyze user prompts for patterns that require
injected context (reminders, warnings, etc.) regardless of
whether the prompt is a skill command.
"""

from typing import Protocol, runtime_checkable

_HANDLERS: list["PromptHandler"] = []


@runtime_checkable
class PromptHandler(Protocol):
    """Protocol for prompt pattern handlers."""

    def check(self, prompt: str, session_id: str) -> str | None:
        """
        Check prompt for patterns and return context to inject.

        Args:
            prompt: The user's prompt text
            session_id: Current session ID

        Returns:
            String to inject as context, or None if no match.
        """
        ...


def register_handler(handler: "PromptHandler") -> None:
    """Register a prompt handler."""
    _HANDLERS.append(handler)


def get_all_handlers() -> list["PromptHandler"]:
    """Get all registered handlers."""
    return _HANDLERS.copy()


# Import handlers to register them
from . import critical_thinking
from . import destructive_ops
from . import user_issues
from . import abort_clarification
