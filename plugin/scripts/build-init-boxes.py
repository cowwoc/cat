#!/usr/bin/env python3
"""
Build pre-computed boxes for init.md with correct padding.

This script generates properly aligned boxes for the init.md skill.
All boxes use the standard style (see original box style) with correct padding
to ensure right borders align.

Usage:
  build-init-boxes.py [--width N]

Output:
  JSON with all 8 pre-computed box templates for init.md.

Box style uses: curved box characters
"""

import sys
import json
import argparse
from pathlib import Path

# Add parent directory to path for lib imports
sys.path.insert(0, str(Path(__file__).parent))

from lib.emoji_widths import EmojiWidths

# Standard internal width for init boxes (content area, not including borders)
# Total box width = 70 characters (68 internal + 2 borders)
DEFAULT_WIDTH = 68

# Initialize emoji width calculator
_ew = None

def get_emoji_widths():
    """Get cached EmojiWidths instance."""
    global _ew
    if _ew is None:
        _ew = EmojiWidths()
    return _ew


def build_content_line(content: str, width: int) -> str:
    """Build a single content line with correct padding.

    Args:
        content: The content string (may contain emoji)
        width: Internal content width

    Returns:
        Formatted line: "| content<padding> |"
    """
    ew = get_emoji_widths()
    content_display_width = ew.display_width(content)
    padding = width - content_display_width
    if padding < 0:
        padding = 0
    return "│" + content + " " * padding + "│"


def build_header_line(title: str, width: int) -> str:
    """Build a header line with title embedded.

    Format: ╭─── EMOJI TITLE ───────...───╮

    Args:
        title: Title text (may contain emoji at start)
        width: Internal content width

    Returns:
        Formatted header line
    """
    ew = get_emoji_widths()
    title_display_width = ew.display_width(title)

    # Header format: ╭─── TITLE <dashes>╮
    # Total width = width (content) + 2 (borders)
    # "╭───" = 4 display chars, " " = 1, TITLE = title_display_width, " " = 1
    # So dashes needed = width + 2 - 4 - 1 - title_display_width - 1 - 1
    # = width - title_display_width - 5
    dashes_needed = width - title_display_width - 5
    if dashes_needed < 3:
        dashes_needed = 3

    dashes = "─" * dashes_needed
    return "╭─── " + title + " " + dashes + "╮"


def build_bottom_line(width: int) -> str:
    """Build the bottom border line.

    Args:
        width: Internal content width

    Returns:
        Bottom border: ╰────...────╯
    """
    # Bottom line total width = width + 2 (for side borders)
    # ╰ + dashes + ╯
    dashes = "─" * width
    return "╰" + dashes + "╯"


def build_box(title: str, content_lines: list, width: int = DEFAULT_WIDTH) -> str:
    """Build a complete box with header, content, and footer.

    Args:
        title: Box title (may include emoji)
        content_lines: List of content strings (each will get padding)
        width: Internal content width (default 68)

    Returns:
        Complete box string with correct alignment
    """
    lines = []

    # Header
    lines.append(build_header_line(title, width))

    # Content lines
    for content in content_lines:
        lines.append(build_content_line(content, width))

    # Bottom border
    lines.append(build_bottom_line(width))

    return "\n".join(lines)


def generate_all_boxes(width: int = DEFAULT_WIDTH) -> dict:
    """Generate all 8 pre-computed boxes for init.md.

    Returns:
        Dictionary with box names as keys and rendered boxes as values.
        Boxes with variables use placeholders like {N}, {trust}, etc.
    """
    boxes = {}

    # Box 1: Default gates configured
    # Title has {N} variable, uses emoji 📊
    boxes["default_gates_configured"] = build_box(
        "📊 Default gates configured for {N} versions",
        [
            "                                                                  ",
            "  Entry gates: Work proceeds sequentially                         ",
            "  - Each minor waits for previous minor to complete               ",
            "  - Each major waits for previous major to complete               ",
            "                                                                  ",
            "  Exit gates: Standard completion criteria                        ",
            "  - Minor versions: all tasks must complete                       ",
            "  - Major versions: all minor versions must complete              ",
            "                                                                  ",
            "  To customize gates for any version:                             ",
            "  → /cat:config → 📊 Version Gates                                ",
        ],
        width
    )

    # Box 2: Research skipped
    # Static content, uses emoji ℹ️
    boxes["research_skipped"] = build_box(
        "ℹ️ RESEARCH SKIPPED",
        [
            "                                                                  ",
            "  Stakeholder research was skipped during import.                 ",
            "                                                                  ",
            "  To research a pending version later:                            ",
            "  → /cat:research {version}                                       ",
            "                                                                  ",
            "  Example: /cat:research 1.2                                      ",
        ],
        width
    )

    # Box 3: Choose your partner
    # Static content, uses emoji 🎮
    boxes["choose_your_partner"] = build_box(
        "🎮 CHOOSE YOUR PARTNER",
        [
            "                                                                  ",
            "  Every developer has a style. These questions shape how your     ",
            "  AI partner approaches the work ahead.                           ",
            "                                                                  ",
            "  Choose wisely - your preferences guide every decision.          ",
        ],
        width
    )

    # Box 4: CAT initialized
    # Has {trust}, {curiosity}, {patience} variables, uses emoji 🚀
    boxes["cat_initialized"] = build_box(
        "🚀 CAT INITIALIZED",
        [
            "                                                                  ",
            "  🤝 Trust: {trust}                                               ",
            "  🔍 Curiosity: {curiosity}                                       ",
            "  ⏳ Patience: {patience}                                         ",
            "                                                                  ",
            "  Your partner is ready. Let's build something solid.             ",
            "  Adjust anytime: /cat:config                                     ",
        ],
        width
    )

    # Box 5: First task walkthrough
    # Static content, uses emoji 📋
    boxes["first_task_walkthrough"] = build_box(
        "📋 FIRST TASK WALKTHROUGH",
        [
            "                                                                  ",
            "  Great! Let's create your first task together.                   ",
            "  I'll ask a few questions to understand what you want to build.  ",
        ],
        width
    )

    # Box 6: First task created
    # Has {task-name} variable, uses emoji ✅
    boxes["first_task_created"] = build_box(
        "✅ FIRST TASK CREATED",
        [
            "                                                                  ",
            "  Task: {task-name}                                               ",
            "  Location: .claude/cat/v0/v0.0/{task-name}/                      ",
            "                                                                  ",
            "  Files created:                                                  ",
            "  - PLAN.md - What needs to be done                               ",
            "  - STATE.md - Progress tracking                                  ",
        ],
        width
    )

    # Box 7: All set
    # Static content, uses emoji 👋
    boxes["all_set"] = build_box(
        "👋 ALL SET",
        [
            "                                                                  ",
            "  Your project is ready. When you want to start:                  ",
            "                                                                  ",
            "  → /cat:work         Execute your first task                     ",
            "  → /cat:status       See project overview                        ",
            "  → /cat:add          Add more tasks or versions                  ",
            "  → /cat:help         Full command reference                      ",
        ],
        width
    )

    # Box 8: Explore at your own pace
    # Static content, uses emoji 👋
    boxes["explore_at_your_own_pace"] = build_box(
        "👋 EXPLORE AT YOUR OWN PACE",
        [
            "                                                                  ",
            "  Essential commands to get started:                              ",
            "                                                                  ",
            "  → /cat:status       See what's happening                        ",
            "  → /cat:add          Add versions and tasks                      ",
            "  → /cat:work         Execute tasks                               ",
            "  → /cat:help         Full command reference                      ",
            "                                                                  ",
            "  Tip: Run /cat:status anytime to see suggested next steps.       ",
        ],
        width
    )

    return boxes


def main():
    parser = argparse.ArgumentParser(description='Build pre-computed boxes for init.md')
    parser.add_argument('--width', type=int, default=DEFAULT_WIDTH,
                       help=f'Internal content width (default: {DEFAULT_WIDTH})')
    parser.add_argument('--format', choices=['json', 'text'], default='json',
                       help='Output format (default: json)')
    parser.add_argument('--box', type=str,
                       help='Output only a specific box by name')
    args = parser.parse_args()

    boxes = generate_all_boxes(args.width)

    if args.box:
        if args.box not in boxes:
            print(json.dumps({"error": f"Unknown box: {args.box}"}))
            sys.exit(1)
        if args.format == 'json':
            print(json.dumps({args.box: boxes[args.box]}, ensure_ascii=False, indent=2))
        else:
            print(boxes[args.box])
    else:
        if args.format == 'json':
            print(json.dumps(boxes, ensure_ascii=False, indent=2))
        else:
            for name, box in boxes.items():
                print(f"=== {name} ===")
                print(box)
                print()


if __name__ == "__main__":
    main()
