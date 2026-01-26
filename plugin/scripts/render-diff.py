#!/usr/bin/env python3
"""
render-diff.py - Transform git diff to 4-column table format

Optimized for approval gate reviews. Converts unified diff format into
a structured table with old/new line numbers, change symbols, and content.

Usage:
    git diff main..HEAD | ./render-diff.py
    ./render-diff.py diff-output.txt

Features:
    - 4-column format: Old line | Symbol | New line | Content
    - Hunk context (function name) in column header
    - Word-level diff highlighting with [] brackets
    - Whitespace visualization (· for spaces, → for tabs)
    - Binary and renamed file indicators
    - Line wrapping with ↩ for long lines
    - Dynamic legend showing only used symbols
    - Display-width aware truncation for wide characters
"""

import json
import os
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

# Add lib directory to path for shared imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'lib'))
from emoji_widths import display_width as _lib_display_width, get_emoji_widths

# Cache emoji widths at module level for efficiency
_emoji_widths = None

def _get_widths():
    """Get cached emoji widths dict."""
    global _emoji_widths
    if _emoji_widths is None:
        _emoji_widths = get_emoji_widths()
    return _emoji_widths


# Box drawing characters
BOX_TOP_LEFT = '╭'
BOX_TOP_RIGHT = '╮'
BOX_BOTTOM_LEFT = '╰'
BOX_BOTTOM_RIGHT = '╯'
BOX_HORIZONTAL = '─'
BOX_VERTICAL = '│'
BOX_T_DOWN = '┬'
BOX_T_UP = '┴'
BOX_T_RIGHT = '├'
BOX_T_LEFT = '┤'
BOX_CROSS = '┼'

# Column widths (fixed)
COL_OLD = 4   # Old line number
COL_SYM = 3   # Symbol (+/-)
COL_NEW = 4   # New line number


@dataclass
class UsedSymbols:
    """Track which symbols are used for dynamic legend."""
    minus: bool = False
    plus: bool = False
    space: bool = False
    tab: bool = False
    wrap: bool = False


@dataclass
class DiffHunk:
    """Represents a single diff hunk."""
    file: str
    old_start: int
    new_start: int
    context: str
    lines: list = field(default_factory=list)


@dataclass
class ParsedDiff:
    """Parsed diff data."""
    hunks: list = field(default_factory=list)
    binary_files: list = field(default_factory=list)
    renamed_files: dict = field(default_factory=dict)


def display_width(s: str) -> int:
    """Calculate display width of a string using shared emoji_widths library."""
    return _lib_display_width(s, _get_widths())


def truncate_to_width(s: str, target_width: int) -> str:
    """Truncate string to target display width."""
    result = ''
    width = 0
    for char in s:
        ea = unicodedata.east_asian_width(char)
        char_width = 2 if ea in ('W', 'F') else 1
        if width + char_width > target_width:
            break
        result += char
        width += char_width
    return result


def fill_char(char: str, count: int) -> str:
    """Generate repeated characters."""
    return char * max(0, count)


def pad_num(num: str, width: int) -> str:
    """Pad number/string to width (right-aligned)."""
    return str(num).rjust(width)


def is_whitespace_only_change(old_line: str, new_line: str) -> bool:
    """Check if a change is whitespace-only."""
    return old_line.replace(' ', '').replace('\t', '') == new_line.replace(' ', '').replace('\t', '')


def visualize_whitespace(line: str, used: UsedSymbols) -> str:
    """Make whitespace visible with markers."""
    result = []
    for char in line:
        if char == '\t':
            result.append('→')
            used.tab = True
        elif char == ' ':
            result.append('·')
            used.space = True
        else:
            result.append(char)
    return ''.join(result)




class DiffRenderer:
    """Render diff in 4-column table format."""

    def __init__(self, width: int = 50):
        self.width = width
        # Calculate content width: total - borders - col widths - internal padding
        # │Old │ x │New │ Content │ = 17 fixed chars
        self.content_width = width - 17
        self.used = UsedSymbols()
        self.output: list[str] = []

    def render(self, diff: ParsedDiff) -> str:
        """Render the complete diff."""
        self.output = []
        first_box = True

        # Render binary files first
        for file in diff.binary_files:
            if not first_box:
                self.output.append('')
            first_box = False
            self._print_binary_file(file)

        # Render renamed files without content changes
        files_with_hunks = {h.file for h in diff.hunks}
        for new_path, old_path in diff.renamed_files.items():
            if new_path not in files_with_hunks:
                if not first_box:
                    self.output.append('')
                first_box = False
                self._print_renamed_file(old_path, new_path)

        # Render each hunk as a separate box
        for hunk in diff.hunks:
            if hunk.file in [bf for bf in diff.binary_files]:
                continue

            if not first_box:
                self.output.append('')
            first_box = False

            self._print_hunk_top(hunk.file)
            self._print_column_header(hunk.context)
            self._render_hunk_content(hunk)
            self._print_hunk_bottom()

        # Print legend
        self._print_legend()

        return '\n'.join(self.output)

    def _print_hunk_top(self, filename: str):
        """Print hunk box top with file header."""
        file_text = f"FILE: {filename}"
        file_len = display_width(file_text)
        padding = self.width - 4 - file_len

        self.output.append(f"{BOX_TOP_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_TOP_RIGHT}")
        self.output.append(f"{BOX_VERTICAL} {file_text}{' ' * padding} {BOX_VERTICAL}")

    def _print_column_header(self, context: str):
        """Print column header row with hunk context."""
        # Separator after file header
        self.output.append(
            f"{BOX_T_RIGHT}{fill_char(BOX_HORIZONTAL, COL_OLD)}{BOX_T_DOWN}"
            f"{fill_char(BOX_HORIZONTAL, COL_SYM)}{BOX_T_DOWN}"
            f"{fill_char(BOX_HORIZONTAL, COL_NEW)}{BOX_T_DOWN}"
            f"{fill_char(BOX_HORIZONTAL, self.content_width + 1)}{BOX_T_LEFT}"
        )

        # Header row with context
        context_text = f"⌁ {context}" if context else ""
        ctx_len = display_width(context_text)
        if ctx_len > self.content_width:
            context_text = truncate_to_width(context_text, self.content_width - 1) + "…"

        self.output.append(
            f"{BOX_VERTICAL}{pad_num('Old', COL_OLD)}{BOX_VERTICAL}   "
            f"{BOX_VERTICAL}{pad_num('New', COL_NEW)}{BOX_VERTICAL} "
            f"{context_text.ljust(self.content_width)}{BOX_VERTICAL}"
        )

        # Separator after headers
        self.output.append(
            f"{BOX_T_RIGHT}{fill_char(BOX_HORIZONTAL, COL_OLD)}{BOX_CROSS}"
            f"{fill_char(BOX_HORIZONTAL, COL_SYM)}{BOX_CROSS}"
            f"{fill_char(BOX_HORIZONTAL, COL_NEW)}{BOX_CROSS}"
            f"{fill_char(BOX_HORIZONTAL, self.content_width + 1)}{BOX_T_LEFT}"
        )

    def _print_row(self, old_num: str, symbol: str, new_num: str, content: str):
        """Print a content row, handling wrapping for long lines."""
        content_len = display_width(content)

        if content_len <= self.content_width:
            # Fits on one line - use ljust for padding
            padded_content = content + ' ' * (self.content_width - content_len)
            self.output.append(
                f"{BOX_VERTICAL}{pad_num(old_num, COL_OLD)}{BOX_VERTICAL} {symbol} "
                f"{BOX_VERTICAL}{pad_num(new_num, COL_NEW)}{BOX_VERTICAL} "
                f"{padded_content}{BOX_VERTICAL}"
            )
        else:
            # Wrap long lines
            self.used.wrap = True
            first_part = content[:self.content_width - 1]
            self.output.append(
                f"{BOX_VERTICAL}{pad_num(old_num, COL_OLD)}{BOX_VERTICAL} {symbol} "
                f"{BOX_VERTICAL}{pad_num(new_num, COL_NEW)}{BOX_VERTICAL} "
                f"{first_part}↩{BOX_VERTICAL}"
            )

            remaining = content[self.content_width - 1:]
            while remaining:
                part_len = display_width(remaining)
                if part_len <= self.content_width:
                    padded = remaining + ' ' * (self.content_width - part_len)
                    self.output.append(
                        f"{BOX_VERTICAL}{' ' * COL_OLD}{BOX_VERTICAL}   "
                        f"{BOX_VERTICAL}{' ' * COL_NEW}{BOX_VERTICAL} "
                        f"{padded}{BOX_VERTICAL}"
                    )
                    remaining = ''
                else:
                    next_part = remaining[:self.content_width - 1]
                    self.output.append(
                        f"{BOX_VERTICAL}{' ' * COL_OLD}{BOX_VERTICAL}   "
                        f"{BOX_VERTICAL}{' ' * COL_NEW}{BOX_VERTICAL} "
                        f"{next_part}↩{BOX_VERTICAL}"
                    )
                    remaining = remaining[self.content_width - 1:]

    def _print_hunk_bottom(self):
        """Print hunk box bottom."""
        self.output.append(
            f"{BOX_BOTTOM_LEFT}{fill_char(BOX_HORIZONTAL, COL_OLD)}{BOX_T_UP}"
            f"{fill_char(BOX_HORIZONTAL, COL_SYM)}{BOX_T_UP}"
            f"{fill_char(BOX_HORIZONTAL, COL_NEW)}{BOX_T_UP}"
            f"{fill_char(BOX_HORIZONTAL, self.content_width + 1)}{BOX_BOTTOM_RIGHT}"
        )

    def _print_binary_file(self, filename: str):
        """Print binary file box."""
        file_text = f"FILE: {filename} (binary)"
        file_len = display_width(file_text)
        padding = self.width - 4 - file_len

        self.output.append(f"{BOX_TOP_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_TOP_RIGHT}")
        self.output.append(f"{BOX_VERTICAL} {file_text}{' ' * padding} {BOX_VERTICAL}")
        self.output.append(f"{BOX_T_RIGHT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_T_LEFT}")
        content = "Binary file changed"
        self.output.append(f"{BOX_VERTICAL} {content.ljust(self.width - 4)} {BOX_VERTICAL}")
        self.output.append(f"{BOX_BOTTOM_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_BOTTOM_RIGHT}")

    def _print_renamed_file(self, old_path: str, new_path: str):
        """Print renamed file box."""
        file_text = f"FILE: {old_path} → {new_path} (renamed)"
        file_len = display_width(file_text)
        padding = self.width - 4 - file_len

        self.output.append(f"{BOX_TOP_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_TOP_RIGHT}")
        if file_len > self.width - 4:
            truncated = file_text[:self.width - 7] + "..."
            self.output.append(f"{BOX_VERTICAL} {truncated.ljust(self.width - 4)} {BOX_VERTICAL}")
        else:
            self.output.append(f"{BOX_VERTICAL} {file_text}{' ' * padding} {BOX_VERTICAL}")
        self.output.append(f"{BOX_T_RIGHT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_T_LEFT}")
        content = "File renamed (no content changes)"
        self.output.append(f"{BOX_VERTICAL} {content.ljust(self.width - 4)} {BOX_VERTICAL}")
        self.output.append(f"{BOX_BOTTOM_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_BOTTOM_RIGHT}")

    def _print_legend(self):
        """Print legend box showing used symbols."""
        legend_items = []

        if self.used.minus:
            legend_items.append("-  del")
        if self.used.plus:
            legend_items.append("+  add")
        if self.used.space:
            legend_items.append("·  space")
        if self.used.tab:
            legend_items.append("→  tab")
        if self.used.wrap:
            legend_items.append("↩  wrap")

        if not legend_items:
            return

        self.output.append('')
        self.output.append(f"{BOX_TOP_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_TOP_RIGHT}")
        self.output.append(f"{BOX_VERTICAL} {'Legend'.ljust(self.width - 3)}{BOX_VERTICAL}")
        self.output.append(f"{BOX_T_RIGHT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_T_LEFT}")

        legend_line = "    ".join(legend_items)
        self.output.append(f"{BOX_VERTICAL}  {legend_line.ljust(self.width - 4)}{BOX_VERTICAL}")
        self.output.append(f"{BOX_BOTTOM_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_BOTTOM_RIGHT}")

    def _render_hunk_content(self, hunk: DiffHunk):
        """Render the content lines of a hunk."""
        # Parse lines into types and contents
        line_types = []
        line_contents = []

        for line in hunk.lines:
            if not line:
                continue
            if line.startswith('+'):
                line_types.append('add')
                line_contents.append(line[1:])
            elif line.startswith('-'):
                line_types.append('del')
                line_contents.append(line[1:])
            elif line.startswith(' '):
                line_types.append('ctx')
                line_contents.append(line[1:])
            elif line == '\\ No newline at end of file':
                continue

        old_line = hunk.old_start
        new_line = hunk.new_start
        i = 0
        num_lines = len(line_types)

        while i < num_lines:
            ltype = line_types[i]
            lcontent = line_contents[i]

            if ltype == 'ctx':
                self._print_row(str(old_line), ' ', str(new_line), lcontent)
                old_line += 1
                new_line += 1
                i += 1

            elif ltype == 'del':
                self.used.minus = True

                # Check for adjacent add for word-diff
                if i + 1 < num_lines and line_types[i + 1] == 'add':
                    del_content = lcontent
                    add_content = line_contents[i + 1]

                    # Check if whitespace-only change
                    if is_whitespace_only_change(del_content, add_content):
                        del_vis = visualize_whitespace(del_content, self.used)
                        add_vis = visualize_whitespace(add_content, self.used)
                        self._print_row(str(old_line), '-', '', del_vis)
                        old_line += 1
                        i += 1
                        self.used.plus = True
                        self._print_row('', '+', str(new_line), add_vis)
                        new_line += 1
                        i += 1
                    else:
                        # Show full lines without inline highlighting
                        self._print_row(str(old_line), '-', '', del_content)
                        old_line += 1
                        i += 1
                        self.used.plus = True
                        self._print_row('', '+', str(new_line), add_content)
                        new_line += 1
                        i += 1
                else:
                    # Just a deletion
                    self._print_row(str(old_line), '-', '', lcontent)
                    old_line += 1
                    i += 1

            elif ltype == 'add':
                self.used.plus = True
                self._print_row('', '+', str(new_line), lcontent)
                new_line += 1
                i += 1

            else:
                i += 1


class DiffParser:
    """Parse unified diff format."""

    # Regex patterns
    FILE_HEADER = re.compile(r'^diff --git a/(.+) b/(.+)$')
    HUNK_HEADER = re.compile(r'^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)$')
    RENAME_FROM = re.compile(r'^rename from (.+)$')
    RENAME_TO = re.compile(r'^rename to (.+)$')
    BINARY = re.compile(r'^Binary files')

    def parse(self, diff_text: str) -> ParsedDiff:
        """Parse diff text into structured data."""
        result = ParsedDiff()
        current_file = ''
        current_hunk: Optional[DiffHunk] = None
        rename_from = ''

        for line in diff_text.split('\n'):
            # New file
            match = self.FILE_HEADER.match(line)
            if match:
                if current_hunk:
                    result.hunks.append(current_hunk)
                    current_hunk = None
                current_file = match.group(2)
                rename_from = ''
                continue

            # Rename detection
            match = self.RENAME_FROM.match(line)
            if match:
                rename_from = match.group(1)
                continue

            match = self.RENAME_TO.match(line)
            if match and rename_from:
                result.renamed_files[current_file] = rename_from
                continue

            # Binary file
            if self.BINARY.match(line):
                result.binary_files.append(current_file)
                continue

            # Skip metadata
            if (line.startswith('index ') or line.startswith('--- ') or
                line.startswith('+++ ') or line.startswith('new file') or
                line.startswith('deleted file') or line.startswith('similarity')):
                continue

            # Hunk header
            match = self.HUNK_HEADER.match(line)
            if match:
                if current_hunk:
                    result.hunks.append(current_hunk)
                current_hunk = DiffHunk(
                    file=current_file,
                    old_start=int(match.group(1)),
                    new_start=int(match.group(2)),
                    context=match.group(3).strip()
                )
                continue

            # Content lines
            if current_hunk is not None:
                if line.startswith('+') or line.startswith('-') or line.startswith(' '):
                    current_hunk.lines.append(line)
                elif line == '\\ No newline at end of file':
                    current_hunk.lines.append(line)

        # Save final hunk
        if current_hunk:
            result.hunks.append(current_hunk)

        return result


def load_config(project_dir: str = '.') -> dict:
    """Load configuration with local override support.

    Loading order (later overrides earlier):
    1. Default values
    2. cat-config.json (project settings)
    3. cat-config.local.json (user overrides)

    Args:
        project_dir: Project root directory containing .claude/cat/
    """
    defaults = {"terminalWidth": 50}
    config = defaults.copy()
    config_dir = Path(project_dir) / '.claude' / 'cat'

    # Load base config, then local config (local overrides base)
    for filename in ['cat-config.json', 'cat-config.local.json']:
        config_path = config_dir / filename
        if config_path.exists():
            try:
                with open(config_path) as f:
                    config.update(json.load(f))
            except (json.JSONDecodeError, IOError):
                pass

    return config


def main():
    """Main entry point."""
    import argparse

    parser = argparse.ArgumentParser(
        description='Transform git diff to 4-column table format',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  git diff main..HEAD | render-diff.py
  render-diff.py diff-output.txt
  render-diff.py --project-dir /path/to/project diff-output.txt
        '''
    )
    parser.add_argument('file', nargs='?', help='Diff file to read (default: stdin)')
    parser.add_argument('--width', '-w', type=int, help='Terminal width (default: from config or 50)')
    parser.add_argument('--project-dir', '-p', default='.', help='Project root directory (default: current directory)')

    args = parser.parse_args()

    # Load config
    config = load_config(args.project_dir)

    # Determine width
    width = args.width or config.get('terminalWidth', 50)

    # Read diff input
    if args.file:
        try:
            with open(args.file) as f:
                diff_text = f.read()
        except IOError as e:
            print(f"ERROR: {e}", file=sys.stderr)
            sys.exit(1)
    else:
        if sys.stdin.isatty():
            print("No diff input. Pipe git diff output or provide a file.", file=sys.stderr)
            sys.exit(1)
        diff_text = sys.stdin.read()

    # Handle empty diff
    if not diff_text.strip():
        print("No changes to display.")
        sys.exit(0)

    # Parse and render
    parser_inst = DiffParser()
    diff = parser_inst.parse(diff_text)

    if not diff.hunks and not diff.binary_files and not diff.renamed_files:
        print("No parseable changes found.", file=sys.stderr)
        sys.exit(0)

    renderer = DiffRenderer(width=width)
    print(renderer.render(diff))


if __name__ == '__main__':
    main()
