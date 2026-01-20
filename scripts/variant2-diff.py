#!/usr/bin/env python3
"""
variant2-diff.py - Convert git diff output to variant 2 format

This script reads git diff output and converts it to a structured, readable
format with language-aware section detection and configurable terminal width.

Usage:
    git diff | variant2-diff.py [--task-name NAME] [--width WIDTH]
    variant2-diff.py [--task-name NAME] [--width WIDTH] < diff.patch
    variant2-diff.py [--task-name NAME] [--width WIDTH] --file diff.patch
"""

import argparse
import json
import os
import re
import sys
import textwrap
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Optional


class FileType(Enum):
    """File type classification for formatting rules."""
    PROSE = "prose"  # markdown, txt, license
    CODE = "code"    # java, python, js, ts, sh, json, yaml
    UNKNOWN = "unknown"


@dataclass
class DiffHunk:
    """Represents a single diff hunk with context and changes."""
    old_start: int
    old_count: int
    new_start: int
    new_count: int
    section_header: str
    lines: list = field(default_factory=list)

    def get_line_range(self) -> tuple:
        """Get the line range this hunk covers."""
        return (self.new_start, self.new_start + self.new_count - 1)


@dataclass
class DiffFile:
    """Represents a file in the diff."""
    old_path: str
    new_path: str
    hunks: list = field(default_factory=list)
    is_binary: bool = False
    is_new: bool = False
    is_deleted: bool = False

    @property
    def display_path(self) -> str:
        """Get the path to display."""
        if self.is_new:
            return self.new_path
        if self.is_deleted:
            return self.old_path
        return self.new_path

    @property
    def file_type(self) -> FileType:
        """Detect file type from extension."""
        ext = Path(self.display_path).suffix.lower()
        prose_extensions = {'.md', '.txt', '.license', '.rst', '.adoc'}
        code_extensions = {
            '.py', '.java', '.js', '.ts', '.jsx', '.tsx',
            '.sh', '.bash', '.zsh', '.fish',
            '.json', '.yaml', '.yml', '.toml',
            '.c', '.cpp', '.h', '.hpp', '.cs',
            '.go', '.rs', '.rb', '.php', '.pl',
            '.swift', '.kt', '.scala', '.groovy',
            '.lua', '.r', '.m', '.mm',
            '.sql', '.graphql', '.proto',
            '.css', '.scss', '.sass', '.less',
            '.html', '.htm', '.xml', '.svg',
            '.vue', '.svelte'
        }

        if ext in prose_extensions:
            return FileType.PROSE
        elif ext in code_extensions:
            return FileType.CODE
        else:
            # Default to code for unknown types (no wrapping)
            return FileType.UNKNOWN


@dataclass
class DiffStats:
    """Statistics for the diff."""
    files_changed: int = 0
    lines_added: int = 0
    lines_removed: int = 0


class SectionDetector:
    """Detect section boundaries in code and prose files."""

    # Language-specific section patterns
    SECTION_PATTERNS = {
        '.py': [
            r'^(class\s+\w+|def\s+\w+|async\s+def\s+\w+)',
        ],
        '.java': [
            r'^(\s*(?:public|private|protected)?\s*(?:static\s+)?(?:class|interface|enum)\s+\w+)',
            r'^(\s*(?:public|private|protected)?\s*(?:static\s+)?(?:\w+(?:<[^>]+>)?\s+)?\w+\s*\([^)]*\)\s*(?:throws\s+\w+)?)',
        ],
        '.js': [
            r'^(\s*(?:export\s+)?(?:default\s+)?(?:async\s+)?function\s+\w+)',
            r'^(\s*(?:export\s+)?(?:default\s+)?class\s+\w+)',
            r'^(\s*(?:const|let|var)\s+\w+\s*=\s*(?:async\s+)?(?:function|\([^)]*\)\s*=>))',
        ],
        '.ts': [
            r'^(\s*(?:export\s+)?(?:default\s+)?(?:async\s+)?function\s+\w+)',
            r'^(\s*(?:export\s+)?(?:default\s+)?class\s+\w+)',
            r'^(\s*(?:export\s+)?interface\s+\w+)',
            r'^(\s*(?:export\s+)?type\s+\w+)',
        ],
        '.sh': [
            r'^(\s*\w+\s*\(\))',
            r'^(\s*function\s+\w+)',
        ],
        '.bash': [
            r'^(\s*\w+\s*\(\))',
            r'^(\s*function\s+\w+)',
        ],
        '.md': [
            r'^(#{1,6}\s+.+)',  # Markdown headings
        ],
        '.txt': [
            r'^(\s*$)',  # Blank lines as section separators
        ],
        '.json': [
            r'^(\s*"[^"]+"\s*:)',  # Top-level keys
        ],
        '.yaml': [
            r'^([a-zA-Z_][a-zA-Z0-9_]*:)',  # Top-level keys
        ],
        '.yml': [
            r'^([a-zA-Z_][a-zA-Z0-9_]*:)',  # Top-level keys
        ],
    }

    @classmethod
    def detect_section(cls, filepath: str, line: str) -> Optional[str]:
        """Detect if a line marks a section boundary."""
        ext = Path(filepath).suffix.lower()
        patterns = cls.SECTION_PATTERNS.get(ext, [])

        for pattern in patterns:
            match = re.match(pattern, line)
            if match:
                return match.group(1).strip()

        return None

    @classmethod
    def find_section_for_hunk(cls, filepath: str, hunk_header: str, context_lines: list) -> str:
        """Find the section name for a hunk based on its header or context."""
        # First try to extract from git's hunk header (e.g., @@ -1,5 +1,5 @@ function_name)
        if hunk_header:
            # Git often includes function/class name after the @@...@@
            match = re.match(r'^@@[^@]+@@\s*(.+)$', hunk_header)
            if match:
                return match.group(1).strip()

        # Search context lines for section markers
        for line in reversed(context_lines):
            section = cls.detect_section(filepath, line.lstrip(' -+'))
            if section:
                return section

        return "(no section detected)"


class WhitespaceMarker:
    """Handle whitespace visualization for whitespace-only changes."""

    # Non-standard whitespace characters that should always be flagged
    SUSPICIOUS_WHITESPACE = {
        '\u00A0': 'NBSP',       # Non-breaking space
        '\u200B': 'ZWSP',       # Zero-width space
        '\u200C': 'ZWNJ',       # Zero-width non-joiner
        '\u200D': 'ZWJ',        # Zero-width joiner
        '\u2060': 'WJ',         # Word joiner
        '\uFEFF': 'BOM',        # Byte order mark
        '\u2028': 'LSEP',       # Line separator
        '\u2029': 'PSEP',       # Paragraph separator
    }

    @classmethod
    def is_whitespace_only_change(cls, old_line: str, new_line: str) -> bool:
        """Check if the change is whitespace-only."""
        return old_line.strip() == new_line.strip()

    @classmethod
    def mark_whitespace(cls, line: str, show_all: bool = False) -> str:
        """Replace whitespace characters with visible markers."""
        result = line

        # Always mark suspicious non-standard whitespace
        for char, name in cls.SUSPICIOUS_WHITESPACE.items():
            if char in result:
                result = result.replace(char, f'[{name}]')

        if show_all:
            # Mark regular whitespace only when relevant
            result = result.replace('\t', '\u2192')  # Arrow for tab
            # Mark spaces at end of line or when they're the change
            result = re.sub(r' ', '\u00B7', result)  # Middle dot for space

        return result

    @classmethod
    def detect_trailing_whitespace(cls, line: str) -> tuple:
        """Detect trailing whitespace, return (content, trailing)."""
        stripped = line.rstrip()
        trailing = line[len(stripped):]
        return (stripped, trailing)

    @classmethod
    def detect_line_ending_change(cls, old_line: str, new_line: str) -> Optional[str]:
        """Detect line ending changes."""
        old_has_crlf = old_line.endswith('\r\n')
        new_has_crlf = new_line.endswith('\r\n')
        old_has_lf = old_line.endswith('\n') and not old_has_crlf
        new_has_lf = new_line.endswith('\n') and not new_has_crlf

        if old_has_crlf and new_has_lf:
            return '[CRLF\u2192LF]'
        elif old_has_lf and new_has_crlf:
            return '[LF\u2192CRLF]'

        return None

    @classmethod
    def format_whitespace_change(cls, prefix: str, line: str) -> str:
        """Format a line with whitespace markers for whitespace-only changes."""
        marked = cls.mark_whitespace(line, show_all=True)
        content, trailing = cls.detect_trailing_whitespace(marked)
        if trailing:
            return f"{prefix}{content}[trailing:{len(trailing)} chars]"
        return f"{prefix}{marked}"


class Variant2Formatter:
    """Format diff output in variant 2 style (Proposal I - Inline Context Markers)."""

    # Box drawing characters
    BOX_TOP_LEFT = '\u250C'      # ┌
    BOX_TOP_RIGHT = '\u2510'     # ┐
    BOX_BOTTOM_LEFT = '\u2514'   # └
    BOX_BOTTOM_RIGHT = '\u2518'  # ┘
    BOX_HORIZONTAL = '\u2500'    # ─
    BOX_VERTICAL = '\u2502'      # │

    # Line number column width (5 chars for number + space + vertical bar)
    LINE_NUM_WIDTH = 5

    def __init__(self, width: int = 50, task_name: str = ""):
        self.width = width
        self.task_name = task_name or "diff"

    def format_header(self, stats: DiffStats) -> str:
        """Format the summary header box with task name and stats."""
        # Inner content width (excluding border chars and padding spaces)
        # Box is: │ content │ so inner content width = width - 4
        content_width = self.width - 4

        # Format stats: "N files  +X / -Y"
        stats_str = f"{stats.files_changed} files  +{stats.lines_added} / -{stats.lines_removed}"

        # Task name left, stats right
        # Available space for task name = content_width - len(stats_str) - spacing
        spacing = 2  # minimum spacing between name and stats
        task_display = self.task_name
        max_task_len = content_width - len(stats_str) - spacing
        if len(task_display) > max_task_len:
            task_display = task_display[:max_task_len - 3] + "..."

        # Build content line: task_name + padding + stats
        content_padding = content_width - len(task_display) - len(stats_str)
        content_line = task_display + (' ' * content_padding) + stats_str

        # Build the box (top/bottom use width-2 for border chars only)
        border_width = self.width - 2
        lines = []
        lines.append(f"{self.BOX_TOP_LEFT}{self.BOX_HORIZONTAL * border_width}{self.BOX_TOP_RIGHT}")
        lines.append(f"{self.BOX_VERTICAL} {content_line} {self.BOX_VERTICAL}")
        lines.append(f"{self.BOX_BOTTOM_LEFT}{self.BOX_HORIZONTAL * border_width}{self.BOX_BOTTOM_RIGHT}")

        return '\n'.join(lines)

    def format_file_header(self, filepath: str) -> str:
        """Format a file header separator: ── filename.ext ──────..."""
        prefix = f"{self.BOX_HORIZONTAL}{self.BOX_HORIZONTAL} {filepath} "
        remaining = self.width - len(prefix)
        return f"\n{prefix}{self.BOX_HORIZONTAL * max(0, remaining)}"

    def format_file_footer(self) -> str:
        """Format a file footer: just dashes."""
        return f"{self.BOX_HORIZONTAL * self.width}"

    def format_hunk(self, file: DiffFile, hunk: DiffHunk) -> str:
        """Format a single diff hunk with inline line numbers."""
        output_lines = []

        # Track current line number in the new file
        current_line = hunk.new_start

        # Track groups of deletions for proper line number display
        in_deletion_group = False
        deletion_group_first = True

        for line in hunk.lines:
            if not line:
                # Empty line in diff = context line that was empty
                formatted = self._format_context_line(current_line, '')
                output_lines.append(formatted)
                current_line += 1
                in_deletion_group = False
                deletion_group_first = True
                continue

            prefix = line[0] if line else ' '
            content = line[1:] if len(line) > 1 else ''

            if prefix == '-':
                # Removed line - show line number only for first in group
                if deletion_group_first:
                    formatted = self._format_removed_line(current_line, content)
                    deletion_group_first = False
                else:
                    formatted = self._format_removed_line(None, content)
                in_deletion_group = True
                # Do NOT increment current_line for removed lines
                output_lines.append(formatted)
            elif prefix == '+':
                # Added line - no line number (they replace at same position)
                formatted = self._format_added_line(content)
                output_lines.append(formatted)
                current_line += 1
                in_deletion_group = False
                deletion_group_first = True
            else:
                # Context line
                formatted = self._format_context_line(current_line, content)
                output_lines.append(formatted)
                current_line += 1
                in_deletion_group = False
                deletion_group_first = True

        return '\n'.join(output_lines)

    def _format_line_num(self, line_num: Optional[int]) -> str:
        """Format line number column (5 chars right-aligned)."""
        if line_num is None:
            return ' ' * self.LINE_NUM_WIDTH
        return f"{line_num:>{self.LINE_NUM_WIDTH}}"

    def _format_context_line(self, line_num: int, content: str) -> str:
        """Format a context line: '    1 │   content'"""
        line_str = self._format_line_num(line_num)
        return f"{line_str} {self.BOX_VERTICAL}   {content}"

    def _format_removed_line(self, line_num: Optional[int], content: str) -> str:
        """Format a removed line: '    1 | - content' or '      | - content'"""
        line_str = self._format_line_num(line_num)
        return f"{line_str} {self.BOX_VERTICAL} - {content}"

    def _format_added_line(self, content: str) -> str:
        """Format an added line: '      | + content'"""
        line_str = self._format_line_num(None)
        return f"{line_str} {self.BOX_VERTICAL} + {content}"

    def format(self, files: list, stats: DiffStats) -> str:
        """Format the complete diff output."""
        output = []

        # Header
        output.append(self.format_header(stats))

        # Process each file
        for file in files:
            output.append(self.format_file_header(file.display_path))

            if file.is_binary:
                output.append("  [Binary file - cannot display diff]")
            else:
                # Format all hunks for this file
                for i, hunk in enumerate(file.hunks):
                    if i > 0:
                        # Add blank line between hunks
                        output.append(f"{' ' * self.LINE_NUM_WIDTH} {self.BOX_VERTICAL} ")
                    output.append(self.format_hunk(file, hunk))

            output.append(self.format_file_footer())

        return '\n'.join(output)


class DiffParser:
    """Parse unified diff format."""

    # Regex patterns for parsing diff
    FILE_HEADER = re.compile(r'^diff --git a/(.+) b/(.+)$')
    OLD_FILE = re.compile(r'^--- (?:a/)?(.+)$')
    NEW_FILE = re.compile(r'^\+\+\+ (?:b/)?(.+)$')
    HUNK_HEADER = re.compile(r'^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$')
    BINARY_FILE = re.compile(r'^Binary files .+ differ$')
    NEW_FILE_MODE = re.compile(r'^new file mode')
    DELETED_FILE_MODE = re.compile(r'^deleted file mode')

    def parse(self, diff_text: str) -> tuple:
        """Parse diff text and return (files, stats)."""
        files = []
        stats = DiffStats()
        current_file = None
        current_hunk = None

        lines = diff_text.split('\n')
        i = 0

        while i < len(lines):
            line = lines[i]

            # Check for file header
            match = self.FILE_HEADER.match(line)
            if match:
                if current_file:
                    if current_hunk:
                        current_file.hunks.append(current_hunk)
                    if current_file.hunks or current_file.is_binary:
                        files.append(current_file)
                        stats.files_changed += 1

                current_file = DiffFile(
                    old_path=match.group(1),
                    new_path=match.group(2)
                )
                current_hunk = None
                i += 1
                continue

            # Check for new/deleted file markers
            if current_file:
                if self.NEW_FILE_MODE.match(line):
                    current_file.is_new = True
                    i += 1
                    continue
                if self.DELETED_FILE_MODE.match(line):
                    current_file.is_deleted = True
                    i += 1
                    continue

            # Check for binary file
            if self.BINARY_FILE.match(line):
                if current_file:
                    current_file.is_binary = True
                i += 1
                continue

            # Check for old/new file names (update paths if needed)
            old_match = self.OLD_FILE.match(line)
            if old_match and current_file:
                path = old_match.group(1)
                if path != '/dev/null':
                    current_file.old_path = path
                i += 1
                continue

            new_match = self.NEW_FILE.match(line)
            if new_match and current_file:
                path = new_match.group(1)
                if path != '/dev/null':
                    current_file.new_path = path
                i += 1
                continue

            # Check for hunk header
            hunk_match = self.HUNK_HEADER.match(line)
            if hunk_match and current_file:
                if current_hunk:
                    current_file.hunks.append(current_hunk)

                current_hunk = DiffHunk(
                    old_start=int(hunk_match.group(1)),
                    old_count=int(hunk_match.group(2) or 1),
                    new_start=int(hunk_match.group(3)),
                    new_count=int(hunk_match.group(4) or 1),
                    section_header=hunk_match.group(5).strip()
                )
                i += 1
                continue

            # Collect hunk lines
            if current_hunk is not None:
                if line.startswith('+') and not line.startswith('+++'):
                    current_hunk.lines.append(line)
                    stats.lines_added += 1
                elif line.startswith('-') and not line.startswith('---'):
                    current_hunk.lines.append(line)
                    stats.lines_removed += 1
                elif line.startswith(' ') or line == '':
                    current_hunk.lines.append(line)

            i += 1

        # Add final file and hunk
        if current_file:
            if current_hunk:
                current_file.hunks.append(current_hunk)
            if current_file.hunks or current_file.is_binary:
                files.append(current_file)
                stats.files_changed += 1

        return files, stats


def load_config(project_dir: Optional[str] = None) -> dict:
    """Load configuration from cat-config.json."""
    search_paths = []

    if project_dir:
        search_paths.append(Path(project_dir) / '.claude' / 'cat' / 'cat-config.json')

    # Try current directory and parents
    current = Path.cwd()
    while current != current.parent:
        search_paths.append(current / '.claude' / 'cat' / 'cat-config.json')
        current = current.parent

    for config_path in search_paths:
        if config_path.exists():
            try:
                with open(config_path) as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError):
                continue

    return {}


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description='Convert git diff output to variant 2 format',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  git diff | variant2-diff.py
  git diff HEAD~3 | variant2-diff.py --task-name "feature-xyz"
  variant2-diff.py --file changes.patch --width 80
        '''
    )

    parser.add_argument('--task-name', '-t', default='',
                       help='Task name for the header (default: "diff")')
    parser.add_argument('--width', '-w', type=int, default=None,
                       help='Terminal width (default: from cat-config.json or 50)')
    parser.add_argument('--file', '-f', type=str, default=None,
                       help='Read diff from file instead of stdin')
    parser.add_argument('--project-dir', '-p', type=str, default=None,
                       help='Project directory for config lookup')

    args = parser.parse_args()

    # Load config
    config = load_config(args.project_dir)

    # Determine terminal width
    width = args.width or config.get('terminalWidth', 50)

    # Read diff input
    if args.file:
        try:
            with open(args.file) as f:
                diff_text = f.read()
        except IOError as e:
            print(f"Error reading file: {e}", file=sys.stderr)
            sys.exit(1)
    else:
        # Check if stdin has data
        if sys.stdin.isatty():
            print("No diff input. Pipe git diff output or use --file option.",
                  file=sys.stderr)
            print("Use --help for usage information.", file=sys.stderr)
            sys.exit(1)
        diff_text = sys.stdin.read()

    # Handle empty diff
    if not diff_text.strip():
        print("No changes to display.", file=sys.stderr)
        sys.exit(0)

    # Parse diff
    parser_inst = DiffParser()
    files, stats = parser_inst.parse(diff_text)

    if not files:
        print("No parseable changes found.", file=sys.stderr)
        sys.exit(0)

    # Format and print output
    formatter = Variant2Formatter(width=width, task_name=args.task_name)
    print(formatter.format(files, stats))


if __name__ == '__main__':
    main()
