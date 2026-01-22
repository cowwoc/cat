"""
Handler for /cat:status precomputation.

Collects project status data and renders the display box.
"""

import re
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


def get_task_status(state_file: Path) -> str:
    """Get task status from STATE.md file."""
    if not state_file.exists():
        return "pending"

    try:
        content = state_file.read_text()
    except Exception:
        return "pending"

    # Try "Status: <value>" format
    match = re.search(r'^[Ss]tatus:\s*(.+)$', content, re.MULTILINE)
    if match:
        return match.group(1).strip()

    # Try "- **Status:** <value>" format
    match = re.search(r'^\- \*\*Status:\*\*\s*(.+)$', content, re.MULTILINE)
    if match:
        return match.group(1).strip()

    return "pending"


def collect_status_data(cat_dir: Path) -> dict:
    """Collect project status data from CAT directory structure."""
    if not cat_dir.is_dir():
        return {"error": "No planning structure found. Run /cat:init to initialize."}

    # Get project name
    project_file = cat_dir / "PROJECT.md"
    project_name = "Unknown Project"
    if project_file.exists():
        try:
            content = project_file.read_text()
            match = re.search(r'^# (.+)$', content, re.MULTILINE)
            if match:
                project_name = match.group(1)
        except Exception:
            pass

    # Read roadmap for descriptions
    roadmap_file = cat_dir / "ROADMAP.md"
    roadmap_content = ""
    if roadmap_file.exists():
        try:
            roadmap_content = roadmap_file.read_text()
        except Exception:
            pass

    majors = []
    minors = []
    total_completed = 0
    total_tasks = 0
    current_minor = ""
    in_progress_task = ""
    pending_tasks = []

    # Iterate over major version directories
    for major_dir in sorted(cat_dir.glob("v[0-9]*")):
        if not major_dir.is_dir():
            continue

        major_id = major_dir.name
        major_num = major_id[1:]  # Remove 'v' prefix

        # Get major name from roadmap
        major_name = f"Version {major_num}"
        match = re.search(rf'^## Version {re.escape(major_num)}: (.+)$', roadmap_content, re.MULTILINE)
        if match:
            major_name = match.group(1)

        majors.append({"id": major_id, "name": major_name})

        # Iterate over minor version directories
        for minor_dir in sorted(major_dir.glob("v[0-9]*.[0-9]*")):
            if not minor_dir.is_dir():
                continue

            minor_id = minor_dir.name
            minor_num = minor_id[1:]  # Remove 'v' prefix

            local_completed = 0
            local_total = 0
            local_inprog = ""
            tasks = []

            # Iterate over task directories
            for task_dir in sorted(minor_dir.iterdir()):
                if not task_dir.is_dir():
                    continue

                task_name = task_dir.name
                state_file = task_dir / "STATE.md"
                plan_file = task_dir / "PLAN.md"

                if not state_file.exists() and not plan_file.exists():
                    continue

                status = get_task_status(state_file)
                local_total += 1
                tasks.append({"name": task_name, "status": status})

                if status in ("completed", "done"):
                    local_completed += 1
                elif status in ("in-progress", "active"):
                    local_inprog = task_name

            # Get minor description from roadmap
            desc = ""
            match = re.search(rf'^\- \*\*{re.escape(minor_num)}:\*\*\s*([^(]+)', roadmap_content, re.MULTILINE)
            if match:
                desc = match.group(1).strip()

            total_completed += local_completed
            total_tasks += local_total

            # Determine current minor
            if not current_minor:
                if local_inprog:
                    current_minor = minor_id
                    in_progress_task = local_inprog
                elif local_completed < local_total:
                    current_minor = minor_id

            minors.append({
                "id": minor_id,
                "major": major_id,
                "description": desc,
                "completed": local_completed,
                "total": local_total,
                "inProgress": local_inprog,
                "tasks": tasks
            })

    # Collect pending tasks for current minor
    if current_minor:
        for minor in minors:
            if minor["id"] == current_minor:
                for task in minor["tasks"]:
                    if task["status"] == "pending":
                        pending_tasks.append(task["name"])
                break

    # Calculate percentage
    if total_tasks == 0:
        total_tasks = 1
    percent = total_completed * 100 // total_tasks

    return {
        "project": project_name,
        "overall": {
            "percent": percent,
            "completed": total_completed,
            "total": total_tasks
        },
        "current": {
            "minor": current_minor,
            "inProgressTask": in_progress_task,
            "pendingTasks": pending_tasks
        },
        "majors": majors,
        "minors": minors
    }


class StatusHandler:
    """Handler for /cat:status skill."""

    def handle(self, context: dict) -> str | None:
        """Run status computation and return result."""
        if not HAS_BOX_UTILS:
            return None

        project_root = context.get("project_root")

        if not project_root:
            return None

        cat_dir = Path(project_root) / ".claude" / "cat"
        if not cat_dir.is_dir():
            return None

        # Collect status data directly in Python
        status_data = collect_status_data(cat_dir)

        if 'error' in status_data:
            return None

        # Extract data
        project = status_data.get('project', 'Project')
        percent = status_data.get('overall', {}).get('percent', 0)
        completed = status_data.get('overall', {}).get('completed', 0)
        total = status_data.get('overall', {}).get('total', 0)
        active_minor = status_data.get('current', {}).get('minor', 'none')
        pending_tasks = status_data.get('current', {}).get('pendingTasks', [])
        pending_count = len(pending_tasks)

        # Build progress bar
        progress_bar = build_progress_bar(percent)

        # Build content
        content_items = []
        content_items.append(f"\U0001F4CA Overall: [{progress_bar}] {percent}%")
        content_items.append(f"\U0001F3C6 {completed}/{total} tasks complete")
        content_items.append('')

        # Inner boxes for major versions
        majors = status_data.get('majors', [])
        minors = status_data.get('minors', [])

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
