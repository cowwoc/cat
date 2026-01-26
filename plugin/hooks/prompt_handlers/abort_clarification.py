"""
Abort terminology clarification handler.

Detects when users use ambiguous "abort" terminology with tasks
and reminds agent to clarify intent before taking action (M266).
"""

from . import register_handler

ABORT_PATTERNS = [
    "abort the task",
    "abort task",
    "abort this task",
    "cancel the task",
    "cancel task",
    "stop the task",
    "stop task",
]


class AbortClarificationHandler:
    """Detect abort-related terminology and remind to clarify."""

    def check(self, prompt: str, session_id: str) -> str | None:
        """Check for abort patterns."""
        prompt_lower = prompt.lower()

        matched_pattern = None
        for pattern in ABORT_PATTERNS:
            if pattern in prompt_lower:
                matched_pattern = pattern
                break

        if not matched_pattern:
            return None

        return """╭─ ⚠️ AMBIGUOUS TERMINOLOGY DETECTED (M266)
│
│  User said: "{pattern}"
│
│  This is AMBIGUOUS - clarify with AskUserQuestion BEFORE acting:
│
│  Possible meanings:
│  • "Stop current work session" → Keep task pending, just stop working now
│  • "Cleanup worktree/branch" → Remove worktree but keep task in planning
│  • "Abandon permanently" → Mark task as abandoned (rarely intended)
│
│  Use AskUserQuestion with options:
│  1. "Stop working (keep task pending)" - Most common intent
│  2. "Cleanup worktree only (keep in planning)"
│  3. "Abandon task permanently"
│
╰─""".format(pattern=matched_pattern)


# Register handler
register_handler(AbortClarificationHandler())
