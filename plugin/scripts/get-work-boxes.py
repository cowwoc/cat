#!/usr/bin/env python3
"""
get-work-boxes.py - Generate work skill box templates.

Outputs pre-rendered box templates for /cat:work skill.
Designed to be called via !` preprocessing in SKILL.md.
"""

import sys
from pathlib import Path

# Add lib directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR / "lib"))

from emoji_widths import display_width, get_emoji_widths

_ew = None


def get_ew():
    global _ew
    if _ew is None:
        _ew = get_emoji_widths()
    return _ew


def dw(s):
    """Calculate display width of string."""
    return display_width(s, get_ew())


def build_line(content: str, max_width: int) -> str:
    """Build a padded content line."""
    padding = max_width - dw(content)
    return "│ " + content + " " * padding + " │"


def build_border(max_width: int, is_top: bool) -> str:
    """Build top or bottom border."""
    dashes = "─" * (max_width + 2)
    if is_top:
        return "╭" + dashes + "╮"
    return "╰" + dashes + "╯"


def build_separator(max_width: int) -> str:
    """Build horizontal separator."""
    return "├" + "─" * (max_width + 2) + "┤"


def build_header_top(header: str, max_width: int) -> str:
    """Build top border with embedded header."""
    inner_width = max_width + 2
    header_width = dw(header)
    prefix_dashes = "─── "
    suffix_dashes_count = inner_width - 4 - header_width - 1
    if suffix_dashes_count < 1:
        suffix_dashes_count = 1
    suffix_dashes = "─" * suffix_dashes_count
    return "╭" + prefix_dashes + header + " " + suffix_dashes + "╮"


def build_simple_box(icon: str, title: str, content_lines: list) -> str:
    """Build a simple box with icon prefix header."""
    prefix = f"─ {icon} {title}"
    content_widths = [dw(c) for c in content_lines]
    prefix_width = dw(prefix)
    max_content = max(content_widths) if content_widths else 0
    inner_width = max(max_content, prefix_width) + 2

    lines = []
    suffix_dashes = "─" * (inner_width - prefix_width)
    lines.append("╭" + prefix + suffix_dashes + "╮")
    for content in content_lines:
        padding = inner_width - dw(content)
        lines.append("│ " + content + " " * (padding - 1) + "│")
    lines.append("╰" + "─" * inner_width + "╯")
    return "\n".join(lines)


def build_no_executable_issues():
    return build_simple_box(
        "ℹ️", "No executable issues",
        ["", "Run /cat:status to see available issues"]
    )


def build_issue_not_found():
    return build_simple_box(
        "❓", 'Issue "{issue-name}" not found',
        ["", "Did you mean: {suggestion}?", "Run /cat:status to see all issues"]
    )


def build_fork_in_the_road():
    content_lines = [
        "   Issue: {issue-name}",
        "",
        "   Multiple viable paths - how would you prefer to proceed?",
        "",
        "   CHOOSE YOUR PATH",
        "   " + "─" * 65,
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
    content_widths = [dw(c) for c in content_lines]
    max_width = max(content_widths) if content_widths else 60

    lines = ["🔀 FORK IN THE ROAD", "╭" + "─" * (max_width + 2) + "╮"]
    for content in content_lines:
        lines.append(build_line(content, max_width))
    lines.append("╰" + "─" * (max_width + 2) + "╯")
    return "\n".join(lines)


def build_checkpoint_box(header: str, main_content: list, metrics: list, branch: list):
    all_content = main_content + metrics + branch
    content_widths = [dw(c) for c in all_content]
    header_width = dw(header)
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [header, "╭" + "─" * (max_width + 2) + "╮"]
    for c in main_content:
        lines.append(build_line(c, max_width))
    lines.append("├" + "─" * (max_width + 2) + "┤")
    for c in metrics:
        lines.append(build_line(c, max_width))
    lines.append("├" + "─" * (max_width + 2) + "┤")
    for c in branch:
        lines.append(build_line(c, max_width))
    lines.append("╰" + "─" * (max_width + 2) + "╯")
    return "\n".join(lines)


def build_checkpoint_issue_complete():
    return build_checkpoint_box(
        "✅ **CHECKPOINT: Issue Complete**",
        ["", "**Issue:** {issue-name}", ""],
        ["**Time:** {N} minutes | **Tokens:** {N} ({percentage}% of context)"],
        ["**Branch:** {issue-branch}", ""]
    )


def build_checkpoint_feedback_applied():
    return build_checkpoint_box(
        "✅ **CHECKPOINT: Feedback Applied**",
        ["", "**Issue:** {issue-name}", "**Feedback iteration:** {N}", ""],
        ["**Feedback subagent:** {N}K tokens", "**Total tokens (all iterations):** {total}K"],
        ["**Branch:** {issue-branch}", ""]
    )


def build_issue_complete_with_next():
    header = "✓ Issue Complete"
    content = ["", "**{issue-name}** merged to main.", ""]
    sep = [
        "**Next:** {next-issue-name}", "{goal from PLAN.md}", "",
        "Auto-continuing in 3s...",
        '• Type "stop" to pause after this issue',
        '• Type "abort" to cancel immediately'
    ]
    footer = [""]

    all_content = content + sep + footer
    content_widths = [dw(c) for c in all_content]
    header_width = dw(header) + 5
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [build_header_top(header, max_width)]
    for c in content:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in sep:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in footer:
        lines.append(build_line(c, max_width))
    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


def build_issue_already_complete():
    header = "✓ Issue Already Complete"
    content = ["", "**{issue-name}** was already implemented.", "Commit: {commit-hash}", "", "STATE.md updated to reflect completion.", ""]
    sep = [
        "**Next:** {next-issue-name}", "{goal from PLAN.md}", "",
        "Auto-continuing in 3s...",
        '• Type "stop" to pause after this issue',
        '• Type "abort" to cancel immediately'
    ]
    footer = [""]

    all_content = content + sep + footer
    content_widths = [dw(c) for c in all_content]
    header_width = dw(header) + 5
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [build_header_top(header, max_width)]
    for c in content:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in sep:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in footer:
        lines.append(build_line(c, max_width))
    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


def build_scope_complete():
    header = "✓ Scope Complete"
    content = [
        "", "**{scope description}** - all tasks complete!", "",
        '{For minor: "v0.5 complete"}',
        '{For major: "v0.x complete"}',
        '{For all: "All versions complete!"}', ""
    ]

    content_widths = [dw(c) for c in content]
    header_width = dw(header) + 5
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [build_header_top(header, max_width)]
    for c in content:
        lines.append(build_line(c, max_width))
    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


def build_issue_complete_low_trust():
    header = "✓ Issue Complete"
    content = ["", "**{issue-name}** merged to main.", ""]
    sep = [
        "**Next Up:** {next-issue-name}", "{goal from PLAN.md}", "",
        "`/clear` then `/cat:work` to continue"
    ]
    footer = [""]

    all_content = content + sep + footer
    content_widths = [dw(c) for c in all_content]
    header_width = dw(header) + 5
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [build_header_top(header, max_width)]
    for c in content:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in sep:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in footer:
        lines.append(build_line(c, max_width))
    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


def build_version_boundary_gate():
    header = "✓ Version Complete"
    content = ["", "**v{current-version}** is now complete!", ""]
    summary = [
        "**Summary:**", "• Tasks completed: {count}", "",
        "**Before continuing, consider:**",
        "• Publishing/releasing this version",
        "• Tagging the release in git",
        "• Updating documentation", ""
    ]
    next_ver = ["**Next Version:** v{next-version}", "{next-task-name}", ""]

    all_content = content + summary + next_ver
    content_widths = [dw(c) for c in all_content]
    header_width = dw(header) + 5
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [build_header_top(header, max_width)]
    for c in content:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in summary:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in next_ver:
        lines.append(build_line(c, max_width))
    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


def main():
    print("""## Pre-rendered Work Boxes

Output EXACTLY as shown, replace only {placeholders}:

### NO_EXECUTABLE_ISSUES
""")
    print(build_no_executable_issues())
    print("""
### ISSUE_NOT_FOUND
""")
    print(build_issue_not_found())
    print("""
### FORK_IN_THE_ROAD
""")
    print(build_fork_in_the_road())
    print("""
### CHECKPOINT_ISSUE_COMPLETE
""")
    print(build_checkpoint_issue_complete())
    print("""
### CHECKPOINT_FEEDBACK_APPLIED
""")
    print(build_checkpoint_feedback_applied())
    print("""
### ISSUE_COMPLETE_WITH_NEXT_ISSUE
""")
    print(build_issue_complete_with_next())
    print("""
### ISSUE_ALREADY_COMPLETE
""")
    print(build_issue_already_complete())
    print("""
### SCOPE_COMPLETE
""")
    print(build_scope_complete())
    print("""
### ISSUE_COMPLETE_LOW_TRUST
""")
    print(build_issue_complete_low_trust())
    print("""
### VERSION_BOUNDARY_GATE
""")
    print(build_version_boundary_gate())


if __name__ == "__main__":
    main()
