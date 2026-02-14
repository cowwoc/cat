#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
Compute token report table with deterministic alignment.

Reads session data and produces a output template table for the token-report skill.
Emoji indicators are placed INSIDE the Context column for proper alignment.

Usage:
  SESSION_FILE=/path/to/session.jsonl compute-token-table.py
  compute-token-table.py /path/to/session.jsonl

Output:
  JSON with {"lines": [...], "summary": {...}}

Column widths:
  Type: 17, Description: 30, Tokens: 8, Context: 16, Duration: 10
"""

import sys
import os
import json
import subprocess
from typing import List, Dict, Any, Tuple

# Add lib directory to path for shared imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'lib'))
from emoji_widths import display_width, get_emoji_widths

# Column widths (fixed)
COL_TYPE = 17
COL_DESC = 30
COL_TOKENS = 8
COL_CONTEXT = 16  # Must fit "85% " + emoji (display width 2) + padding
COL_DURATION = 10

# Load emoji widths for current terminal (cached at module level)
_emoji_widths = None

def _get_widths():
    """Get cached emoji widths dict."""
    global _emoji_widths
    if _emoji_widths is None:
        _emoji_widths = get_emoji_widths()
    return _emoji_widths


def format_tokens(n: int) -> str:
    """Format token count: 68400 -> '68.4k', 1500000 -> '1.5M'."""
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    elif n >= 1000:
        return f"{n / 1000:.1f}k"
    else:
        return str(n)


def format_duration(ms: int) -> str:
    """Format duration: 67000 -> '1m 7s'."""
    secs = ms // 1000
    if secs >= 60:
        mins = secs // 60
        remaining_secs = secs % 60
        return f"{mins}m {remaining_secs}s"
    else:
        return f"{secs}s"


def context_status(tokens: int, limit: int = 200000) -> Tuple[str, str]:
    """
    Returns (display_text, warning_indicator).

    Warning indicators are INSIDE the Context column:
    - < 40%: "34%" (no indicator)
    - >= 40% and < 80%: "45% " + warning emoji
    - >= 80%: "85% " + critical emoji
    """
    pct = (tokens * 100) // limit
    if pct >= 80:
        return f"{pct}% ", "critical"  # Will add emoji in cell
    elif pct >= 40:
        return f"{pct}% ", "warning"   # Will add emoji in cell
    else:
        return f"{pct}%", "ok"


def pad_cell(content: str, width: int, align: str = "left") -> str:
    """Pad content to exact display width."""
    widths = _get_widths()
    content_width = display_width(content, widths)
    padding = width - content_width
    if padding < 0:
        padding = 0

    if align == "right":
        return " " * padding + content
    else:
        return content + " " * padding


def truncate(text: str, max_width: int) -> str:
    """Truncate text to max display width, adding ... if needed."""
    widths = _get_widths()
    if display_width(text, widths) <= max_width:
        return text

    # Truncate character by character using shared width calculation
    result = ""
    target_width = max_width - 3  # Reserve space for "..."

    # Build result one character at a time, checking width
    for i, char in enumerate(text):
        test_result = result + char
        if display_width(test_result, widths) > target_width:
            break
        result = test_result

    return result + "..."


def extract_subagent_data(session_file: str) -> Tuple[List[Dict[str, Any]], int, int]:
    """
    Extract subagent data from session file using jq.

    Returns: (subagent_list, total_tokens, total_duration_ms)
    """
    if not os.path.exists(session_file):
        return [], 0, 0

    # Use jq to extract Task tool calls and their results
    jq_query = '''
      . as $all |
      [range(length)] |
      map(
        . as $i |
        $all[$i] |
        select(.type == "assistant" and .message.content[]?.type == "tool_use" and .message.content[]?.name == "Task") |
        {
          index: $i,
          task: (.message.content[] | select(.type == "tool_use" and .name == "Task")),
          id: (.message.content[] | select(.type == "tool_use" and .name == "Task") | .id)
        }
      ) |
      map(
        . as $task |
        ($all | map(select(.type == "user" and .toolUseResult.tool_use_id == $task.id)) | first) as $result |
        {
          type: ($task.task.input.prompt | split("\\n")[0] | gsub("^## "; "") | .[0:25]),
          description: (($task.task.input.description // "Subagent task") | .[0:28]),
          tokens: ($result.toolUseResult.totalTokens // 0),
          duration_ms: ($result.toolUseResult.durationMs // 0)
        }
      ) |
      map(select(.tokens > 0))
    '''

    try:
        result = subprocess.run(
            ['jq', '-s', jq_query, session_file],
            capture_output=True,
            text=True,
            timeout=30
        )

        if result.returncode != 0:
            return [], 0, 0

        data = json.loads(result.stdout)

        total_tokens = sum(item.get('tokens', 0) for item in data)
        total_duration = sum(item.get('duration_ms', 0) for item in data)

        return data, total_tokens, total_duration

    except (subprocess.TimeoutExpired, json.JSONDecodeError, Exception):
        return [], 0, 0


def build_table_lines(subagent_data: List[Dict[str, Any]], total_tokens: int, total_duration: int) -> List[str]:
    """Build all table lines with exact formatting."""
    lines = []

    # Calculate total inner width (columns + separators)
    # Format: │ col1 │ col2 │ col3 │ col4 │ col5 │
    # Inner width = sum(col_widths) + 4 separators (│) + 10 spaces (2 per column padding)
    total_width = COL_TYPE + COL_DESC + COL_TOKENS + COL_CONTEXT + COL_DURATION + 14

    # Top border
    top = "╭" + "─" * (COL_TYPE + 2) + "┬" + "─" * (COL_DESC + 2) + "┬" + "─" * (COL_TOKENS + 2) + "┬" + "─" * (COL_CONTEXT + 2) + "┬" + "─" * (COL_DURATION + 2) + "╮"
    lines.append(top)

    # Header row
    header = "│ " + pad_cell("Type", COL_TYPE) + " │ " + pad_cell("Description", COL_DESC) + " │ " + pad_cell("Tokens", COL_TOKENS) + " │ " + pad_cell("Context", COL_CONTEXT) + " │ " + pad_cell("Duration", COL_DURATION) + " │"
    lines.append(header)

    # Header divider
    divider = "├" + "─" * (COL_TYPE + 2) + "┼" + "─" * (COL_DESC + 2) + "┼" + "─" * (COL_TOKENS + 2) + "┼" + "─" * (COL_CONTEXT + 2) + "┼" + "─" * (COL_DURATION + 2) + "┤"
    lines.append(divider)

    # Data rows
    for item in subagent_data:
        type_val = truncate(item.get('type', ''), COL_TYPE)
        desc_val = truncate(item.get('description', ''), COL_DESC)
        tokens_val = format_tokens(item.get('tokens', 0))
        duration_val = format_duration(item.get('duration_ms', 0))

        # Context with emoji indicator INSIDE
        context_text, status = context_status(item.get('tokens', 0))
        if status == "critical":
            context_content = context_text + ""  # critical emoji
        elif status == "warning":
            context_content = context_text + "⚠️"   # warning emoji
        else:
            context_content = context_text

        row = "│ " + pad_cell(type_val, COL_TYPE) + " │ " + pad_cell(desc_val, COL_DESC) + " │ " + pad_cell(tokens_val, COL_TOKENS) + " │ " + pad_cell(context_content, COL_CONTEXT) + " │ " + pad_cell(duration_val, COL_DURATION) + " │"
        lines.append(row)

    # Footer divider
    lines.append(divider)

    # Total row
    total_tokens_str = format_tokens(total_tokens)
    total_duration_str = format_duration(total_duration)
    total_row = "│ " + pad_cell("", COL_TYPE) + " │ " + pad_cell("TOTAL", COL_DESC) + " │ " + pad_cell(total_tokens_str, COL_TOKENS) + " │ " + pad_cell("-", COL_CONTEXT) + " │ " + pad_cell(total_duration_str, COL_DURATION) + " │"
    lines.append(total_row)

    # Bottom border
    bottom = "╰" + "─" * (COL_TYPE + 2) + "┴" + "─" * (COL_DESC + 2) + "┴" + "─" * (COL_TOKENS + 2) + "┴" + "─" * (COL_CONTEXT + 2) + "┴" + "─" * (COL_DURATION + 2) + "╯"
    lines.append(bottom)

    return lines


def main():
    # Get session file from env or command line
    session_file = os.environ.get('SESSION_FILE')
    if not session_file and len(sys.argv) > 1:
        session_file = sys.argv[1]

    if not session_file:
        result = {
            "error": "No session file provided. Set SESSION_FILE env var or pass as argument.",
            "lines": [],
            "summary": {"total_tokens": 0, "total_duration_ms": 0, "subagent_count": 0}
        }
        print(json.dumps(result, ensure_ascii=False))
        sys.exit(1)

    if not os.path.exists(session_file):
        result = {
            "error": f"Session file not found: {session_file}",
            "lines": [],
            "summary": {"total_tokens": 0, "total_duration_ms": 0, "subagent_count": 0}
        }
        print(json.dumps(result, ensure_ascii=False))
        sys.exit(1)

    # Extract data
    subagent_data, total_tokens, total_duration = extract_subagent_data(session_file)

    # Build table
    lines = build_table_lines(subagent_data, total_tokens, total_duration)

    # Build result
    result = {
        "lines": lines,
        "summary": {
            "total_tokens": total_tokens,
            "total_duration_ms": total_duration,
            "subagent_count": len(subagent_data)
        }
    }

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
