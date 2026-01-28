"""
Handler for /cat:work precomputation.

Provides progress display format templates for inline rendering.
"""

import re
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
        """Build No executable tasks info box."""
        return build_simple_box(
            "ℹ️",
            "No executable tasks",
            [
                "",
                "Run /cat:status to see available tasks",
            ]
        )

    def _build_task_not_found(self) -> str:
        """Build Task not found box with suggestion placeholder."""
        return build_simple_box(
            "❓",
            "Task \"{task-name}\" not found",
            [
                "",
                "Did you mean: {suggestion}?",
                "Run /cat:status to see all tasks",
            ]
        )

    def _build_fork_in_the_road(self) -> str:
        """Build Fork in the road wizard box."""
        content_lines = [
            "   Task: {task-name}",
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
        """Build Checkpoint: Task Complete box with metrics."""
        content_lines = [
            "",
            "**Task:** {task-name}",
            "",
        ]
        metrics_lines = [
            "**Time:** {N} minutes | **Tokens:** {N} ({percentage}% of context)",
        ]
        branch_lines = [
            "**Branch:** {task-branch}",
            "",
        ]

        all_content = content_lines + metrics_lines + branch_lines
        content_widths = [display_width(c) for c in all_content]
        header = "✅ **CHECKPOINT: Task Complete**"
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
            "**Task:** {task-name}",
            "**Feedback iteration:** {N}",
            "",
        ]
        metrics_lines = [
            "**Feedback subagent:** {N}K tokens",
            "**Total tokens (all iterations):** {total}K",
        ]
        branch_lines = [
            "**Branch:** {task-branch}",
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
        """Build Task Complete box for auto-continue mode (trust >= medium)."""
        header = "✓ Task Complete"

        content_lines = [
            "",
            "**{task-name}** merged to main.",
            "",
        ]

        separator_content = [
            "**Next:** {next-task-name}",
            "{goal from PLAN.md}",
            "",
            "Auto-continuing in 3s...",
            "• Type \"stop\" to pause after this task",
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
        """Build Task Already Complete box for tasks found already implemented."""
        header = "✓ Task Already Complete"

        content_lines = [
            "",
            "**{task-name}** was already implemented.",
            "Commit: {commit-hash}",
            "",
            "STATE.md updated to reflect completion.",
            "",
        ]

        separator_content = [
            "**Next:** {next-task-name}",
            "{goal from PLAN.md}",
            "",
            "Auto-continuing in 3s...",
            "• Type \"stop\" to pause after this task",
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
            "**{scope description}** - all tasks complete!",
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
        """Build Task Complete box for low trust (user must invoke)."""
        header = "✓ Task Complete"

        content_lines = [
            "",
            "**{task-name}** merged to main.",
            "",
        ]

        separator_content = [
            "**Next Up:** {next-task-name}",
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
            "• Tasks completed: {count}",
            "",
            "**Before continuing, consider:**",
            "• Publishing/releasing this version",
            "• Tagging the release in git",
            "• Updating documentation",
            "",
        ]

        next_version_content = [
            "**Next Version:** v{next-version}",
            "{next-task-name}",
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

    def _build_progress_banner(self, task_id: str = "2.1-compress-lang-md",
                                 phases: tuple = ("◉", "○", "○", "○")) -> str:
        """Build progress banner with proper alignment.

        Args:
            task_id: The task ID to display
            phases: Tuple of 4 phase symbols (Preparing, Executing, Reviewing, Merging)
        """
        p1, p2, p3, p4 = phases
        # Phase content without border characters
        phase_content = f"  {p1} Preparing ────── {p2} Executing ────── {p3} Reviewing ────── {p4} Merging "
        phase_width = display_width(phase_content)

        # Header content: "─ 🐱 " + task_id + " "
        header_prefix = "─ 🐱 "
        header_content = header_prefix + task_id + " "
        header_width = display_width(header_content)

        # Box width is determined by the wider of header or phase content
        inner_width = max(header_width, phase_width)

        # Build top border: ┌ + header_content + dashes + ┐
        top_dashes = "─" * (inner_width - header_width)
        top_line = "┌" + header_content + top_dashes + "┐"

        # Build middle line: │ + phase_content + padding + │
        phase_padding = " " * (inner_width - phase_width)
        middle_line = "│" + phase_content + phase_padding + "│"

        # Build bottom border: └ + dashes + ┘
        bottom_line = "└" + "─" * inner_width + "┘"

        return "\n".join([top_line, middle_line, bottom_line])

    def handle(self, context: dict) -> str | None:
        """Provide progress format templates for the work skill."""
        # Build example banners
        example_preparing = self._build_progress_banner("2.1-compress-lang-md", ("◉", "○", "○", "○"))
        example_executing = self._build_progress_banner("2.1-compress-lang-md", ("●", "◉", "○", "○"))

        # Compact progress banner format - no verbose examples
        return f"""OUTPUT TEMPLATE WORK PROGRESS FORMAT:

## Progress Display Templates

### Symbols
○ Pending | ● Complete | ◉ Active | ✗ Failed

### Progress Banner Construction

**Phase content (fixed):** `  {{P1}} Preparing ────── {{P2}} Executing ────── {{P3}} Reviewing ────── {{P4}} Merging `

**Construction steps:**
1. Calculate phase content width (65 display chars with symbols)
2. Calculate header width: `─ 🐱 ` + TASK_ID + ` `
3. Inner width = max(header_width, phase_width)
4. Top: `┌` + header + dashes to fill inner_width + `┐`
5. Middle: `│` + phase_content + padding to fill inner_width + `│`
6. Bottom: `└` + dashes for inner_width + `┘`

### Examples (copy structure, replace task ID and symbols)

**Preparing phase:**
```
{example_preparing}
```

**Executing phase:**
```
{example_executing}
```

Do NOT show progress before task is identified.

OUTPUT TEMPLATE WORK BOXES - LITERAL COPY-PASTE (M225):

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
{self._build_version_boundary_gate()}

Copy box VERBATIM, replace only placeholders."""


# Register handler
_handler = WorkHandler()
register_handler("work", _handler)
