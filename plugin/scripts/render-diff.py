#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
render-diff.py - Transform git diff to 2-column table format

Optimized for approval gate reviews. Converts unified diff format into
a structured table with dynamic line numbers and content.

Usage:
    git diff main..HEAD | ./render-diff.py
    git log -p main..HEAD | ./render-diff.py    # Shows commit messages
    ./render-diff.py diff-output.txt

Features:
    - 2-column format: Line number | Indicator+Content
    - Dynamic line number width (sized to max line number in hunk)
    - Commit message headers when using git log -p output
    - Hunk context (function name) in separator row
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


@dataclass
class UsedSymbols:
    """Track which symbols are used for dynamic legend."""
    minus: bool = False
    plus: bool = False
    space: bool = False
    tab: bool = False
    wrap: bool = False


@dataclass
class Commit:
    """Represents a git commit."""
    hash: str
    subject: str
    body: str = ""


@dataclass
class DiffHunk:
    """Represents a single diff hunk."""
    file: str
    old_start: int
    new_start: int
    context: str
    lines: list = field(default_factory=list)
    commit: Optional[Commit] = None


@dataclass
class ParsedDiff:
    """Parsed diff data."""
    hunks: list = field(default_factory=list)
    binary_files: list = field(default_factory=list)
    renamed_files: dict = field(default_factory=dict)
    commits: list = field(default_factory=list)  # Ordered list of commits
    file_to_commit: dict = field(default_factory=dict)  # Maps file to commit


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
    """Render diff in 2-column table format."""

    def __init__(self, width: int = 50):
        self.width = width
        self.used = UsedSymbols()
        self.output: list[str] = []
        # Per-hunk state (set before rendering each hunk)
        self.col_line = 0
        self.content_width = 0

    def _calc_col_width(self, hunk: DiffHunk) -> int:
        """Calculate the line number column width for a hunk (max 4 digits = 9999)."""
        max_line = 0
        old_line = hunk.old_start
        new_line = hunk.new_start

        for line in hunk.lines:
            if not line:
                continue
            if line.startswith('+'):
                max_line = max(max_line, new_line)
                new_line += 1
            elif line.startswith('-'):
                max_line = max(max_line, old_line)
                old_line += 1
            elif line.startswith(' '):
                max_line = max(max_line, new_line)
                old_line += 1
                new_line += 1

        # Return digit count, capped at 4
        return min(4, max(2, len(str(max_line))))

    def render(self, diff: ParsedDiff) -> str:
        """Render the complete diff."""
        self.output = []
        first_box = True
        current_commit = None
        current_file = None

        # Group hunks by commit for rendering
        def get_commit_for_file(filename: str) -> Optional[Commit]:
            return diff.file_to_commit.get(filename)

        def maybe_print_commit_header(commit: Optional[Commit]) -> bool:
            """Print commit header if it's a new commit. Returns True if printed."""
            nonlocal current_commit, first_box
            if commit and commit != current_commit:
                if not first_box:
                    self.output.append('')
                current_commit = commit
                self._print_commit_header(commit)
                first_box = False
                return True
            return False

        # Render binary files first
        for file in diff.binary_files:
            commit = get_commit_for_file(file)
            maybe_print_commit_header(commit)
            if not first_box:
                self.output.append('')
            first_box = False
            self._print_binary_file(file)

        # Render renamed files without content changes
        files_with_hunks = {h.file for h in diff.hunks}
        for new_path, old_path in diff.renamed_files.items():
            if new_path not in files_with_hunks:
                commit = get_commit_for_file(new_path)
                maybe_print_commit_header(commit)
                if not first_box:
                    self.output.append('')
                first_box = False
                self._print_renamed_file(old_path, new_path)

        # Render each hunk
        for hunk in diff.hunks:
            if hunk.file in [bf for bf in diff.binary_files]:
                continue

            maybe_print_commit_header(hunk.commit)
            if not first_box:
                self.output.append('')
            first_box = False

            # Set up per-hunk rendering state
            self.col_line = self._calc_col_width(hunk)
            # content_width = total - left│ - col_line - mid│ - 2 indicator chars - right│
            self.content_width = self.width - self.col_line - 5

            # First hunk for this file: print top border with filename
            if hunk.file != current_file:
                current_file = hunk.file
                self._print_hunk_top(hunk.file)
                self._print_hunk_separator(hunk.context)
            else:
                # Subsequent hunk for same file: just separator
                self._print_hunk_separator(hunk.context)

            self._render_hunk_content(hunk)

        # Print bottom border for last hunk
        if diff.hunks:
            self._print_hunk_bottom()

        # Print legend
        self._print_legend()

        return '\n'.join(self.output)

    def _print_commit_header(self, commit: Commit):
        """Print commit header box."""
        # Short hash (7 chars)
        short_hash = commit.hash[:7] if len(commit.hash) >= 7 else commit.hash

        # Format: COMMIT abc1234: subject line
        header_text = f"COMMIT {short_hash}: {commit.subject}"
        header_len = display_width(header_text)

        # Truncate if too long
        if header_len > self.width - 4:
            max_subject_len = self.width - 4 - len(f"COMMIT {short_hash}: ") - 3
            truncated_subject = commit.subject[:max_subject_len] + "..."
            header_text = f"COMMIT {short_hash}: {truncated_subject}"
            header_len = display_width(header_text)

        padding = self.width - 4 - header_len

        self.output.append(f"{BOX_TOP_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_TOP_RIGHT}")
        self.output.append(f"{BOX_VERTICAL} {header_text}{' ' * max(0, padding)} {BOX_VERTICAL}")
        self.output.append(f"{BOX_BOTTOM_LEFT}{fill_char(BOX_HORIZONTAL, self.width - 2)}{BOX_BOTTOM_RIGHT}")

    def _print_hunk_top(self, filename: str):
        """Print hunk box top with filename embedded in border."""
        # Format: ╭──┬─ filename ─╮
        file_text = f" {filename} "
        file_len = display_width(file_text)

        # Available space for filename: total - corners - col marker - padding
        available = self.width - 2 - self.col_line - 3  # -3 for ┬─ and final ─

        if file_len > available:
            # Truncate filename
            file_text = f" {truncate_to_width(filename, available - 5)}... "
            file_len = display_width(file_text)

        # Left side: ╭ + col_line dashes + ┬─
        left_part = f"{BOX_TOP_LEFT}{fill_char(BOX_HORIZONTAL, self.col_line)}{BOX_T_DOWN}{BOX_HORIZONTAL}"
        # Right side: remaining dashes + ╮
        right_dashes = self.width - len(left_part) - file_len - 1

        self.output.append(f"{left_part}{file_text}{fill_char(BOX_HORIZONTAL, right_dashes)}{BOX_TOP_RIGHT}")

    def _print_hunk_separator(self, context: str):
        """Print hunk separator with context."""
        # Format: ├──┼─ ⌁ context ─┤
        context_text = f" ⌁ {context} " if context else " "
        ctx_len = display_width(context_text)

        # Available space: total - corners - col marker - padding
        available = self.width - 2 - self.col_line - 3  # -3 for ┼─ and final ─

        if ctx_len > available:
            # Truncate context
            context_text = f" ⌁ {truncate_to_width(context, available - 6)}… "
            ctx_len = display_width(context_text)

        # Left side: ├ + col_line dashes + ┼─
        left_part = f"{BOX_T_RIGHT}{fill_char(BOX_HORIZONTAL, self.col_line)}{BOX_CROSS}{BOX_HORIZONTAL}"
        # Right side: remaining dashes + ┤
        right_dashes = self.width - len(left_part) - ctx_len - 1

        self.output.append(f"{left_part}{context_text}{fill_char(BOX_HORIZONTAL, right_dashes)}{BOX_T_LEFT}")

    def _print_row(self, line_num: int, indicator: str, content: str):
        """Print a content row, handling wrapping for long lines.

        Args:
            line_num: Line number to display (0 for continuation lines)
            indicator: Two-character indicator ('- ', '+ ', or '  ')
            content: Line content
        """
        content_len = display_width(content)
        line_str = str(line_num) if line_num > 0 else ''

        if content_len <= self.content_width:
            # Fits on one line - use padding for alignment
            padded_content = content + ' ' * (self.content_width - content_len)
            self.output.append(
                f"{BOX_VERTICAL}{line_str:>{self.col_line}}{BOX_VERTICAL}{indicator}"
                f"{padded_content}{BOX_VERTICAL}"
            )
        else:
            # Wrap long lines
            self.used.wrap = True
            first_part = content[:self.content_width - 1]
            self.output.append(
                f"{BOX_VERTICAL}{line_str:>{self.col_line}}{BOX_VERTICAL}{indicator}"
                f"{first_part}↩{BOX_VERTICAL}"
            )

            remaining = content[self.content_width - 1:]
            while remaining:
                part_len = display_width(remaining)
                if part_len <= self.content_width:
                    padded = remaining + ' ' * (self.content_width - part_len)
                    self.output.append(
                        f"{BOX_VERTICAL}{' ' * self.col_line}{BOX_VERTICAL}  "
                        f"{padded}{BOX_VERTICAL}"
                    )
                    remaining = ''
                else:
                    next_part = remaining[:self.content_width - 1]
                    self.output.append(
                        f"{BOX_VERTICAL}{' ' * self.col_line}{BOX_VERTICAL}  "
                        f"{next_part}↩{BOX_VERTICAL}"
                    )
                    remaining = remaining[self.content_width - 1:]

    def _print_hunk_bottom(self):
        """Print hunk box bottom."""
        # Format: ╰──┴───╯
        self.output.append(
            f"{BOX_BOTTOM_LEFT}{fill_char(BOX_HORIZONTAL, self.col_line)}{BOX_T_UP}"
            f"{fill_char(BOX_HORIZONTAL, self.width - self.col_line - 2)}{BOX_BOTTOM_RIGHT}"
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
                self._print_row(new_line, '  ', lcontent)
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
                        self._print_row(old_line, '- ', del_vis)
                        old_line += 1
                        i += 1
                        self.used.plus = True
                        self._print_row(new_line, '+ ', add_vis)
                        new_line += 1
                        i += 1
                    else:
                        # Show full lines without inline highlighting
                        self._print_row(old_line, '- ', del_content)
                        old_line += 1
                        i += 1
                        self.used.plus = True
                        self._print_row(new_line, '+ ', add_content)
                        new_line += 1
                        i += 1
                else:
                    # Just a deletion
                    self._print_row(old_line, '- ', lcontent)
                    old_line += 1
                    i += 1

            elif ltype == 'add':
                self.used.plus = True
                self._print_row(new_line, '+ ', lcontent)
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
    COMMIT_HEADER = re.compile(r'^commit ([a-f0-9]{7,40})$')

    def parse(self, diff_text: str) -> ParsedDiff:
        """Parse diff text into structured data."""
        result = ParsedDiff()
        current_file = ''
        current_hunk: Optional[DiffHunk] = None
        current_commit: Optional[Commit] = None
        rename_from = ''
        in_commit_message = False
        in_diff_content = False  # True once we've seen 'diff --git' for this commit
        commit_message_lines: list[str] = []

        lines = diff_text.split('\n')
        i = 0
        while i < len(lines):
            line = lines[i]

            # Commit header (from git log -p or git show)
            match = self.COMMIT_HEADER.match(line)
            if match:
                # Finalize previous commit message if any
                if current_commit and commit_message_lines:
                    self._finalize_commit_message(current_commit, commit_message_lines)
                    commit_message_lines = []

                if current_hunk:
                    result.hunks.append(current_hunk)
                    current_hunk = None

                commit_hash = match.group(1)
                current_commit = Commit(hash=commit_hash, subject="")
                result.commits.append(current_commit)
                in_commit_message = False
                in_diff_content = False  # Reset for new commit
                i += 1
                continue

            # Skip Author/Date/Merge lines after commit header (before diff content)
            if current_commit and not in_commit_message and not in_diff_content:
                if (line.startswith('Author:') or line.startswith('Date:') or
                    line.startswith('Merge:') or line.strip() == ''):
                    # Empty line after Date: marks start of commit message
                    if line.strip() == '' and not commit_message_lines:
                        in_commit_message = True
                    i += 1
                    continue

            # Collect commit message lines (indented with 4 spaces, before diff content)
            if in_commit_message and current_commit and not in_diff_content:
                if line.startswith('    '):
                    commit_message_lines.append(line[4:])
                    i += 1
                    continue
                elif line.strip() == '':
                    commit_message_lines.append('')
                    i += 1
                    continue
                else:
                    # End of commit message (hit diff --git or other content)
                    self._finalize_commit_message(current_commit, commit_message_lines)
                    commit_message_lines = []
                    in_commit_message = False
                    # Don't increment i - process this line normally

            # New file
            match = self.FILE_HEADER.match(line)
            if match:
                in_diff_content = True  # Now in diff content, stop looking for commit messages
                if current_hunk:
                    result.hunks.append(current_hunk)
                    current_hunk = None
                current_file = match.group(2)
                rename_from = ''
                # Associate file with current commit
                if current_commit:
                    result.file_to_commit[current_file] = current_commit
                i += 1
                continue

            # Rename detection
            match = self.RENAME_FROM.match(line)
            if match:
                rename_from = match.group(1)
                i += 1
                continue

            match = self.RENAME_TO.match(line)
            if match and rename_from:
                result.renamed_files[current_file] = rename_from
                i += 1
                continue

            # Binary file
            if self.BINARY.match(line):
                result.binary_files.append(current_file)
                i += 1
                continue

            # Skip metadata
            if (line.startswith('index ') or line.startswith('--- ') or
                line.startswith('+++ ') or line.startswith('new file') or
                line.startswith('deleted file') or line.startswith('similarity')):
                i += 1
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
                    context=match.group(3).strip(),
                    commit=current_commit
                )
                i += 1
                continue

            # Content lines
            if current_hunk is not None:
                if line.startswith('+') or line.startswith('-') or line.startswith(' '):
                    current_hunk.lines.append(line)
                elif line == '\\ No newline at end of file':
                    current_hunk.lines.append(line)

            i += 1

        # Finalize any remaining commit message
        if current_commit and commit_message_lines:
            self._finalize_commit_message(current_commit, commit_message_lines)

        # Save final hunk
        if current_hunk:
            result.hunks.append(current_hunk)

        return result

    def _finalize_commit_message(self, commit: Commit, lines: list[str]):
        """Extract subject and body from commit message lines."""
        # Remove leading/trailing empty lines
        while lines and not lines[0].strip():
            lines.pop(0)
        while lines and not lines[-1].strip():
            lines.pop()

        if not lines:
            return

        # First non-empty line is subject
        commit.subject = lines[0].strip()

        # Rest is body (skip blank line after subject if present)
        if len(lines) > 1:
            body_lines = lines[1:]
            while body_lines and not body_lines[0].strip():
                body_lines.pop(0)
            commit.body = '\n'.join(body_lines)


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
    # Default to CLAUDE_PROJECT_DIR if available (handles worktrees correctly)
    default_project_dir = os.environ.get('CLAUDE_PROJECT_DIR', '.')
    parser.add_argument('--project-dir', '-p', default=default_project_dir,
                        help='Project root directory (default: CLAUDE_PROJECT_DIR or current directory)')

    args = parser.parse_args()

    # Load config from project dir (CLAUDE_PROJECT_DIR has .local.json, worktrees don't)
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
