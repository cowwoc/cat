"""
Handler for /cat:status precomputation.

This handler reuses the logic from get-status-output.py.
"""

import json
import os
import subprocess
import sys
from pathlib import Path

from . import register_handler

# Add plugin scripts directory to path for imports
SCRIPT_DIR = Path(__file__).parent.parent
PLUGIN_ROOT = SCRIPT_DIR.parent
sys.path.insert(0, str(PLUGIN_ROOT / 'scripts'))

try:
    from build_box_lines import display_width, build_line, build_border
    HAS_BOX_UTILS = True
except ImportError:
    HAS_BOX_UTILS = False


def build_progress_bar(percent: int, width: int = 25) -> str:
    """Build a progress bar string."""
    filled = percent * width // 100
    empty = width - filled
    return '\u2588' * filled + '\u2591' * empty


def build_inner_box(header: str, content_items: list, forced_width: int = None) -> list:
    """Build an inner box with header and content."""
    if not content_items:
        content_items = ['']

    content_widths = [display_width(c) for c in content_items]
    max_content_width = max(content_widths) if content_widths else 0

    header_width = display_width(header)
    header_min_width = header_width + 1

    inner_max = max(header_min_width, max_content_width)
    if forced_width is not None:
        inner_max = max(inner_max, forced_width)

    remaining = inner_max - header_width - 1
    if remaining < 0:
        remaining = 0
    dashes = '\u2500' * remaining if remaining > 0 else ''
    inner_top = f"\u256d\u2500 {header} {dashes}\u256e"

    lines = [build_line(c, inner_max) for c in content_items]
    inner_bottom = build_border(inner_max, is_top=False)

    return [inner_top] + lines + [inner_bottom]


class StatusHandler:
    """Handler for /cat:status skill."""

    def handle(self, context: dict) -> str | None:
        """Run status computation and return result."""
        if not HAS_BOX_UTILS:
            return None

        project_root = context.get("project_root")
        plugin_root = context.get("plugin_root")

        if not project_root:
            return None

        cat_dir = Path(project_root) / ".claude" / "cat"
        if not cat_dir.is_dir():
            return None

        status_script = Path(plugin_root) / "scripts" / "get-status-data.sh"
        if not status_script.exists():
            return None

        # Run status script to get JSON data
        try:
            result = subprocess.run(
                [str(status_script), str(cat_dir)],
                capture_output=True,
                text=True,
                timeout=30
            )
            status_json = json.loads(result.stdout)
        except (subprocess.TimeoutExpired, json.JSONDecodeError, Exception):
            return None

        if 'error' in status_json:
            return None

        # Extract data
        project = status_json.get('project', 'Project')
        percent = status_json.get('overall', {}).get('percent', 0)
        completed = status_json.get('overall', {}).get('completed', 0)
        total = status_json.get('overall', {}).get('total', 0)
        active_minor = status_json.get('current', {}).get('minor', 'none')
        pending_tasks = status_json.get('current', {}).get('pendingTasks', [])
        pending_count = len(pending_tasks)

        # Build progress bar
        progress_bar = build_progress_bar(percent)

        # Build content
        content_items = []
        content_items.append(f"\U0001F4CA Overall: [{progress_bar}] {percent}%")
        content_items.append(f"\U0001F3C6 {completed}/{total} tasks complete")
        content_items.append('')

        # Inner boxes for major versions
        majors = status_json.get('majors', [])
        minors = status_json.get('minors', [])

        for major in majors:
            major_id = major.get('id', '')
            major_name = major.get('name', '')

            major_minors = [m for m in minors if m.get('major') == major_id]

            inner_content = []
            for minor in major_minors:
                minor_id = minor.get('id', '')
                minor_desc = minor.get('description', '')
                minor_completed = minor.get('completed', 0)
                minor_total = minor.get('total', 0)
                minor_in_progress = minor.get('inProgress', '')

                if minor_completed == minor_total and minor_total > 0:
                    emoji = '\u2611\ufe0f'
                elif minor_in_progress or minor_id == active_minor:
                    emoji = '\U0001F504'
                else:
                    emoji = '\U0001F533'

                if minor_desc:
                    inner_content.append(f"{emoji} {minor_id}: {minor_desc} ({minor_completed}/{minor_total})")
                else:
                    inner_content.append(f"{emoji} {minor_id}: ({minor_completed}/{minor_total})")

                if minor_id == active_minor:
                    for task in pending_tasks:
                        if len(task) > 25:
                            task = task[:22] + '...'
                        inner_content.append(f"   \U0001F533 {task}")

            header = f"\U0001F4E6 {major_id}: {major_name}"
            inner_box_lines = build_inner_box(header, inner_content)
            content_items.extend(inner_box_lines)
            content_items.append('')

        content_items.append(f"\U0001F3AF Active: {active_minor}")
        content_items.append(f"\U0001F4CB Available: {pending_count} pending tasks")

        # Compute outer box
        content_widths = [display_width(c) for c in content_items]
        max_content_width = max(content_widths) if content_widths else 0

        outer_top = build_border(max_content_width, is_top=True)
        outer_lines = [build_line(c, max_content_width) for c in content_items]
        outer_bottom = build_border(max_content_width, is_top=False)

        final_box = outer_top + '\n'
        for line in outer_lines:
            final_box += line + '\n'
        final_box += outer_bottom

        return f"""PRE-COMPUTED STATUS DISPLAY (copy exactly):

{final_box}

NEXT STEPS table:
| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | `/cat:work {active_minor}-<task-name>` |
| [**2**] | Add new task | `/cat:add` |

Legend: \u2611\ufe0f Completed \u00b7 \U0001F504 In Progress \u00b7 \U0001F533 Pending \u00b7 \U0001F6AB Blocked \u00b7 \U0001F6A7 Gate Waiting

INSTRUCTION: Output the above box and tables EXACTLY as shown. Do not recalculate."""


# Register handler
_handler = StatusHandler()
register_handler("status", _handler)
