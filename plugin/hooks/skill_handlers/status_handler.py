"""
Handler for /cat:status precomputation.

Collects project status data and renders the display box with ultra-compact layout.
"""

import re
from pathlib import Path

from . import register_handler

# Get plugin root for accessing cat data
SCRIPT_DIR = Path(__file__).parent.parent
PLUGIN_ROOT = SCRIPT_DIR.parent


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


def get_task_dependencies(state_file: Path) -> list:
    """Get task dependencies from STATE.md file."""
    if not state_file.exists():
        return []

    try:
        content = state_file.read_text()
    except Exception:
        return []

    # Try "## Dependencies" section with list items
    # Format: - task-name (reason)
    deps = []
    match = re.search(r'^## Dependencies\s*\n((?:- .+\n?)+)', content, re.MULTILINE)
    if match:
        for line in match.group(1).strip().split('\n'):
            dep_match = re.match(r'^- ([a-zA-Z0-9_-]+)', line)
            if dep_match:
                dep_name = dep_match.group(1)
                # Filter out "None" as it's not a real dependency
                if dep_name.lower() != 'none':
                    deps.append(dep_name)
        return deps

    # Try "- **Dependencies:** [task1, task2]" format
    match = re.search(r'^\- \*\*Dependencies:\*\*\s*\[([^\]]*)\]', content, re.MULTILINE)
    if match:
        dep_str = match.group(1).strip()
        if dep_str:
            deps = [d.strip() for d in dep_str.split(',') if d.strip()]
        return deps

    return []


def collect_status_data(issues_dir: Path, cat_dir: Path = None) -> dict:
    """Collect project status data from CAT directory structure.

    Args:
        issues_dir: Path to .claude/cat/issues/ where version directories live
        cat_dir: Optional path to .claude/cat/ where PROJECT.md and ROADMAP.md live.
                 If not provided, uses issues_dir.parent
    """
    if not issues_dir.is_dir():
        return {"error": "No planning structure found. Run /cat:init to initialize."}

    # Determine where config files are (parent of issues_dir)
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

    # Iterate over major version directories
    for major_dir in sorted(issues_dir.glob("v[0-9]*")):
        if not major_dir.is_dir():
            continue

        major_id = major_dir.name
        major_num = major_id[1:]  # Remove 'v' prefix

        # Get major name from roadmap
        major_name = f"Version {major_num}"
        match = re.search(rf'^## Version {re.escape(major_num)}: (.+)$', roadmap_content, re.MULTILINE)
        if match:
            major_name = match.group(1).split('(')[0].strip()

        # Check if major is completed from STATE.md
        major_state_file = major_dir / "STATE.md"
        major_status = "pending"
        if major_state_file.exists():
            major_status = get_task_status(major_state_file)

        majors.append({
            "id": major_id,
            "name": major_name,
            "status": major_status
        })

        # Iterate over minor version directories
        for minor_dir in sorted(major_dir.glob("v[0-9]*.[0-9]*"), key=lambda p: [int(x) for x in p.name[1:].split('.')]):
            if not minor_dir.is_dir():
                continue

            minor_id = minor_dir.name
            minor_num = minor_id[1:]  # Remove 'v' prefix

            local_completed = 0
            local_total = 0
            local_inprog = ""
            tasks = []
            all_task_statuses = {}

            # First pass: collect all task statuses
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

            # Second pass: check dependencies and build task list
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

                # Check if blocked by incomplete dependencies
                blocked_by = []
                for dep in dependencies:
                    dep_status = all_task_statuses.get(dep, "pending")
                    if dep_status not in ("completed", "done"):
                        blocked_by.append(dep)

                tasks.append({
                    "name": task_name,
                    "status": status,
                    "dependencies": dependencies,
                    "blocked_by": blocked_by
                })

                if status in ("completed", "done"):
                    local_completed += 1
                elif status in ("in-progress", "active", "in_progress"):
                    local_inprog = task_name

            # Get minor description from roadmap
            desc = ""
            match = re.search(rf'^\- \*\*{re.escape(minor_num)}:\*\*\s*([^(]+)', roadmap_content, re.MULTILINE)
            if match:
                desc = match.group(1).strip()

            total_completed += local_completed
            total_tasks += local_total

            # Determine current minor and next task
            if not current_minor:
                if local_inprog:
                    current_minor = minor_id
                    in_progress_task = local_inprog
                elif local_completed < local_total:
                    current_minor = minor_id
                    # Find first non-blocked pending task
                    for task in tasks:
                        if task["status"] == "pending" and not task["blocked_by"]:
                            next_task = task["name"]
                            break
                    # If all pending tasks are blocked, just pick the first pending
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
            "nextTask": next_task
        },
        "majors": majors,
        "minors": minors
    }


def collapse_completed_minors(minors: list) -> str:
    """Collapse completed minors into a range string like 'v1.0 - v1.10 (81/81)'."""
    if not minors:
        return ""

    # Sort by version number
    sorted_minors = sorted(minors, key=lambda m: [int(x) for x in m["id"][1:].split('.')])

    first = sorted_minors[0]["id"]
    last = sorted_minors[-1]["id"]
    total_completed = sum(m["completed"] for m in sorted_minors)
    total_tasks = sum(m["total"] for m in sorted_minors)

    if first == last:
        return f"☑️ {first} ({total_completed}/{total_tasks})"
    else:
        return f"☑️ {first} - {last} ({total_completed}/{total_tasks})"


class StatusHandler:
    """Handler for /cat:status skill."""

    def handle(self, context: dict) -> str | None:
        """Run status computation and return result."""
        project_root = context.get("project_root")

        if not project_root:
            return None

        cat_dir = Path(project_root) / ".claude" / "cat"
        if not cat_dir.is_dir():
            return None

        # Collect status data from issues subdirectory
        issues_dir = cat_dir / "issues"
        status_data = collect_status_data(issues_dir)

        if 'error' in status_data:
            return None

        # Extract data
        percent = status_data.get('overall', {}).get('percent', 0)
        completed = status_data.get('overall', {}).get('completed', 0)
        total = status_data.get('overall', {}).get('total', 0)
        active_minor = status_data.get('current', {}).get('minor', '')
        in_progress_task = status_data.get('current', {}).get('inProgressTask', '')
        next_task = status_data.get('current', {}).get('nextTask', '')
        majors = status_data.get('majors', [])
        minors = status_data.get('minors', [])

        # Build content
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

            # Check if all minors are complete
            all_complete = all(
                m.get('completed', 0) == m.get('total', 0) and m.get('total', 0) > 0
                for m in major_minors
            ) if major_minors else False

            # Also check major status
            if major_status in ("completed", "done"):
                all_complete = True

            inner_content = []

            if all_complete and major_minors:
                # Collapse completed major to single line
                inner_content.append(collapse_completed_minors(major_minors))
            else:
                # Show individual minors
                first_minor = True
                for minor in major_minors:
                    minor_id = minor.get('id', '')
                    minor_desc = minor.get('description', '')
                    minor_completed = minor.get('completed', 0)
                    minor_total = minor.get('total', 0)
                    minor_in_progress = minor.get('inProgress', '')
                    tasks = minor.get('tasks', [])

                    # Add empty line between minors (not before first)
                    if not first_minor:
                        inner_content.append('')
                    first_minor = False

                    # Determine if this minor is complete
                    is_complete = minor_completed == minor_total and minor_total > 0
                    is_active = minor_id == active_minor

                    # Only active version gets 🔄, parent minor (if exists) has no emoji
                    if is_complete:
                        emoji = '☑️'
                    elif is_active:
                        emoji = '🔄'
                    else:
                        emoji = '🔳'

                    # Build minor line
                    if minor_desc:
                        inner_content.append(f"{emoji} {minor_id}: {minor_desc} ({minor_completed}/{minor_total})")
                    else:
                        inner_content.append(f"{emoji} {minor_id}: ({minor_completed}/{minor_total})")

                    # Only show tasks for active minor
                    if is_active:
                        # Add empty line before tasks for visual separation
                        if tasks:
                            inner_content.append('')

                        for task in tasks:
                            task_name = task.get('name', '')
                            task_status = task.get('status', 'pending')
                            blocked_by = task.get('blocked_by', [])

                            # Determine task emoji
                            if task_status in ("completed", "done"):
                                task_emoji = '☑️'
                            elif task_status in ("in-progress", "active", "in_progress"):
                                task_emoji = '🔄'
                            elif blocked_by:
                                task_emoji = '🚫'
                            else:
                                task_emoji = '🔳'

                            # Build task line with full name (no truncation)
                            # Only show blocked info for pending tasks that are actually blocked
                            if blocked_by and task_status not in ("completed", "done"):
                                blocked_str = ", ".join(blocked_by)
                                inner_content.append(f"   {task_emoji} {task_name} (blocked by: {blocked_str})")
                            else:
                                inner_content.append(f"   {task_emoji} {task_name}")

                        # Add empty line after tasks for visual separation
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

        return f"""PRE-COMPUTED STATUS DISPLAY (copy exactly):

{final_box}

NEXT STEPS table:
| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | `/clear` then `/cat:work {active_minor}-<task-name>` |
| [**2**] | Add new task | `/cat:add` |

Legend: ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

INSTRUCTION: Output the above box and tables EXACTLY as shown. Do not recalculate."""


# Register handler
_handler = StatusHandler()
register_handler("status", _handler)
