#!/usr/bin/env python3
"""
get-checkpoint-box.py - Generate Checkpoint boxes with actual values.

Usage:
  get-checkpoint-box.py --type issue-complete --issue-name NAME --tokens N --percent N --branch BRANCH
  get-checkpoint-box.py --type feedback-applied --issue-name NAME --iteration N --tokens N --total N --branch BRANCH

Outputs a fully-rendered Checkpoint box with correct alignment.
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


def build_checkpoint_issue_complete(issue_name: str, tokens: str, percent: str, branch: str) -> str:
    """Build CHECKPOINT: Issue Complete box with actual values."""
    header = "✅ **CHECKPOINT: Issue Complete**"

    main_content = ["", f"**Issue:** {issue_name}", ""]
    metrics = [f"**Tokens:** {tokens} ({percent}% of context)"]
    branch_content = [f"**Branch:** {branch}", ""]

    all_content = main_content + metrics + branch_content
    content_widths = [dw(c) for c in all_content]
    header_width = dw(header)
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [header, build_border(max_width, True)]
    for c in main_content:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in metrics:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in branch_content:
        lines.append(build_line(c, max_width))
    lines.append(build_border(max_width, False))

    return "\n".join(lines)


def build_checkpoint_feedback_applied(issue_name: str, iteration: str, tokens: str, total: str, branch: str) -> str:
    """Build CHECKPOINT: Feedback Applied box with actual values."""
    header = "✅ **CHECKPOINT: Feedback Applied**"

    main_content = ["", f"**Issue:** {issue_name}", f"**Feedback iteration:** {iteration}", ""]
    metrics = [f"**Feedback subagent:** {tokens}K tokens", f"**Total tokens (all iterations):** {total}K"]
    branch_content = [f"**Branch:** {branch}", ""]

    all_content = main_content + metrics + branch_content
    content_widths = [dw(c) for c in all_content]
    header_width = dw(header)
    max_width = max(max(content_widths) if content_widths else 0, header_width)

    lines = [header, build_border(max_width, True)]
    for c in main_content:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in metrics:
        lines.append(build_line(c, max_width))
    lines.append(build_separator(max_width))
    for c in branch_content:
        lines.append(build_line(c, max_width))
    lines.append(build_border(max_width, False))

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Generate Checkpoint box")
    parser.add_argument("--type", required=True, choices=["issue-complete", "feedback-applied"],
                        help="Type of checkpoint box")
    parser.add_argument("--issue-name", required=True, help="Name of the issue")
    parser.add_argument("--tokens", help="Token count (for issue-complete)")
    parser.add_argument("--percent", help="Percent of context (for issue-complete)")
    parser.add_argument("--branch", required=True, help="Branch name")
    parser.add_argument("--iteration", help="Feedback iteration number (for feedback-applied)")
    parser.add_argument("--total", help="Total tokens across iterations (for feedback-applied)")

    args = parser.parse_args()

    if args.type == "issue-complete":
        if not args.tokens or not args.percent:
            parser.error("--tokens and --percent required for issue-complete type")
        box = build_checkpoint_issue_complete(
            issue_name=args.issue_name,
            tokens=args.tokens,
            percent=args.percent,
            branch=args.branch
        )
    else:  # feedback-applied
        if not args.iteration or not args.tokens or not args.total:
            parser.error("--iteration, --tokens, and --total required for feedback-applied type")
        box = build_checkpoint_feedback_applied(
            issue_name=args.issue_name,
            iteration=args.iteration,
            tokens=args.tokens,
            total=args.total,
            branch=args.branch
        )

    print(box)


if __name__ == "__main__":
    main()
