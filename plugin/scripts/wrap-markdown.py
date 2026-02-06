#!/usr/bin/env python3
"""
Wrap markdown files to 120 characters while preserving special content.

Preserves:
- Code blocks (``` fenced)
- Markdown tables (| delimited rows)
- YAML frontmatter (--- delimited)
- Lines with box-drawing characters
- Bare URLs (lines that are purely a URL)
- Predominantly HTML lines (start with < and end with >)

Wraps:
- Regular prose
- Bullet points and numbered lists (maintaining indentation)
"""

import re
import sys
from pathlib import Path
from typing import List


def is_table_line(line: str) -> bool:
    """Check if line is a markdown table row."""
    stripped = line.lstrip()
    return stripped.startswith('|') and '|' in stripped[1:]


def is_yaml_delimiter(line: str) -> bool:
    """Check if line is YAML frontmatter delimiter."""
    return line.strip() == '---'


def has_box_drawing_chars(line: str) -> bool:
    """Check if line contains box-drawing characters."""
    box_chars = '╭╮╰╯│─├┤┌┐└┘┬┴┼╔╗╚╝║═╠╣╦╩╬'
    return any(c in line for c in box_chars)


def is_bare_url(line: str) -> bool:
    """Check if line is PURELY a URL (entire line is just a URL)."""
    stripped = line.strip()
    # Match lines that are just a URL with optional markdown link text
    url_pattern = r'^<?https?://[^\s>]+>?$'
    return bool(re.match(url_pattern, stripped))


def is_predominantly_html(line: str) -> bool:
    """Check if line is predominantly HTML (starts with < and ends with >)."""
    stripped = line.strip()
    return stripped.startswith('<') and stripped.endswith('>')


def get_list_indent(line: str) -> tuple:
    """
    Get the indent level and marker for a list item.
    Returns (indent_str, marker, content) or (None, None, None) if not a list.
    """
    # Match bullet lists: -, *, +
    match = re.match(r'^(\s*)([-*+])\s+(.*)$', line)
    if match:
        return match.group(1), match.group(2) + ' ', match.group(3)

    # Match numbered lists: 1., 2., etc.
    match = re.match(r'^(\s*)(\d+\.)\s+(.*)$', line)
    if match:
        return match.group(1), match.group(2) + ' ', match.group(3)

    return None, None, None


def would_create_list_marker(word: str, next_word: str = None) -> bool:
    """
    Check if a word would create a list marker when placed at the start of a continuation line.

    Returns True if:
    - The word is a single list marker character (-, *, +) that will be followed by another word
    - The word starts with a list marker pattern: "- ", "* ", "+ ", "1. ", etc.
    """
    # Single character list markers that will combine with next word
    if word in ['-', '*', '+'] and next_word:
        return True
    # Word already starts with list marker pattern
    if word.startswith('- ') or word.startswith('* ') or word.startswith('+ '):
        return True
    # Check for numbered list pattern: digit(s) followed by dot (with or without space)
    if re.match(r'^\d+\.', word):
        return True
    return False


def wrap_line(line: str, max_width: int = 120, continuation_indent: str = '', avoid_list_markers: bool = False) -> List[str]:
    """
    Wrap a single line to max_width characters.

    Args:
        line: The line to wrap
        max_width: Maximum line width (default 120)
        continuation_indent: Indent to use for continuation lines
        avoid_list_markers: If True, avoid breaking at words that would create list markers

    Returns:
        List of wrapped lines
    """
    if len(line) <= max_width:
        return [line]

    # Don't wrap if it has special content
    if (is_table_line(line) or has_box_drawing_chars(line) or
        is_bare_url(line) or is_predominantly_html(line)):
        return [line]

    # Word wrap
    words = line.split()
    if not words:
        return [line]

    lines = []
    current_line = words[0]

    for i, word in enumerate(words[1:], start=1):
        # Check if adding this word would exceed max_width
        test_line = current_line + ' ' + word
        if len(test_line) <= max_width:
            current_line = test_line
        else:
            # Check if starting a continuation line with this word would create a list marker
            next_word = words[i + 1] if i + 1 < len(words) else None
            if avoid_list_markers and would_create_list_marker(word, next_word):
                # Break one word earlier so the list-marker word isn't at line start
                # Find the last space in current_line to split off the last word
                last_space = current_line.rfind(' ')
                if last_space > 0:
                    lines.append(current_line[:last_space])
                    current_line = continuation_indent + current_line[last_space + 1:] + ' ' + word
                else:
                    # Can't break earlier - keep on current line as fallback
                    current_line = test_line
            else:
                # Start a new line
                lines.append(current_line)
                current_line = continuation_indent + word

    # Add the last line
    if current_line:
        lines.append(current_line)

    return lines


def wrap_markdown(content: str, max_width: int = 120) -> str:
    """
    Wrap markdown content to max_width characters.

    Args:
        content: The markdown content to wrap
        max_width: Maximum line width (default 120)

    Returns:
        Wrapped markdown content
    """
    lines = content.splitlines(keepends=True)
    result = []

    in_code_block = False
    in_yaml_frontmatter = False
    yaml_delimiter_count = 0

    for line in lines:
        # Track line ending
        has_newline = line.endswith('\n')
        line = line.rstrip('\n\r')

        # Check for code block boundaries
        if line.strip().startswith('```'):
            in_code_block = not in_code_block
            result.append(line)
            if has_newline:
                result.append('\n')
            continue

        # Check for YAML frontmatter boundaries
        if is_yaml_delimiter(line):
            yaml_delimiter_count += 1
            if yaml_delimiter_count == 1:
                in_yaml_frontmatter = True
            elif yaml_delimiter_count == 2:
                in_yaml_frontmatter = False
            result.append(line)
            if has_newline:
                result.append('\n')
            continue

        # Don't wrap inside code blocks or YAML frontmatter
        if in_code_block or in_yaml_frontmatter:
            result.append(line)
            if has_newline:
                result.append('\n')
            continue

        # Don't wrap table lines
        if is_table_line(line):
            result.append(line)
            if has_newline:
                result.append('\n')
            continue

        # Don't wrap empty lines
        if not line.strip():
            result.append(line)
            if has_newline:
                result.append('\n')
            continue

        # Handle list items
        indent, marker, content = get_list_indent(line)
        if indent is not None:
            # This is a list item
            full_line = indent + marker + content
            if len(full_line) <= max_width:
                result.append(full_line)
                if has_newline:
                    result.append('\n')
            else:
                # Need to wrap the list item
                # Continuation indent is the list indent + marker width
                continuation_indent = indent + ' ' * len(marker)
                wrapped = wrap_line(content, max_width - len(indent) - len(marker), '', avoid_list_markers=True)

                # First line gets the marker
                result.append(indent + marker + wrapped[0])
                if has_newline or len(wrapped) > 1:
                    result.append('\n')

                # Continuation lines get indented
                for i, wrapped_line in enumerate(wrapped[1:]):
                    result.append(continuation_indent + wrapped_line)
                    if has_newline or i < len(wrapped) - 2:
                        result.append('\n')
            continue

        # Regular line - wrap if needed
        if len(line) <= max_width:
            result.append(line)
            if has_newline:
                result.append('\n')
        else:
            # Check if line should be preserved as-is
            if has_box_drawing_chars(line) or is_bare_url(line) or is_predominantly_html(line):
                result.append(line)
                if has_newline:
                    result.append('\n')
            else:
                # Preserve leading whitespace
                leading_space = len(line) - len(line.lstrip())
                indent = line[:leading_space]
                content = line[leading_space:]

                wrapped = wrap_line(content, max_width - leading_space, indent)
                for i, wrapped_line in enumerate(wrapped):
                    result.append(indent + wrapped_line if i == 0 else wrapped_line)
                    if has_newline or i < len(wrapped) - 1:
                        result.append('\n')

    return ''.join(result)


def main():
    """Main entry point."""
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <markdown-file>", file=sys.stderr)
        sys.exit(1)

    filepath = Path(sys.argv[1])

    if not filepath.exists():
        print(f"Error: File not found: {filepath}", file=sys.stderr)
        sys.exit(1)

    if not filepath.suffix == '.md':
        print(f"Error: Not a markdown file: {filepath}", file=sys.stderr)
        sys.exit(1)

    # Read the file
    try:
        content = filepath.read_text(encoding='utf-8')
    except Exception as e:
        print(f"Error reading file: {e}", file=sys.stderr)
        sys.exit(1)

    # Wrap the content
    wrapped = wrap_markdown(content)

    # Write back
    try:
        filepath.write_text(wrapped, encoding='utf-8')
        print(f"Wrapped: {filepath}")
    except Exception as e:
        print(f"Error writing file: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
