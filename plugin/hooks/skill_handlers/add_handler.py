"""
Handler for /cat:add precomputation.

Generates box displays for issue and version creation completion.
"""

from . import register_handler
from .status_handler import build_header_box


class AddHandler:
    """Handler for /cat:add skill."""

    def handle(self, context: dict) -> str | None:
        """
        Generate output template display for add completion.

        Context keys:
            - item_type: "issue" or "version"
            - item_name: Name of the created item
            - version: Version string (e.g., "2.0" for issue, "2.1" for version)
            - issue_type: Type of issue (Feature, Bugfix, etc.) - for issues only
            - dependencies: List of dependencies - for issues only
            - version_type: Type of version (major, minor, patch) - for versions only
            - parent_info: Parent version info - for versions only
            - path: Filesystem path to the created item
        """
        item_type = context.get("item_type")

        if not item_type:
            return None

        if item_type == "issue":
            return self._build_issue_display(context)
        elif item_type == "version":
            return self._build_version_display(context)

        return None

    def _build_issue_display(self, context: dict) -> str:
        """Build display box for issue creation."""
        item_name = context.get("item_name", "unknown-issue")
        version = context.get("version", "0.0")
        issue_type = context.get("issue_type", "Feature")
        dependencies = context.get("dependencies", [])

        deps_str = ", ".join(dependencies) if dependencies else "None"

        # Build content items
        content_items = [
            item_name,
            "",
            f"Version: {version}",
            f"Type: {issue_type}",
            f"Dependencies: {deps_str}",
        ]

        header = "✅ Issue Created"
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

        header = "✅ Version Created"
        final_box = build_header_box(header, content_items, min_width=40, prefix="─ ")

        return f"""SCRIPT OUTPUT ADD DISPLAY::

{final_box}

Next: /clear, then /cat:add (to add issues)

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""


# Register handler
_handler = AddHandler()
register_handler("add", _handler)
