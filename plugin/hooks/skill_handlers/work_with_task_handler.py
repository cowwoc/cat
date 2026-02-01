"""
Handler for /cat:work-with-task precomputation.

Generates progress banners for all 4 work phases by parsing task_id from skill args
and delegating to get-progress-banner.sh.
"""

import json
import re
import subprocess
from pathlib import Path

from . import register_handler


class WorkWithTaskHandler:
    """Handler for /cat:work-with-task skill."""

    def handle(self, context: dict) -> str | None:
        """
        Generate progress banners for all 4 work phases.

        Parses task_id from the skill invocation JSON args and calls
        get-progress-banner.sh to render all phase banners.

        Args:
            context: Handler context containing user_prompt with skill invocation

        Returns:
            Pre-rendered banners or None if task_id cannot be parsed
        """
        user_prompt = context.get("user_prompt", "")
        plugin_root = context.get("plugin_root", "")

        if not user_prompt or not plugin_root:
            return None

        # Extract JSON args from prompt: /cat:work-with-task {"task_id": "..."}
        # The skill invocation can be:
        # - /cat:work-with-task {"task_id": "2.1-xxx", ...}
        # - cat:work-with-task {"task_id": "2.1-xxx"}
        match = re.search(r'/?\s*cat:work-with-task\s+(\{[^}]*\})', user_prompt, re.IGNORECASE)
        if not match:
            return None

        try:
            args = json.loads(match.group(1))
            task_id = args.get("task_id")
        except json.JSONDecodeError:
            return None

        if not task_id:
            return None

        # Call get-progress-banner.sh with task_id and --all-phases
        banner_script = Path(plugin_root) / "scripts" / "get-progress-banner.sh"
        if not banner_script.exists():
            return None

        try:
            result = subprocess.run(
                [str(banner_script), task_id, "--all-phases"],
                capture_output=True,
                text=True,
                timeout=30
            )
            if result.returncode != 0:
                return None

            # Return banners in the SCRIPT OUTPUT format
            return f"SCRIPT OUTPUT PROGRESS BANNERS:\n\n{result.stdout}"
        except Exception:
            return None


# Register handler
_handler = WorkWithTaskHandler()
register_handler("work-with-task", _handler)
