#!/usr/bin/env python3
"""
Build box lines with correct padding for terminal display.

Usage:
  build-box-lines.py [--min-width N] content1 [content2 ...]
  echo -e "content1\ncontent2" | build-box-lines.py [--min-width N]

Options:
  --min-width N   Minimum box width. Box expands automatically to fit content
                  if content is wider than N. Useful for ensuring consistent
                  widths when header text is wider than content.

Output:
  JSON with computed lines ready to copy-paste into box output.

Example:
  $ ./build-box-lines.py "📊 Overall: 91%" "🏆 112/122 tasks"
  {
    "max_content_width": 16,
    "lines": [
      "│ 📊 Overall: 91%  │",
      "│ 🏆 112/122 tasks │"
    ],
    "top": "╭──────────────────╮",
    "bottom": "╰──────────────────╯"
  }
"""

import sys
import json
import argparse

# Emojis that display as width 2 in most terminals
WIDTH_2_EMOJIS = {
    '📊', '📦', '🎯', '📋', '⚙️', '🏆', '🧠', '🐱', '🧹', '🤝',
    '✅', '🔍', '👀', '🔭', '⏳', '⚡', '🔒', '✨', '⚠️', '✦',
    '☑️', '🔄', '🔳', '🚫', '🚧', '🚀'
}

# Single-character emojis (without variation selector) that are width 2
WIDTH_2_SINGLE = {
    '📊', '📦', '🎯', '📋', '🏆', '🧠', '🐱', '🧹', '🤝',
    '✅', '🔍', '👀', '🔭', '⏳', '⚡', '🔒', '✨', '✦',
    '🔄', '🔳', '🚫', '🚧', '🚀'
}


def display_width(text: str) -> int:
    """Calculate terminal display width of a string."""
    width = 0
    i = 0
    while i < len(text):
        char = text[i]

        # Check for two-character emoji sequences (char + variation selector)
        if i + 1 < len(text):
            two_char = text[i:i+2]
            if two_char in WIDTH_2_EMOJIS:
                width += 2
                i += 2
                continue

        # Check for single-character width-2 emojis
        if char in WIDTH_2_SINGLE:
            width += 2
            i += 1
            continue

        # Skip variation selectors (they don't add width)
        if char == '\ufe0f':  # variation selector-16
            i += 1
            continue

        # All other characters are width 1
        width += 1
        i += 1

    return width


def build_line(content: str, max_width: int) -> str:
    """Build a single box line with correct padding."""
    content_width = display_width(content)
    padding = max_width - content_width
    return "│ " + content + " " * padding + " │"


def build_border(max_width: int, is_top: bool) -> str:
    """Build top or bottom border."""
    dash_count = max_width + 2
    dashes = "─" * dash_count
    if is_top:
        return "╭" + dashes + "╮"
    else:
        return "╰" + dashes + "╯"


def main():
    parser = argparse.ArgumentParser(description='Build box lines with correct padding')
    parser.add_argument('contents', nargs='*', help='Content items for the box')
    parser.add_argument('--min-width', type=int,
                        help='Minimum width (box expands to fit content if wider)')
    parser.add_argument('--format', choices=['json', 'lines'], default='json',
                       help='Output format (default: json)')
    args = parser.parse_args()

    # Get content from args or stdin
    if args.contents:
        contents = args.contents
    else:
        # Preserve empty lines (they become blank rows in the box)
        contents = [line.rstrip('\n') for line in sys.stdin]
        # Remove trailing empty lines only
        while contents and not contents[-1]:
            contents.pop()

    if not contents:
        print(json.dumps({"error": "No content provided"}))
        sys.exit(1)

    # Calculate widths
    widths = [(c, display_width(c)) for c in contents]
    natural_max = max(w for _, w in widths)
    # --min-width sets minimum width; always expand to fit content
    max_content_width = max(args.min_width or 0, natural_max)

    # Build lines
    lines = [build_line(c, max_content_width) for c in contents]
    top = build_border(max_content_width, is_top=True)
    bottom = build_border(max_content_width, is_top=False)

    if args.format == 'json':
        result = {
            "max_content_width": max_content_width,
            "widths": {c: w for c, w in widths},
            "lines": lines,
            "top": top,
            "bottom": bottom
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        # Plain lines format for direct copy-paste
        print(top)
        for line in lines:
            print(line)
        print(bottom)


if __name__ == "__main__":
    main()
