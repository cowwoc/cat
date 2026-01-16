"""
Destructive operations detection handler.

Checks for keywords indicating destructive operations and
injects verification reminder.
"""

import re
from . import register_handler

DESTRUCTIVE_KEYWORDS = [
    "git rebase",
    "git reset",
    "git checkout",
    "squash",
    "consolidate",
    "merge",
    "remove duplicate",
    "cleanup",
    "reorganize",
    "refactor",
    "delete",
    r"\brm\b",  # Word boundary for 'rm'
]


class DestructiveOpsHandler:
    """Detect destructive operations and inject verification reminder."""

    def __init__(self):
        # Pre-compile patterns for efficiency
        self.patterns = [
            (kw, re.compile(re.escape(kw) if not kw.startswith(r"\b") else kw, re.IGNORECASE))
            for kw in DESTRUCTIVE_KEYWORDS
        ]

    def check(self, prompt: str, session_id: str) -> str | None:
        """Check for destructive keywords."""
        prompt_lower = prompt.lower()

        for keyword, pattern in self.patterns:
            if pattern.search(prompt):
                display_kw = keyword.replace(r"\b", "").replace("\\", "")
                return f"""🚨 DESTRUCTIVE OPERATION DETECTED: '{display_kw}'

⚠️  MANDATORY VERIFICATION REQUIRED:
After completing this operation, you MUST:
1. Double-check that no important details were unintentionally removed
2. Verify that all essential information has been preserved
3. Compare before/after to ensure completeness
4. If consolidating/reorganizing, confirm all original content is retained

🔍 This verification step is REQUIRED before considering the task complete."""

        return None


# Register handler
register_handler(DestructiveOpsHandler())
