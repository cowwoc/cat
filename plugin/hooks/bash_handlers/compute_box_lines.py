"""
Compute box lines via hook interception.

M192: Agent calculated box widths correctly but re-typed output from memory,
causing alignment errors. This handler executes Python-based computation
and returns results via additionalContext.

USAGE: Agent invokes Bash with marker comment:
  Bash("#BOX_COMPUTE\ncontent1\ncontent2\ncontent3")
"""

import sys
from pathlib import Path

from . import register_handler

# Add scripts directory to path for imports
SCRIPT_DIR = Path(__file__).parent.parent.parent
sys.path.insert(0, str(SCRIPT_DIR / 'scripts'))

try:
    from build_box_lines import build_box
    HAS_BOX_UTILS = True
except ImportError:
    HAS_BOX_UTILS = False


class ComputeBoxLinesHandler:
    """Intercept #BOX_COMPUTE commands and compute box lines."""

    def check(self, command: str, context: dict) -> dict | None:
        # Check for the BOX_COMPUTE marker
        lines = command.split('\n')
        if not lines or not lines[0].strip().startswith('#BOX_COMPUTE'):
            return None

        if not HAS_BOX_UTILS:
            return {
                "decision": "block",
                "reason": "BOX_COMPUTE: build_box_lines module not available"
            }

        # Extract content items (all lines after the marker)
        content_items = lines[1:] if len(lines) > 1 else []

        if not content_items:
            return {
                "decision": "block",
                "reason": "BOX_COMPUTE: No content items provided"
            }

        # Build the box
        try:
            box_output = build_box(content_items)
            return {
                "decision": "block",  # Block the bash command
                "reason": f"BOX_COMPUTE result (use this output exactly):\n\n{box_output}",
                "additionalContext": f"Script output box (copy exactly):\n```\n{box_output}\n```"
            }
        except Exception as e:
            return {
                "decision": "block",
                "reason": f"BOX_COMPUTE: Error computing box: {e}"
            }


register_handler(ComputeBoxLinesHandler())
