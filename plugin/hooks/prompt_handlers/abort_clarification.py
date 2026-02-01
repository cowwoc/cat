"""
Abort terminology clarification handler.

Detects when users use ambiguous "abort" terminology with tasks
and reminds agent to clarify intent before taking action (M266).
"""

import re
import sys
from pathlib import Path

# Add skill_handlers to path for build_header_box import
_hooks_dir = Path(__file__).parent.parent
if str(_hooks_dir) not in sys.path:
    sys.path.insert(0, str(_hooks_dir))

from skill_handlers.status_handler import build_header_box
from . import register_handler

# Regex patterns to match abort-related phrases with optional task names
# Matches: "abort the task", "abort the 2.1-foo task", "abort task foo", etc.
ABORT_PATTERNS = [
    r"abort\s+(?:the\s+)?(?:[\w\.\-]+\s+)?task",
    r"cancel\s+(?:the\s+)?(?:[\w\.\-]+\s+)?task",
    r"stop\s+(?:the\s+)?(?:[\w\.\-]+\s+)?task",
    r"abort\s+this\s+task",
    r"cancel\s+this\s+task",
    r"stop\s+this\s+task",
]


class AbortClarificationHandler:
    """Detect abort-related terminology and remind to clarify."""

    def check(self, prompt: str, session_id: str) -> str | None:
        """Check for abort patterns using regex."""
        prompt_lower = prompt.lower()

        matched_pattern = None
        for pattern in ABORT_PATTERNS:
            if re.search(pattern, prompt_lower):
                match = re.search(pattern, prompt_lower)
                matched_pattern = match.group(0) if match else pattern
                break

        if not matched_pattern:
            return None

        # Build warning box
        content = [
            "",
            f'User said: "{matched_pattern}"',
            "",
            "This is AMBIGUOUS - clarify with AskUserQuestion BEFORE acting:",
            "",
            "Possible meanings:",
            '• "Stop current work session" → Keep task pending, just stop working now',
            '• "Cleanup worktree/branch" → Remove worktree but keep task in planning',
            '• "Abandon permanently" → Mark task as abandoned (rarely intended)',
            "",
            "Use AskUserQuestion with options:",
            '1. "Stop working (keep task pending)" - Most common intent',
            '2. "Cleanup worktree only (keep in planning)"',
            '3. "Abandon task permanently"',
            "",
        ]

        return build_header_box("⚠️ AMBIGUOUS TERMINOLOGY DETECTED (M266)", content, prefix="─ ")


# Register handler
register_handler(AbortClarificationHandler())
