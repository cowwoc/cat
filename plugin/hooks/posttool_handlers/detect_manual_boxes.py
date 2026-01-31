"""
Detect manual box/banner construction (A008/PATTERN-008).

Monitors conversation for box-drawing characters that appear without
preceding script execution, indicating Claude manually constructed
boxes instead of using pre-rendered templates.

Related mistakes: M311, M315, M319, M321, M322, M323
"""

import json
import re
from pathlib import Path
from . import register_handler


class DetectManualBoxesHandler:
    """Detect manual box construction and warn."""

    # Box-drawing characters used in progress banners and work boxes
    BOX_CHARS = frozenset("─│┌┐└┘├┤┬┴┼╭╮╰╯║═╔╗╚╝╠╣╦╩╬")

    # Scripts that legitimately produce box output
    BOX_SCRIPTS = [
        "get-progress-banner.sh",
        "get-work-boxes.py",
        "get-work-boxes.sh",
        "get-status-display.sh",
        "get-cleanup-boxes.sh",
        "get-config-boxes.sh",
        "get-help-display.sh",
        "render-diff.py",
        "render-add-complete.sh",
    ]

    # Patterns that indicate legitimate box usage (not manual construction)
    LEGITIMATE_PATTERNS = [
        r"```",  # Code blocks may contain box chars
        r"^\s*#",  # Comments
        r"cat <<",  # Heredocs
        r"echo.*[─│┌┐└┘]",  # Echo commands with box chars
    ]

    def __init__(self):
        self._checked_sessions = {}  # session_id -> last_checked_line

    def check(self, tool_name: str, tool_result: dict, context: dict) -> dict | None:
        """
        Check if recent assistant messages contain manually constructed boxes.

        We check after each tool call to see if the preceding assistant message
        contained box characters without script execution.
        """
        session_id = context.get("session_id", "")
        if not session_id:
            return None

        # Only check periodically to avoid excessive log reading
        # Check after Bash calls (which might be script executions) and after
        # text-heavy tools like Read
        if tool_name not in ("Bash", "Read", "Task", "Skill"):
            return None

        # Get recent messages from conversation log
        detection = self._analyze_recent_messages(session_id)

        if detection:
            return {
                "additionalContext": f"""⚠️ MANUAL BOX CONSTRUCTION DETECTED (A008/PATTERN-008)

Box-drawing characters were found in your output without preceding script execution.

**Problem**: LLMs cannot accurately count character display widths, leading to misaligned boxes.

**Required action**:
1. Use pre-rendered scripts for boxes/banners:
   - Progress banners: `get-progress-banner.sh`
   - Work boxes: `get-work-boxes.py` or `get-work-boxes.sh`
   - Status display: `get-status-display.sh`
   - Diff tables: `render-diff.py`

2. Copy script output VERBATIM - do not modify alignment

3. If preprocessing failed, report the error instead of manually constructing boxes

**Detection context**: {detection}"""
            }

        return None

    def _analyze_recent_messages(self, session_id: str) -> str | None:
        """
        Analyze recent messages for manual box construction.

        Returns detection context string if manual boxes found, None otherwise.
        """
        conv_log = (
            Path.home()
            / ".config"
            / "claude"
            / "projects"
            / "-workspace"
            / f"{session_id}.jsonl"
        )
        if not conv_log.exists():
            return None

        try:
            with open(conv_log, "r") as f:
                lines = f.readlines()
        except Exception:
            return None

        # Only check last 50 lines for efficiency
        recent_lines = lines[-50:]

        # Track state
        script_executed = False
        box_in_assistant_message = False
        in_code_block = False

        for line in recent_lines:
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue

            msg_type = entry.get("type", "")
            message = entry.get("message", {})
            content = ""

            if isinstance(message, dict):
                content = message.get("content", "")
                if isinstance(content, list):
                    # Handle content array format
                    content = " ".join(
                        str(c.get("text", "")) if isinstance(c, dict) else str(c)
                        for c in content
                    )

            # Check for script execution in tool calls
            if msg_type == "tool_use" or "tool_use" in str(entry):
                tool_input = entry.get("tool_input", {})
                if isinstance(tool_input, dict):
                    command = tool_input.get("command", "")
                else:
                    command = str(tool_input)

                for script in self.BOX_SCRIPTS:
                    if script in command:
                        script_executed = True
                        break

            # Check assistant messages for box characters
            if msg_type == "assistant" and content:
                # Track code blocks
                code_block_count = content.count("```")
                if code_block_count % 2 == 1:
                    in_code_block = not in_code_block

                # Skip if in code block
                if in_code_block:
                    continue

                # Check for box characters outside code blocks
                box_char_count = sum(1 for c in content if c in self.BOX_CHARS)

                # Need substantial box usage to trigger (avoid false positives)
                if box_char_count >= 10:
                    # Check for legitimate patterns
                    is_legitimate = False
                    for pattern in self.LEGITIMATE_PATTERNS:
                        if re.search(pattern, content, re.MULTILINE):
                            is_legitimate = True
                            break

                    if not is_legitimate:
                        box_in_assistant_message = True

        # Detect manual construction: boxes without script execution
        if box_in_assistant_message and not script_executed:
            return "Box characters in assistant output without script execution"

        return None


# Register handler
register_handler(DetectManualBoxesHandler())
