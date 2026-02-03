"""
Warn when committing compression changes to ensure validation was done.

M382: Compression commits require explicit validation score confirmation.
"""

import re

from . import register_handler


class WarnCompressionCommit:
    """Handler that warns on commits mentioning compression."""

    # Patterns that indicate compression-related commits
    COMPRESSION_PATTERNS = [
        r"\bcompress\w*\b",
        r"\bshrink\w*\b",
        r"\breduction\b",
        r"\breduced\b",
        r"\btoken\s+reduction\b",
    ]

    def check(self, command: str, context: dict) -> dict | None:
        """Check git commit commands for compression-related messages."""
        # Only check git commit commands
        if not re.match(r"^\s*git\s+commit\s", command):
            return None

        # Check if commit message mentions compression
        command_lower = command.lower()
        for pattern in self.COMPRESSION_PATTERNS:
            if re.search(pattern, command_lower):
                return {
                    "warning": (
                        "⚠️ COMPRESSION COMMIT DETECTED (M382)\n"
                        "\n"
                        "Before committing compressed files:\n"
                        "1. Did you run /compare-docs validation?\n"
                        "2. Was the score exactly 1.0? (required for shrink-doc)\n"
                        "\n"
                        "M335 COMMIT GATE: Score < 1.0 → MUST NOT commit\n"
                        "\n"
                        "If validation was skipped or score < 1.0, abort and iterate."
                    )
                }

        return None


# Register handler
register_handler(WarnCompressionCommit())
