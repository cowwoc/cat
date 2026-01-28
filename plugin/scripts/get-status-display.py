#!/usr/bin/env python3
"""
Generate project status display for silent preprocessing.

This script produces the complete status display box with correct alignment.
It's called by get-status-display.sh and outputs to stdout.
"""

import argparse
import re
import sys
from pathlib import Path

# Get the script directory
SCRIPT_DIR = Path(__file__).parent

# Add lib to path for emoji_widths import
LIB_PATH = SCRIPT_DIR / "lib"
if str(LIB_PATH) not in sys.path:
    sys.path.insert(0, str(LIB_PATH))

from emoji_widths import EmojiWidths

# Initialize emoji width calculator
_emoji_widths = EmojiWidths()


def display_width(text: str) -> int:
    """Calculate terminal display width of a string."""
    return _emoji_widths.display_width(text)


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
    dashes = '─' * remaining if remaining > 0 else ''
    inner_top = f"╭─ {header} {dashes}╮"

    lines = [build_line(c, inner_max) for c in content_items]
    inner_bottom = build_border(inner_max, is_top=False)

    return [inner_top] + lines + [inner_bottom]


def build_progress_bar(percent: int, width: int = 25) -> str:
    """Build a progress bar string."""
    filled = percent * width // 100
    empty = width - filled
    return '█' * filled + '░' * empty


# Valid status values (canonical)
VALID_STATUSES = {"pending", "in-progress", "completed", "blocked"}

# Status aliases that get normalized to canonical values
STATUS_ALIASES = {
    "complete": "completed",
    "done": "completed",
    "in_progress": "in-progress",
    "active": "in-progress",
}


def get_task_status(state_file: Path) -> str:
    """Get task status from STATE.md file."""
    if not state_file.exists():
        return "pending"

    try:
        content = state_file.read_text()
    except Exception:
        return "pending"

    match = re.search(r'^\- \*\*Status:\*\*\s*(.+)$', content, re.MULTILINE)
    if not match:
        return "pending"

    raw_status = match.group(1).strip().lower()

    if raw_status in VALID_STATUSES:
        return raw_status

    if raw_status in STATUS_ALIASES:
        return STATUS_ALIASES[raw_status]

    # Unknown status - return pending as fallback for script use
    return "pending"


def get_task_dependencies(state_file: Path) -> list:
    """Get task dependencies from STATE.md file."""
    if not state_file.exists():
        return []

    try:
        content = state_file.read_text()
    except Exception:
        return []

    deps = []
    match = re.search(r'^## Dependencies\s*\n((?:- .+\n?)+)', content, re.MULTILINE)
    if match:
        for line in match.group(1).strip().split('\n'):
            dep_match = re.match(r'^- ([a-zA-Z0-9_-]+)', line)
            if dep_match:
                dep_name = dep_match.group(1)
                if dep_name.lower() != 'none':
                    deps.append(dep_name)
        return deps

    match = re.search(r'^\- \*\*Dependencies:\*\*\s*\[([^\]]*)\]', content, re.MULTILINE)
    if match:
        dep_str = match.group(1).strip()
        if dep_str:
            deps = [d.strip() for d in dep_str.split(',') if d.strip()]
        return deps

    return []


def collect_status_data(issues_dir: Path, cat_dir: Path = None) -> dict:
    """Collect project status data from CAT directory structure."""
    if not issues_dir.is_dir():
        return {"error": "No planning structure found. Run /cat:init to initialize."}

    if cat_dir is None:
        cat_dir = issues_dir.parent

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
    next_task = ""

    for major_dir in sorted(issues_dir.glob("v[0-9]*")):
        if not major_dir.is_dir():
            continue

        major_id = major_dir.name
        major_num = major_id[1:]

        major_name = f"Version {major_num}"
        match = re.search(rf'^## Version {re.escape(major_num)}: (.+)$', roadmap_content, re.MULTILINE)
        if match:
            major_name = match.group(1).split('(')[0].strip()

        major_state_file = major_dir / "STATE.md"
        major_status = "pending"
        if major_state_file.exists():
            major_status = get_task_status(major_state_file)

        majors.append({
            "id": major_id,
            "name": major_name,
            "status": major_status
        })

        for minor_dir in sorted(major_dir.glob("v[0-9]*.[0-9]*"), key=lambda p: [int(x) for x in p.name[1:].split('.')]):
            if not minor_dir.is_dir():
                continue

            minor_id = minor_dir.name
            minor_num = minor_id[1:]

            local_completed = 0
            local_total = 0
            local_inprog = ""
            tasks = []
            all_task_statuses = {}

            for task_dir in sorted(minor_dir.iterdir()):
                if not task_dir.is_dir():
                    continue

                task_name = task_dir.name
                state_file = task_dir / "STATE.md"
                plan_file = task_dir / "PLAN.md"

                if not state_file.exists() and not plan_file.exists():
                    continue

                status = get_task_status(state_file)
                all_task_statuses[task_name] = status

            for task_dir in sorted(minor_dir.iterdir()):
                if not task_dir.is_dir():
                    continue

                task_name = task_dir.name
                state_file = task_dir / "STATE.md"
                plan_file = task_dir / "PLAN.md"

                if not state_file.exists() and not plan_file.exists():
                    continue

                status = get_task_status(state_file)
                dependencies = get_task_dependencies(state_file)
                local_total += 1

                blocked_by = []
                for dep in dependencies:
                    dep_status = all_task_statuses.get(dep, "pending")
                    if dep_status != "completed":
                        blocked_by.append(dep)

                tasks.append({
                    "name": task_name,
                    "status": status,
                    "dependencies": dependencies,
                    "blocked_by": blocked_by
                })

                if status == "completed":
                    local_completed += 1
                elif status == "in-progress":
                    local_inprog = task_name

            desc = ""
            match = re.search(rf'^\- \*\*{re.escape(minor_num)}:\*\*\s*([^(]+)', roadmap_content, re.MULTILINE)
            if match:
                desc = match.group(1).strip()

            total_completed += local_completed
            total_tasks += local_total

            if not current_minor:
                if local_inprog:
                    current_minor = minor_id
                    in_progress_task = local_inprog
                elif local_completed < local_total:
                    current_minor = minor_id
                    for task in tasks:
                        if task["status"] == "pending" and not task["blocked_by"]:
                            next_task = task["name"]
                            break
                    if not next_task:
                        for task in tasks:
                            if task["status"] == "pending":
                                next_task = task["name"]
                                break

            minors.append({
                "id": minor_id,
                "major": major_id,
                "description": desc,
                "completed": local_completed,
                "total": local_total,
                "inProgress": local_inprog,
                "tasks": tasks
            })

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
            "nextTask": next_task
        },
        "majors": majors,
        "minors": minors
    }


def collapse_completed_minors(minors: list) -> str:
    """Collapse completed minors into a range string."""
    if not minors:
        return ""

    sorted_minors = sorted(minors, key=lambda m: [int(x) for x in m["id"][1:].split('.')])

    first = sorted_minors[0]["id"]
    last = sorted_minors[-1]["id"]
    total_completed = sum(m["completed"] for m in sorted_minors)
    total_tasks = sum(m["total"] for m in sorted_minors)

    if first == last:
        return f"☑️ {first} ({total_completed}/{total_tasks})"
    else:
        return f"☑️ {first} - {last} ({total_completed}/{total_tasks})"


def generate_status_display(project_dir: str) -> str:
    """Generate the complete status display."""
    cat_dir = Path(project_dir) / ".claude" / "cat"
    if not cat_dir.is_dir():
        return "No CAT project found. Run /cat:init to initialize."

    issues_dir = cat_dir / "issues"
    status_data = collect_status_data(issues_dir)

    if 'error' in status_data:
        return status_data['error']

    percent = status_data.get('overall', {}).get('percent', 0)
    completed = status_data.get('overall', {}).get('completed', 0)
    total = status_data.get('overall', {}).get('total', 0)
    active_minor = status_data.get('current', {}).get('minor', '')
    in_progress_task = status_data.get('current', {}).get('inProgressTask', '')
    next_task = status_data.get('current', {}).get('nextTask', '')
    majors = status_data.get('majors', [])
    minors = status_data.get('minors', [])

    content_items = []

    # Progress bar merged with task count
    progress_bar = build_progress_bar(percent)
    content_items.append(f"📊 Overall: [{progress_bar}] {percent}% · {completed}/{total} tasks")
    content_items.append('')

    # Process each major version
    for major in majors:
        major_id = major.get('id', '')
        major_name = major.get('name', '')
        major_status = major.get('status', 'pending')

        major_minors = [m for m in minors if m.get('major') == major_id]

        all_complete = all(
            m.get('completed', 0) == m.get('total', 0) and m.get('total', 0) > 0
            for m in major_minors
        ) if major_minors else False

        if major_status in ("completed", "done"):
            all_complete = True

        inner_content = []

        if all_complete and major_minors:
            inner_content.append(collapse_completed_minors(major_minors))
        else:
            first_minor = True
            for minor in major_minors:
                minor_id = minor.get('id', '')
                minor_desc = minor.get('description', '')
                minor_completed = minor.get('completed', 0)
                minor_total = minor.get('total', 0)
                tasks = minor.get('tasks', [])

                if not first_minor:
                    inner_content.append('')
                first_minor = False

                is_complete = minor_completed == minor_total and minor_total > 0
                is_active = minor_id == active_minor

                if is_complete:
                    emoji = '☑️'
                elif is_active:
                    emoji = '🔄'
                else:
                    emoji = '🔳'

                if minor_desc:
                    inner_content.append(f"{emoji} {minor_id}: {minor_desc} ({minor_completed}/{minor_total})")
                else:
                    inner_content.append(f"{emoji} {minor_id}: ({minor_completed}/{minor_total})")

                # Only show tasks for active minor
                if is_active:
                    if tasks:
                        inner_content.append('')

                    for task in tasks:
                        task_name = task.get('name', '')
                        task_status = task.get('status', 'pending')
                        blocked_by = task.get('blocked_by', [])

                        if task_status in ("completed", "done"):
                            task_emoji = '☑️'
                        elif task_status in ("in-progress", "active", "in_progress"):
                            task_emoji = '🔄'
                        elif blocked_by:
                            task_emoji = '🚫'
                        else:
                            task_emoji = '🔳'

                        if blocked_by and task_status not in ("completed", "done"):
                            blocked_str = ", ".join(blocked_by)
                            inner_content.append(f"   {task_emoji} {task_name} (blocked by: {blocked_str})")
                        else:
                            inner_content.append(f"   {task_emoji} {task_name}")

                    if tasks:
                        inner_content.append('')

        header = f"📦 {major_id}: {major_name}"
        inner_box_lines = build_inner_box(header, inner_content)
        content_items.extend(inner_box_lines)
        content_items.append('')

    # Actionable footer
    if in_progress_task:
        content_items.append(f"📋 Current: /cat:work {active_minor}-{in_progress_task}")
    elif next_task:
        content_items.append(f"📋 Next: /cat:work {active_minor}-{next_task}")
    else:
        content_items.append("📋 No pending tasks available")

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

    return final_box


def main():
    parser = argparse.ArgumentParser(description='Generate status display')
    parser.add_argument('--project-dir', default='.', help='Project directory')
    args = parser.parse_args()

    output = generate_status_display(args.project_dir)
    print(output)


if __name__ == '__main__':
    main()
