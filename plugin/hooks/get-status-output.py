#!/usr/bin/env python3
"""Pre-compute status display before skill runs.

TRIGGER: UserPromptSubmit

When user invokes /cat:status, this hook:
1. Runs the get-status-data.sh script to collect project data
2. Computes the exact box lines using imported Python functions
3. Returns the pre-rendered display via additionalContext

The skill then just outputs the pre-computed result - no agent computation needed.
"""

import json
import os
import re
import subprocess
import sys

# Add scripts directory to path for imports
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PLUGIN_ROOT = os.path.dirname(SCRIPT_DIR)
sys.path.insert(0, os.path.join(PLUGIN_ROOT, 'scripts'))

from build_box_lines import display_width, build_line, build_border


def output_hook_message(event: str, message: str) -> None:
    """Output standardized hook message in JSON format."""
    result = {
        "hookSpecificOutput": {
            "hookEventName": event,
            "additionalContext": message
        }
    }
    print(json.dumps(result, ensure_ascii=False))


def init_hook(timeout_secs: int = 5) -> dict:
    """Initialize hook by reading JSON from stdin.

    Returns the parsed JSON dict, or empty dict on failure.
    Sets global USER_PROMPT for convenience.
    """
    global USER_PROMPT, SESSION_ID, HOOK_EVENT

    # Check if stdin is available
    if sys.stdin.isatty():
        return {}

    try:
        hook_json_str = sys.stdin.read()
        hook_json = json.loads(hook_json_str)
    except (json.JSONDecodeError, Exception):
        return {}

    # Extract common fields
    HOOK_EVENT = hook_json.get('hook_event_name', '')
    SESSION_ID = hook_json.get('session_id', '')
    USER_PROMPT = hook_json.get('message', '') or hook_json.get('user_message', '') or hook_json.get('prompt', '')

    return hook_json


def build_progress_bar(percent: int, width: int = 25) -> str:
    """Build a progress bar string."""
    filled = percent * width // 100
    empty = width - filled
    return '\u2588' * filled + '\u2591' * empty  # Full block + light shade


def build_inner_box(header: str, content_items: list, forced_width: int = None) -> list:
    """Build an inner box with header and content.

    Returns list of lines including top border, content lines, and bottom border.
    """
    if not content_items:
        content_items = ['']

    # Calculate widths
    content_widths = [display_width(c) for c in content_items]
    max_content_width = max(content_widths) if content_widths else 0

    # Header width calculation
    header_width = display_width(header)
    header_min_width = header_width + 1  # +1 for trailing space before dashes

    # Use the larger of header width or content width
    inner_max = max(header_min_width, max_content_width)
    if forced_width is not None:
        inner_max = max(inner_max, forced_width)

    # Build header line: top border with embedded header
    remaining = inner_max - header_width - 1
    if remaining < 0:
        remaining = 0
    dashes = '\u2500' * remaining if remaining > 0 else ''
    inner_top = f"\u256d\u2500 {header} {dashes}\u256e"

    # Build content lines
    lines = [build_line(c, inner_max) for c in content_items]

    # Build bottom border
    inner_bottom = build_border(inner_max, is_top=False)

    return [inner_top] + lines + [inner_bottom]


def main():
    # Initialize hook
    hook_json = init_hook()
    if not hook_json:
        print('{}')
        sys.exit(0)

    # Check if this is a /cat:status command
    user_prompt = USER_PROMPT.strip()
    if not re.match(r'^/?cat:status\s*$', user_prompt):
        print('{}')
        sys.exit(0)

    # Find project root (look for .claude/cat directory)
    project_root = None
    claude_project_dir = os.environ.get('CLAUDE_PROJECT_DIR', '')

    if claude_project_dir and os.path.isdir(os.path.join(claude_project_dir, '.claude/cat')):
        project_root = claude_project_dir
    elif os.path.isdir('.claude/cat'):
        project_root = os.getcwd()
    else:
        # No CAT project found, let the skill handle the error
        print('{}')
        sys.exit(0)

    # Find status data script
    status_script = os.path.join(PLUGIN_ROOT, 'scripts', 'get-status-data.sh')

    if not os.path.isfile(status_script):
        print('{}')
        sys.exit(0)

    # Run status script to get JSON data
    try:
        result = subprocess.run(
            [status_script, os.path.join(project_root, '.claude/cat')],
            capture_output=True,
            text=True,
            timeout=30
        )
        status_json = json.loads(result.stdout)
    except (subprocess.TimeoutExpired, json.JSONDecodeError, Exception):
        print('{}')
        sys.exit(0)

    # Check for error in status output
    if 'error' in status_json:
        print('{}')
        sys.exit(0)

    # Extract data from status JSON
    project = status_json.get('project', 'Project')
    percent = status_json.get('overall', {}).get('percent', 0)
    completed = status_json.get('overall', {}).get('completed', 0)
    total = status_json.get('overall', {}).get('total', 0)
    active_minor = status_json.get('current', {}).get('minor', 'none')
    pending_tasks = status_json.get('current', {}).get('pendingTasks', [])
    pending_count = len(pending_tasks)

    # Build progress bar
    progress_bar = build_progress_bar(percent)

    # Build content items for the outer box
    content_items = []
    content_items.append(f"\U0001F4CA Overall: [{progress_bar}] {percent}%")  # chart emoji
    content_items.append(f"\U0001F3C6 {completed}/{total} tasks complete")  # trophy emoji
    content_items.append('')  # blank line

    # Build inner boxes for each major version
    majors = status_json.get('majors', [])
    minors = status_json.get('minors', [])

    for major in majors:
        major_id = major.get('id', '')
        major_name = major.get('name', '')

        # Get minors for this major
        major_minors = [m for m in minors if m.get('major') == major_id]

        inner_content = []
        for minor in major_minors:
            minor_id = minor.get('id', '')
            minor_desc = minor.get('description', '')
            minor_completed = minor.get('completed', 0)
            minor_total = minor.get('total', 0)
            minor_in_progress = minor.get('inProgress', '')

            # Determine emoji
            if minor_completed == minor_total and minor_total > 0:
                emoji = '\u2611\ufe0f'  # checked box
            elif minor_in_progress or minor_id == active_minor:
                emoji = '\U0001F504'  # rotating arrows
            else:
                emoji = '\U0001F533'  # white square button

            # Format: emoji version: description (completed/total)
            if minor_desc:
                inner_content.append(f"{emoji} {minor_id}: {minor_desc} ({minor_completed}/{minor_total})")
            else:
                inner_content.append(f"{emoji} {minor_id}: ({minor_completed}/{minor_total})")

            # If this is the active minor, show pending tasks
            if minor_id == active_minor:
                for task in pending_tasks:
                    # Truncate long task names
                    if len(task) > 25:
                        task = task[:22] + '...'
                    inner_content.append(f"   \U0001F533 {task}")

        # Build inner box header text: package v1: Name
        header = f"\U0001F4E6 {major_id}: {major_name}"

        # Build inner box and add to outer content
        inner_box_lines = build_inner_box(header, inner_content)
        content_items.extend(inner_box_lines)
        content_items.append('')  # blank line between majors

    # Add footer
    content_items.append(f"\U0001F3AF Active: {active_minor}")  # dart emoji
    content_items.append(f"\U0001F4CB Available: {pending_count} pending tasks")  # clipboard emoji

    # Compute outer box
    content_widths = [display_width(c) for c in content_items]
    max_content_width = max(content_widths) if content_widths else 0

    outer_top = build_border(max_content_width, is_top=True)
    outer_lines = [build_line(c, max_content_width) for c in content_items]
    outer_bottom = build_border(max_content_width, is_top=False)

    # Assemble complete box
    final_box = outer_top + '\n'
    for line in outer_lines:
        final_box += line + '\n'
    final_box += outer_bottom

    # Build the complete output message
    output = f"""PRE-COMPUTED STATUS DISPLAY (copy exactly):

{final_box}

NEXT STEPS table:
| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | `/cat:work {active_minor}-<task-name>` |
| [**2**] | Add new task | `/cat:add` |

Legend: \u2611\ufe0f Completed \u00b7 \U0001F504 In Progress \u00b7 \U0001F533 Pending \u00b7 \U0001F6AB Blocked \u00b7 \U0001F6A7 Gate Waiting

INSTRUCTION: Output the above box and tables EXACTLY as shown. Do not recalculate."""

    output_hook_message("UserPromptSubmit", output)
    sys.exit(0)


if __name__ == "__main__":
    main()
