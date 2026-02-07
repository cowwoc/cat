"""
Handler for /cat:work precomputation.

Provides a generic preparing banner (no issue_id) and status boxes for the work skill.
"""

import json
import subprocess
from pathlib import Path

from . import register_handler
from .status_handler import display_width, build_line, build_border


def build_separator(max_width: int) -> str:
    """Build a horizontal separator line (├───┤)."""
    dash_count = max_width + 2
    dashes = "─" * dash_count
    return "├" + dashes + "┤"


def build_header_top(header: str, max_width: int) -> str:
    """Build top border with embedded header (╭─── Header ───╮)."""
    inner_width = max_width + 2
    header_width = display_width(header)
    prefix_dashes = "─── "  # 4 chars
    suffix_dashes_count = inner_width - 4 - header_width - 1
    if suffix_dashes_count < 1:
        suffix_dashes_count = 1
    suffix_dashes = "─" * suffix_dashes_count
    return "╭" + prefix_dashes + header + " " + suffix_dashes + "╮"


def build_simple_box(icon: str, title: str, content_lines: list[str]) -> str:
    """Build a simple box with icon prefix header (╭─ icon title ...)."""
    prefix = f"─ {icon} {title}"
    # Calculate max width from content
    content_widths = [display_width(c) for c in content_lines]
    prefix_width = display_width(prefix)
    max_content = max(content_widths) if content_widths else 0
    inner_width = max(max_content, prefix_width) + 2

    lines = []
    # Top border with embedded prefix
    suffix_dashes = "─" * (inner_width - prefix_width)
    lines.append("╭" + prefix + suffix_dashes + "╮")
    # Content lines
    for content in content_lines:
        padding = inner_width - display_width(content)
        lines.append("│ " + content + " " * (padding - 1) + "│")
    # Bottom border
    lines.append("╰" + "─" * inner_width + "╯")
    return "\n".join(lines)


class WorkHandler:
    """Handler for /cat:work skill."""

    def _build_no_executable_tasks(self) -> str:
        """Build No executable issues info box."""
        return build_simple_box(
            "ℹ️",
            "No executable issues",
            [
                "",
                "Run /cat:status to see available issues",
            ]
        )

    def _build_task_not_found(self) -> str:
        """Build Issue not found box with suggestion placeholder."""
        return build_simple_box(
            "❓",
            "Issue \"{issue-name}\" not found",
            [
                "",
                "Did you mean: {suggestion}?",
                "Run /cat:status to see all issues",
            ]
        )

    def _build_fork_in_the_road(self) -> str:
        """Build Fork in the road wizard box."""
        content_lines = [
            "   Issue: {issue-name}",
            "",
            "   Multiple viable paths - how would you prefer to proceed?",
            "",
            "   CHOOSE YOUR PATH",
            "   ─────────────────────────────────────────────────────────────────",
            "",
            "   [A] {approach-name}",
            "       {description}",
            "       Risk: {level} | Scope: {N} files | Config alignment: {N}%",
            "",
            "   [B] {approach-name}",
            "       {description}",
            "       Risk: {level} | Scope: {N} files | Config alignment: {N}%",
            "",
            "   [C] {approach-name} (if exists)",
            "       ...",
            "",
        ]
        # Calculate max width
        content_widths = [display_width(c) for c in content_lines]
        max_width = max(content_widths) if content_widths else 60

        lines = []
        lines.append("🔀 FORK IN THE ROAD")
        lines.append("╭" + "─" * (max_width + 2) + "╮")
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append("╰" + "─" * (max_width + 2) + "╯")
        return "\n".join(lines)

    def _build_checkpoint_task_complete(self) -> str:
        """Build Checkpoint: Issue Complete box with metrics."""
        content_lines = [
            "",
            "**Issue:** {issue-name}",
            "",
        ]
        metrics_lines = [
            "**Time:** {N} minutes | **Tokens:** {N} ({percentage}% of context)",
        ]
        branch_lines = [
            "**Branch:** {issue-branch}",
            "",
        ]

        all_content = content_lines + metrics_lines + branch_lines
        content_widths = [display_width(c) for c in all_content]
        header = "✅ **CHECKPOINT: Issue Complete**"
        header_width = display_width(header)
        max_width = max(max(content_widths) if content_widths else 0, header_width)

        lines = []
        lines.append(header)
        lines.append("╭" + "─" * (max_width + 2) + "╮")
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append("├" + "─" * (max_width + 2) + "┤")
        for content in metrics_lines:
            lines.append(build_line(content, max_width))
        lines.append("├" + "─" * (max_width + 2) + "┤")
        for content in branch_lines:
            lines.append(build_line(content, max_width))
        lines.append("╰" + "─" * (max_width + 2) + "╯")
        return "\n".join(lines)

    def _build_checkpoint_feedback_applied(self) -> str:
        """Build Checkpoint: Feedback Applied box."""
        content_lines = [
            "",
            "**Issue:** {issue-name}",
            "**Feedback iteration:** {N}",
            "",
        ]
        metrics_lines = [
            "**Feedback subagent:** {N}K tokens",
            "**Total tokens (all iterations):** {total}K",
        ]
        branch_lines = [
            "**Branch:** {issue-branch}",
            "",
        ]

        all_content = content_lines + metrics_lines + branch_lines
        content_widths = [display_width(c) for c in all_content]
        header = "✅ **CHECKPOINT: Feedback Applied**"
        header_width = display_width(header)
        max_width = max(max(content_widths) if content_widths else 0, header_width)

        lines = []
        lines.append(header)
        lines.append("╭" + "─" * (max_width + 2) + "╮")
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append("├" + "─" * (max_width + 2) + "┤")
        for content in metrics_lines:
            lines.append(build_line(content, max_width))
        lines.append("├" + "─" * (max_width + 2) + "┤")
        for content in branch_lines:
            lines.append(build_line(content, max_width))
        lines.append("╰" + "─" * (max_width + 2) + "╯")
        return "\n".join(lines)

    def _build_task_complete_with_next(self) -> str:
        """Build Issue Complete box for auto-continue mode (trust >= medium)."""
        header = "✓ Issue Complete"

        content_lines = [
            "",
            "**{issue-name}** merged to main.",
            "",
        ]

        separator_content = [
            "**Next:** {next-issue-name}",
            "{goal from PLAN.md}",
            "",
            "Continuing to next issue...",
            "• Type \"stop\" to pause after this issue",
            "• Type \"abort\" to cancel immediately",
        ]

        footer_content = [""]

        # Collect all content to calculate max width
        all_content = content_lines + separator_content + footer_content
        content_widths = [display_width(c) for c in all_content]
        header_width = display_width(header) + 5  # Account for "╭─── header ───╮" format
        max_width = max(max(content_widths) if content_widths else 0, header_width)

        lines = []
        lines.append(build_header_top(header, max_width))
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in separator_content:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in footer_content:
            lines.append(build_line(content, max_width))
        lines.append(build_border(max_width, is_top=False))

        return "\n".join(lines)

    def _build_task_already_complete(self) -> str:
        """Build Issue Already Complete box for issues found already implemented."""
        header = "✓ Issue Already Complete"

        content_lines = [
            "",
            "**{issue-name}** was already implemented.",
            "Commit: {commit-hash}",
            "",
            "STATE.md updated to reflect completion.",
            "",
        ]

        separator_content = [
            "**Next:** {next-issue-name}",
            "{goal from PLAN.md}",
            "",
            "Continuing to next issue...",
            "• Type \"stop\" to pause after this issue",
            "• Type \"abort\" to cancel immediately",
        ]

        footer_content = [""]

        # Collect all content to calculate max width
        all_content = content_lines + separator_content + footer_content
        content_widths = [display_width(c) for c in all_content]
        header_width = display_width(header) + 5  # Account for "╭─── header ───╮" format
        max_width = max(max(content_widths) if content_widths else 0, header_width)

        lines = []
        lines.append(build_header_top(header, max_width))
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in separator_content:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in footer_content:
            lines.append(build_line(content, max_width))
        lines.append(build_border(max_width, is_top=False))

        return "\n".join(lines)

    def _build_scope_complete(self) -> str:
        """Build Scope Complete box."""
        header = "✓ Scope Complete"

        content_lines = [
            "",
            "**{scope description}** - all issues complete!",
            "",
            "{For minor: \"v0.5 complete\"}",
            "{For major: \"v0.x complete\"}",
            "{For all: \"All versions complete!\"}",
            "",
        ]

        # Calculate max width from content
        content_widths = [display_width(c) for c in content_lines]
        header_width = display_width(header) + 5  # Account for "╭─── header ───╮" format
        max_width = max(max(content_widths) if content_widths else 0, header_width)

        lines = []
        lines.append(build_header_top(header, max_width))
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append(build_border(max_width, is_top=False))

        return "\n".join(lines)

    def _build_task_complete_low_trust(self) -> str:
        """Build Issue Complete box for low trust (user must invoke)."""
        header = "✓ Issue Complete"

        content_lines = [
            "",
            "**{issue-name}** merged to main.",
            "",
        ]

        separator_content = [
            "**Next Up:** {next-issue-name}",
            "{goal from PLAN.md}",
            "",
            "`/clear` then `/cat:work` to continue",
        ]

        footer_content = [""]

        # Collect all content to calculate max width
        all_content = content_lines + separator_content + footer_content
        content_widths = [display_width(c) for c in all_content]
        header_width = display_width(header) + 5  # Account for "╭─── header ───╮" format
        max_width = max(max(content_widths) if content_widths else 0, header_width)

        lines = []
        lines.append(build_header_top(header, max_width))
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in separator_content:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in footer_content:
            lines.append(build_line(content, max_width))
        lines.append(build_border(max_width, is_top=False))

        return "\n".join(lines)

    def _build_version_boundary_gate(self) -> str:
        """Build Version Boundary Gate box for approval when crossing versions."""
        header = "✓ Version Complete"

        content_lines = [
            "",
            "**v{current-version}** is now complete!",
            "",
        ]

        summary_content = [
            "**Summary:**",
            "• Issues completed: {count}",
            "",
            "**Before continuing, consider:**",
            "• Publishing/releasing this version",
            "• Tagging the release in git",
            "• Updating documentation",
            "",
        ]

        next_version_content = [
            "**Next Version:** v{next-version}",
            "{next-issue-name}",
            "",
        ]

        # Collect all content to calculate max width
        all_content = content_lines + summary_content + next_version_content
        content_widths = [display_width(c) for c in all_content]
        header_width = display_width(header) + 5  # Account for "╭─── header ───╮" format
        max_width = max(max(content_widths) if content_widths else 0, header_width)

        lines = []
        lines.append(build_header_top(header, max_width))
        for content in content_lines:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in summary_content:
            lines.append(build_line(content, max_width))
        lines.append(build_separator(max_width))
        for content in next_version_content:
            lines.append(build_line(content, max_width))
        lines.append(build_border(max_width, is_top=False))

        return "\n".join(lines)

    def _read_config(self, context: dict) -> dict:
        """Read cat-config.json and return config values with defaults."""
        # Get project directory from context or use cwd
        project_dir = context.get("project_root", Path.cwd())
        config_path = Path(project_dir) / ".claude" / "cat" / "cat-config.json"

        # Default values
        config = {
            "trust": "medium",
            "verify": "changed",
            "autoRemoveWorktrees": True
        }

        # Read config file if it exists
        if config_path.exists():
            try:
                with open(config_path, 'r') as f:
                    file_config = json.load(f)
                    # Update defaults with file values
                    if "trust" in file_config:
                        config["trust"] = file_config["trust"]
                    if "verify" in file_config:
                        config["verify"] = file_config["verify"]
                    if "autoRemoveWorktrees" in file_config:
                        config["autoRemoveWorktrees"] = file_config["autoRemoveWorktrees"]
            except (json.JSONDecodeError, IOError):
                # Use defaults if config file is invalid
                pass

        return config

    def _generate_preparing_banner(self, context: dict) -> str:
        """Generate a generic preparing banner (no issue_id known yet)."""
        plugin_root = context.get("plugin_root", "")
        banner_script = Path(plugin_root) / "scripts" / "get-progress-banner.sh" if plugin_root else None

        if banner_script and banner_script.exists():
            try:
                result = subprocess.run(
                    [str(banner_script), "", "--phase", "preparing"],
                    capture_output=True,
                    text=True,
                    timeout=30
                )
                if result.returncode == 0:
                    return result.stdout.strip()
            except Exception:
                pass
        return ""

    def handle(self, context: dict) -> str | None:
        """Provide preparing banner and status boxes for the work skill."""
        # Read configuration
        config = self._read_config(context)
        trust = config["trust"]
        verify = config["verify"]
        auto_remove = str(config["autoRemoveWorktrees"]).lower()

        # Generate preparing banner (generic, no issue_id yet)
        banner = self._generate_preparing_banner(context)
        banner_section = f"SCRIPT OUTPUT PROGRESS BANNERS:\n\n{banner}" if banner else ""

        # Build output with banner, config section, and boxes
        return f"""{banner_section}

CONFIGURATION:
TRUST={trust}
VERIFY={verify}
AUTO_REMOVE={auto_remove}

SCRIPT OUTPUT WORK BOXES:

## Status Boxes (output EXACTLY as shown, replace only {{placeholders}}):

--- NO_EXECUTABLE_TASKS ---
{self._build_no_executable_tasks()}

--- TASK_NOT_FOUND ---
{self._build_task_not_found()}

--- FORK_IN_THE_ROAD ---
{self._build_fork_in_the_road()}

--- CHECKPOINT_TASK_COMPLETE ---
{self._build_checkpoint_task_complete()}

--- CHECKPOINT_FEEDBACK_APPLIED ---
{self._build_checkpoint_feedback_applied()}

--- TASK_COMPLETE_WITH_NEXT_TASK ---
{self._build_task_complete_with_next()}

--- TASK_ALREADY_COMPLETE ---
{self._build_task_already_complete()}

--- SCOPE_COMPLETE ---
{self._build_scope_complete()}

--- TASK_COMPLETE_LOW_TRUST ---
{self._build_task_complete_low_trust()}

--- VERSION_BOUNDARY_GATE ---
{self._build_version_boundary_gate()}"""


# Register handler
_handler = WorkHandler()
register_handler("work", _handler)
