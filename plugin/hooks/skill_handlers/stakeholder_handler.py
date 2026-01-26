"""
Handler for /cat:stakeholder-review precomputation.

Provides box templates for stakeholder selection and review output.
"""

from . import register_handler
from .status_handler import display_width, build_line, build_border


def build_header_box(header: str, content_lines: list[str], separator_indices: list[int] = None) -> str:
    """Build a box with header and optional separators."""
    if separator_indices is None:
        separator_indices = []

    # Calculate max width
    content_widths = [display_width(c) for c in content_lines]
    header_width = display_width(header) + 5  # Account for "╭─── header ───╮"
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    # Build header
    prefix = "─── "
    suffix_dashes = "─" * (max_width - len(prefix) - display_width(header) + 1)
    if len(suffix_dashes) < 1:
        suffix_dashes = "─"
    top = "╭" + prefix + header + " " + suffix_dashes + "╮"

    lines = [top]
    for i, content in enumerate(content_lines):
        if i in separator_indices:
            lines.append("├" + "─" * (max_width + 2) + "┤")
        lines.append(build_line(content, max_width))

    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


def build_concern_box(severity: str, concerns: list[str]) -> str:
    """Build a concern box with square corners (different from main boxes)."""
    header = severity
    content_widths = [display_width(c) for c in concerns]
    header_width = display_width(header) + 4
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    # Square corner box
    top = "┌─ " + header + " " + "─" * (max_width - display_width(header) - 1) + "┐"
    lines = [top]
    for content in concerns:
        padding = max_width - display_width(content)
        lines.append("│ " + content + " " * padding + " │")
    lines.append("└" + "─" * (max_width + 2) + "┘")
    return "\n".join(lines)


class StakeholderHandler:
    """Handler for /cat:stakeholder-review skill."""

    def _build_selection_box(self) -> str:
        """Build STAKEHOLDER SELECTION box template."""
        return build_header_box(
            "STAKEHOLDER SELECTION",
            [
                "",
                "Stakeholder Review: {N} of 10 stakeholders selected",
                "",
                "Running: {running-list}",
                "",
                "Skipped:",
                "  - {stakeholder1}: {reason1}",
                "  - {stakeholder2}: {reason2}",
                "",
            ]
        )

    def _build_review_box(self) -> str:
        """Build STAKEHOLDER REVIEW summary box template."""
        content = [
            "",
            "Task: {task-name}",
            "",
        ]
        separator1 = len(content)
        content.extend([
            "Spawning reviewers...",
            "├── {stakeholder1} {status1}",
            "├── {stakeholder2} {status2}",
            "└── {stakeholderN} {statusN}",
        ])
        separator2 = len(content)
        content.extend([
            "Result: {APPROVED|CONCERNS|REJECTED} ({summary})",
            "",
        ])
        return build_header_box("STAKEHOLDER REVIEW", content, [separator1, separator2])

    def _build_critical_concern_box(self) -> str:
        """Build CRITICAL concern box template."""
        return build_concern_box("CRITICAL", [
            "[{Stakeholder}] {concern-description}",
            "└─ {file-location}",
            "",
        ])

    def _build_high_concern_box(self) -> str:
        """Build HIGH concern box template."""
        return build_concern_box("HIGH", [
            "[{Stakeholder}] {concern-description}",
            "└─ {file-location}",
            "",
        ])

    def handle(self, context: dict) -> str | None:
        """Provide box templates for stakeholder review."""
        return f"""OUTPUT TEMPLATE STAKEHOLDER BOXES - LITERAL COPY-PASTE REQUIRED:

**CRITICAL**: Copy-paste the EXACT boxes below. Do NOT reconstruct or retype them.

--- STAKEHOLDER_SELECTION ---
{self._build_selection_box()}

--- STAKEHOLDER_REVIEW ---
{self._build_review_box()}

--- CRITICAL_CONCERN ---
{self._build_critical_concern_box()}

--- HIGH_CONCERN ---
{self._build_high_concern_box()}

INSTRUCTION: Copy-paste box structures VERBATIM, then replace ONLY placeholder text inside.
Repeat reviewer lines and concern boxes as needed for actual data."""


# Register handler
_handler = StakeholderHandler()
register_handler("stakeholder-review", _handler)
