"""
Warn about extracting large files.
"""

import re
from . import register_handler


class WarnFileExtractionHandler:
    """Warn when extracting large archives."""

    def check(self, command: str, context: dict) -> dict | None:
        # Check for tar/unzip extraction
        if re.search(r'(tar\s+.*-?x|unzip|gunzip)', command):
            # Just a mild warning, don't block
            return {
                "warning": """📦 File extraction detected. Ensure destination directory is appropriate."""
            }

        return None


register_handler(WarnFileExtractionHandler())
