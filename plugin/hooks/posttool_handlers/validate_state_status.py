"""
Validate STATE.md status values after Edit or Write operations (M434).

Monitors PostToolUse events for Edit/Write operations on STATE.md files
and validates that the Status field uses a canonical value.

Canonical statuses: pending, in-progress, completed, blocked
"""

import re
from pathlib import Path
from . import register_handler


# Canonical status values for STATE.md files
CANONICAL_STATUSES = frozenset({"pending", "in-progress", "completed", "blocked"})

# Regex to extract the Status field value from STATE.md
STATUS_PATTERN = re.compile(r"^- \*\*Status:\*\*\s*(.+)$", re.MULTILINE)

WARNING_TEMPLATE = """\u26a0\ufe0f STATE.md STATUS VALIDATION (M434)

Non-canonical status value: "{value}"

Valid values: pending, in-progress, completed, blocked

Common mistakes:
  - "complete" \u2192 use "completed"
  - "done" \u2192 use "completed"
  - "in_progress" \u2192 use "in-progress"
  - "active" \u2192 use "in-progress"

Fix the status value before committing."""


def handle(tool_name: str, tool_input: dict, tool_result: dict, session_state: dict = None) -> str | None:
    """
    Validate STATE.md status field after Edit or Write operations.

    Args:
        tool_name: The tool that was executed (Edit, Write, etc.)
        tool_input: The tool's input parameters (contains file_path)
        tool_result: The tool's result dict
        session_state: Optional session state dict

    Returns:
        Warning string if non-canonical status found, None otherwise.
    """
    # Only trigger on Edit or Write
    if tool_name not in ("Edit", "Write"):
        return None

    # Extract file_path from tool_input
    file_path = tool_input.get("file_path", "")
    if not file_path or not file_path.endswith("/STATE.md"):
        return None

    # Read the file and extract the Status field
    try:
        content = Path(file_path).read_text()
    except (OSError, IOError):
        return None

    match = STATUS_PATTERN.search(content)
    if not match:
        return None

    value = match.group(1).strip()

    if value in CANONICAL_STATUSES:
        return None

    return WARNING_TEMPLATE.format(value=value)


class ValidateStateStatusHandler:
    """PostToolUse handler wrapper for the posttool_handlers dispatcher."""

    def check(self, tool_name: str, tool_result: dict, context: dict) -> dict | None:
        """
        Check tool result for non-canonical STATE.md status values.

        Delegates to the module-level handle() function, extracting
        tool_input from the context's hook_data.
        """
        hook_data = context.get("hook_data", {})
        tool_input = hook_data.get("tool_input", {})

        warning = handle(tool_name, tool_input, tool_result)
        if warning:
            return {"additionalContext": warning}

        return None


# Register handler
register_handler(ValidateStateStatusHandler())
