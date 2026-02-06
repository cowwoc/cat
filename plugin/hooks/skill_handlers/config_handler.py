"""
Handler for /cat:config precomputation.

Runs the get-config-display.sh script and provides all config box templates.
"""

from pathlib import Path

from . import register_handler
from .base import run_script
from .status_handler import display_width, build_line, build_border


def build_simple_header_box(icon: str, title: str, content_lines: list[str]) -> str:
    """Build a box with icon+title header."""
    header = f"{icon} {title}"
    header_width = display_width(header)

    # Calculate max width from content
    content_widths = [display_width(c) for c in content_lines]
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = []
    # Header top with embedded title
    prefix = "─── "
    suffix_dashes = "─" * (max_width - len(prefix) - header_width + 1)
    if len(suffix_dashes) < 1:
        suffix_dashes = "─"
    lines.append("╭" + prefix + header + " " + suffix_dashes + "╮")
    # Content lines
    for content in content_lines:
        lines.append(build_line(content, max_width))
    # Bottom border
    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


class ConfigHandler:
    """Handler for /cat:config skill."""

    def _build_version_gates_overview(self) -> str:
        """Build VERSION GATES overview box."""
        return build_simple_header_box(
            "📊",
            "VERSION GATES",
            [
                "",
                "Entry and exit gates control version dependencies.",
                "",
                "Select a version to configure its gates,",
                "or choose 'Apply defaults to all'.",
            ]
        )

    def _build_gates_for_version(self) -> str:
        """Build GATES FOR {version} box."""
        return build_simple_header_box(
            "🚧",
            "GATES FOR {version}",
            [
                "",
                "Entry: {entry-gate-description}",
                "Exit: {exit-gate-description}",
            ]
        )

    def _build_gates_updated(self) -> str:
        """Build GATES UPDATED confirmation box."""
        return build_simple_header_box(
            "✅",
            "GATES UPDATED",
            [
                "",
                "Version: {version}",
                "Entry: {new-entry-gate}",
                "Exit: {new-exit-gate}",
            ]
        )

    def _build_setting_updated(self) -> str:
        """Build SETTING UPDATED confirmation box."""
        return build_simple_header_box(
            "✅",
            "SETTING UPDATED",
            [
                "",
                "{setting-name}: {old-value} → {new-value}",
            ]
        )

    def _build_configuration_saved(self) -> str:
        """Build CONFIGURATION SAVED confirmation box."""
        return build_simple_header_box(
            "✅",
            "CONFIGURATION SAVED",
            [
                "",
                "Changes committed to cat-config.json",
            ]
        )

    def _build_no_changes(self) -> str:
        """Build NO CHANGES info box."""
        return build_simple_header_box(
            "ℹ️",
            "NO CHANGES",
            [
                "",
                "Configuration unchanged.",
            ]
        )

    def handle(self, context: dict) -> str | None:
        """Run config display script and return result with all box templates."""
        project_root = context.get("project_root")
        plugin_root = context.get("plugin_root")

        if not project_root:
            return None

        config_file = Path(project_root) / ".claude" / "cat" / "cat-config.json"
        if not config_file.exists():
            return None

        display_script = Path(plugin_root) / "scripts" / "get-config-display.sh"
        output = run_script(display_script, project_root)

        if not output:
            return None

        return f"""SCRIPT OUTPUT CONFIG BOXES:

**CRITICAL**: Copy-paste the EXACT boxes below. Do NOT reconstruct or retype them.

--- CURRENT_SETTINGS ---
{output}

--- VERSION_GATES_OVERVIEW ---
{self._build_version_gates_overview()}

--- GATES_FOR_VERSION ---
{self._build_gates_for_version()}

--- GATES_UPDATED ---
{self._build_gates_updated()}

--- SETTING_UPDATED ---
{self._build_setting_updated()}

--- CONFIGURATION_SAVED ---
{self._build_configuration_saved()}

--- NO_CHANGES ---
{self._build_no_changes()}

INSTRUCTION: Copy-paste box structures VERBATIM, then replace ONLY placeholder text inside."""


# Register handler
_handler = ConfigHandler()
register_handler("config", _handler)
