"""
Handler for /cat:add precomputation.

Generates box displays for task and version creation completion.
"""

from . import register_handler
from .status_handler import display_width, build_line, build_border


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

        # Calculate max width
        header = "\u2705 Task Created"
        header_width = display_width(header) + 3  # "─ " prefix and " ─" suffix area
        content_widths = [display_width(c) for c in content_items]
        max_content_width = max(content_widths) if content_widths else 0
        box_width = max(header_width, max_content_width, 40)

        # Build the box with header
        header_dashes = box_width - display_width(header) - 1
        if header_dashes < 0:
            header_dashes = 0
        top_line = "\u256d\u2500 " + header + " " + "\u2500" * header_dashes + "\u256e"

        lines = [build_line(c, box_width) for c in content_items]
        bottom = build_border(box_width, is_top=False)

        # Build final box
        box_lines = [top_line] + lines + [bottom]
        final_box = "\n".join(box_lines)

        next_cmd = f"/cat:work {version}-{item_name}"

        return f"""OUTPUT TEMPLATE ADD DISPLAY (copy exactly):

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

        # Calculate max width
        header = "\u2705 Version Created"
        header_width = display_width(header) + 3
        content_widths = [display_width(c) for c in content_items]
        max_content_width = max(content_widths) if content_widths else 0
        box_width = max(header_width, max_content_width, 40)

        # Build the box with header
        header_dashes = box_width - display_width(header) - 1
        if header_dashes < 0:
            header_dashes = 0
        top_line = "\u256d\u2500 " + header + " " + "\u2500" * header_dashes + "\u256e"

        lines = [build_line(c, box_width) for c in content_items]
        bottom = build_border(box_width, is_top=False)

        # Build final box
        box_lines = [top_line] + lines + [bottom]
        final_box = "\n".join(box_lines)

        return f"""OUTPUT TEMPLATE ADD DISPLAY (copy exactly):

{final_box}

Next: /clear, then /cat:add (to add tasks)

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""


# Register handler
_handler = AddHandler()
register_handler("add", _handler)
