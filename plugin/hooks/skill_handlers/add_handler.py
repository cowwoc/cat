"""
Handler for /cat:add precomputation.

Generates box displays for task and version creation completion.
"""

from . import register_handler
from .status_handler import build_header_box


class AddHandler:
    """Handler for /cat:add skill."""

    def handle(self, context: dict) -> str | None:
        """
        Generate output template display for add completion.

        Context keys:
            - item_type: "task" or "version"
            - item_name: Name of the created item
            - version: Version string (e.g., "2.0" for task, "2.1" for version)
            - task_type: Type of task (Feature, Bugfix, etc.) - for tasks only
            - dependencies: List of dependencies - for tasks only
            - version_type: Type of version (major, minor, patch) - for versions only
            - parent_info: Parent version info - for versions only
            - path: Filesystem path to the created item
        """
        item_type = context.get("item_type")

        if not item_type:
            return None

        if item_type == "task":
            return self._build_task_display(context)
        elif item_type == "version":
            return self._build_version_display(context)

        return None

    def _build_task_display(self, context: dict) -> str:
        """Build display box for task creation."""
        item_name = context.get("item_name", "unknown-task")
        version = context.get("version", "0.0")
        task_type = context.get("task_type", "Feature")
        dependencies = context.get("dependencies", [])

        deps_str = ", ".join(dependencies) if dependencies else "None"

        # Build content items
        content_items = [
            item_name,
            "",
            f"Version: {version}",
            f"Type: {task_type}",
            f"Dependencies: {deps_str}",
        ]

        header = "\u2705 Task Created"
        final_box = build_header_box(header, content_items, min_width=40, prefix="─ ")

        next_cmd = f"/cat:work {version}-{item_name}"

        return f"""SCRIPT OUTPUT ADD DISPLAY::

{final_box}

Next: /clear, then {next_cmd}

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""

    def _build_version_display(self, context: dict) -> str:
        """Build display box for version creation."""
        item_name = context.get("item_name", "New Version")
        version = context.get("version", "0.0")
        version_type = context.get("version_type", "minor")
        parent_info = context.get("parent_info", "")
        path = context.get("path", "")

        # Build content items
        content_items = [
            f"v{version}: {item_name}",
            "",
        ]

        if parent_info:
            content_items.append(f"Parent: {parent_info}")
        if path:
            content_items.append(f"Path: {path}")

        header = "\u2705 Version Created"
        final_box = build_header_box(header, content_items, min_width=40, prefix="─ ")

        return f"""SCRIPT OUTPUT ADD DISPLAY::

{final_box}

Next: /clear, then /cat:add (to add tasks)

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""


# Register handler
_handler = AddHandler()
register_handler("add", _handler)
