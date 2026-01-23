"""
Skill handlers for CAT command precomputation.

Each handler is a module that provides a `handle(context)` function.
The dispatcher routes to handlers based on skill name.
"""

from typing import Protocol, runtime_checkable

# Handler registry - maps skill name to handler module
_HANDLERS: dict[str, "SkillHandler"] = {}


@runtime_checkable
class SkillHandler(Protocol):
    """Protocol for skill handlers."""

    def handle(self, context: dict) -> str | None:
        """
        Handle precomputation for a skill.

        Args:
            context: Dict containing:
                - user_prompt: Full user prompt string
                - session_id: Claude session ID
                - project_root: Path to project root (or empty)
                - plugin_root: Path to plugin root
                - hook_data: Raw hook JSON data

        Returns:
            String to inject as additionalContext, or None for no injection.
        """
        ...


def register_handler(skill_name: str, handler: SkillHandler) -> None:
    """Register a handler for a skill name."""
    _HANDLERS[skill_name] = handler


def get_handler(skill_name: str) -> SkillHandler | None:
    """Get the handler for a skill name, or None if not registered."""
    return _HANDLERS.get(skill_name)


# Import and register handlers
# Each handler module registers itself on import
from . import init_handler
from . import config_handler
from . import help_handler
from . import token_report_handler
from . import status_handler
from . import work_handler
from . import cleanup_handler
from . import add_handler
