#!/usr/bin/env python3
"""
get-issue-complete-box.py - Generate Issue Complete box with actual values.

Usage:
  get-issue-complete-box.py --issue-name NAME --next-issue NAME --next-goal GOAL [--base-branch BRANCH]

Outputs a fully-rendered Issue Complete box with correct alignment.
"""

import argparse
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


def build_issue_complete_box(issue_name: str, next_issue: str, next_goal: str, base_branch: str = "main") -> str:
    """Build Issue Complete box with actual values."""
    header = "✓ Issue Complete"

    content = [
        "",
        f"**{issue_name}** merged to {base_branch}.",
        "",
    ]

    sep = [
        f"**Next:** {next_issue}",
        next_goal,
        "",
        "Auto-continuing in 3s...",
        '• Type "stop" to pause after this issue',
        '• Type "abort" to cancel immediately',
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


def main():
    parser = argparse.ArgumentParser(description="Generate Issue Complete box")
    parser.add_argument("--issue-name", required=True, help="Name of completed issue")
    parser.add_argument("--next-issue", required=True, help="Name of next issue")
    parser.add_argument("--next-goal", required=True, help="Goal of next issue")
    parser.add_argument("--base-branch", default="main", help="Base branch merged to")

    args = parser.parse_args()

    box = build_issue_complete_box(
        issue_name=args.issue_name,
        next_issue=args.next_issue,
        next_goal=args.next_goal,
        base_branch=args.base_branch
    )

    print(box)


if __name__ == "__main__":
    main()
