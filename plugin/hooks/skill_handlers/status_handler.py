"""
Shared display utilities for CAT handlers.

Provides box-building functions used by multiple skill handlers.
Status display generation moved to plugin/scripts/get-status-display.py for silent preprocessing.
"""

import re
import sys
from pathlib import Path

# Get plugin root for accessing cat data
SCRIPT_DIR = Path(__file__).parent.parent
PLUGIN_ROOT = SCRIPT_DIR.parent

# Add scripts/lib to path for emoji_widths import
_LIB_PATH = PLUGIN_ROOT / "scripts" / "lib"
if str(_LIB_PATH) not in sys.path:
    sys.path.insert(0, str(_LIB_PATH))

# Import shared emoji width library
from emoji_widths import EmojiWidths as _EmojiWidths

# Singleton instance for emoji width calculations
_emoji_widths = _EmojiWidths(plugin_root=PLUGIN_ROOT)


def display_width(text: str) -> int:
    """Calculate terminal display width of a string using shared emoji_widths library."""
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


def build_header_box(header: str, content_lines: list[str], separator_indices: list[int] = None,
                     min_width: int = None, prefix: str = None) -> str:
    """Build a box with header and optional separators.

    Args:
        header: Header text to display
        content_lines: Lines of content inside the box
        separator_indices: Indices where separator lines should be inserted
        min_width: Minimum box width (optional)
        prefix: Header prefix (default "─── ", use "─ " for simpler style)
    """
    if separator_indices is None:
        separator_indices = []
    if prefix is None:
        prefix = "─── "

    # Calculate max width
    content_widths = [display_width(c) for c in content_lines]
    # Account for prefix in header width calculation
    header_width = display_width(header) + len(prefix) + 1  # +1 for space before suffix dashes
    max_width = max(max(content_widths) if content_widths else 0, header_width)
    if min_width is not None:
        max_width = max(max_width, min_width)

    # Build header
    suffix_dashes = "─" * (max_width - len(prefix) - display_width(header) + 1)
    if len(suffix_dashes) < 1:
        suffix_dashes = "─"
    top = "╭" + prefix + header + " " + suffix_dashes + "╮"

    lines = [top]
    for i, content in enumerate(content_lines):
        if i in separator_indices:
            lines.append("├" + "─" * (max_width + 2) + "┤")
        lines.append(build_line(content, max_width))

    lines.append(build_border(max_width, is_top=False))
    return "\n".join(lines)


def build_progress_bar(percent: int, width: int = 25) -> str:
    """Build a progress bar string."""
    filled = percent * width // 100
    empty = width - filled
    return '█' * filled + '░' * empty


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


# Valid status values (canonical)
# M253: Scripts must fail-fast on unknown status values
VALID_STATUSES = {"open", "in-progress", "closed", "blocked"}

# Status aliases that get normalized to canonical values
STATUS_ALIASES = {
    "pending": "open",            # Renamed: pending → open
    "completed": "closed",        # Renamed: completed → closed
    "complete": "closed",         # Common typo
    "done": "closed",             # Alternative
    "in_progress": "in-progress", # Underscore variant
    "active": "in-progress",      # Alternative
}


def get_task_status(state_file: Path) -> str:
    """Get issue status from STATE.md file.

    Returns canonical status value. Raises ValueError for unknown statuses (M253).
    """
    if not state_file.exists():
        return "open"

    try:
        content = state_file.read_text()
    except Exception:
        return "open"

    # Format: "- **Status:** <value>"
    match = re.search(r'^\- \*\*Status:\*\*\s*(.+)$', content, re.MULTILINE)
    if not match:
        return "open"

    raw_status = match.group(1).strip().lower()

    # Check if it's a valid canonical status
    if raw_status in VALID_STATUSES:
        return raw_status

    # Check if it's a known alias and normalize
    if raw_status in STATUS_ALIASES:
        canonical = STATUS_ALIASES[raw_status]
        # Log warning about non-canonical value (but don't fail)
        print(f"WARNING: Non-canonical status '{raw_status}' in {state_file}, use '{canonical}'",
              file=sys.stderr)
        return canonical

    # Unknown status - fail fast (M253)
    valid_list = ", ".join(sorted(VALID_STATUSES))
    raise ValueError(
        f"Unknown status '{raw_status}' in {state_file}. "
        f"Valid values: {valid_list}. "
        f"Common typo: 'complete' should be 'closed'"
    )


def get_task_dependencies(state_file: Path) -> list:
    """Get issue dependencies from STATE.md file."""
    if not state_file.exists():
        return []

    try:
        content = state_file.read_text()
    except Exception:
        return []

    # Try "## Dependencies" section with list items
    # Format: - issue-name (reason)
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


import subprocess

from . import register_handler


class StatusHandler:
    """Handler for /cat:status skill preprocessing.

    Runs get-status-display.py to generate the formatted status display
    and wraps output as SCRIPT OUTPUT STATUS DISPLAY.
    """

    def handle(self, context: dict) -> str | None:
        """Run status display script and return result."""
        project_root = context.get("project_root")
        plugin_root = context.get("plugin_root")

        if not project_root or not plugin_root:
            return None

        script = Path(plugin_root) / "scripts" / "get-status-display.py"
        if not script.exists():
            return None

        try:
            result = subprocess.run(
                [sys.executable, str(script), "--project-dir", str(project_root)],
                capture_output=True,
                text=True,
                timeout=30,
            )
            if result.returncode != 0 or not result.stdout.strip():
                return None

            output = result.stdout.strip()
            return f"SCRIPT OUTPUT STATUS DISPLAY:\n\n{output}"

        except (subprocess.TimeoutExpired, subprocess.SubprocessError):
            return None


_handler = StatusHandler()
register_handler("status", _handler)
