"""
Handler for /cat:init precomputation.

Runs build-init-boxes.py to generate all 8 init boxes as templates.
"""

import json
import subprocess
import sys
from pathlib import Path

from . import register_handler


class InitHandler:
    """Handler for /cat:init skill."""

    def handle(self, context: dict) -> str | None:
        """Run init boxes builder and return templates."""
        plugin_root = context.get("plugin_root")

        build_script = Path(plugin_root) / "scripts" / "build-init-boxes.py"
        if not build_script.exists():
            return None

        # Run the build script
        try:
            result = subprocess.run(
                [sys.executable, str(build_script), "--format", "json"],
                capture_output=True,
                text=True,
                timeout=30
            )
            if result.returncode != 0:
                return None

            boxes_json = json.loads(result.stdout)
        except (subprocess.TimeoutExpired, json.JSONDecodeError, Exception):
            return None

        if 'error' in boxes_json:
            return None

        # Extract each box
        default_gates = boxes_json.get('default_gates_configured', '')
        research_skipped = boxes_json.get('research_skipped', '')
        choose_partner = boxes_json.get('choose_your_partner', '')
        cat_initialized = boxes_json.get('cat_initialized', '')
        first_task_walkthrough = boxes_json.get('first_task_walkthrough', '')
        first_task_created = boxes_json.get('first_task_created', '')
        all_set = boxes_json.get('all_set', '')
        explore = boxes_json.get('explore_at_your_own_pace', '')

        return f"""OUTPUT TEMPLATE INIT BOXES

Use these box templates EXACTLY as shown. Replace {{variables}} with actual values at runtime.

=== BOX: default_gates_configured ===
Variables: {{N}} = version count
{default_gates}

=== BOX: research_skipped ===
Variables: {{version}} = example version number (shown in help text)
{research_skipped}

=== BOX: choose_your_partner ===
Variables: none (static)
{choose_partner}

=== BOX: cat_initialized ===
Variables: {{trust}}, {{curiosity}}, {{patience}} = user preference values
{cat_initialized}

=== BOX: first_task_walkthrough ===
Variables: none (static)
{first_task_walkthrough}

=== BOX: first_task_created ===
Variables: {{task-name}} = sanitized task name from user input
{first_task_created}

=== BOX: all_set ===
Variables: none (static)
{all_set}

=== BOX: explore_at_your_own_pace ===
Variables: none (static)
{explore}

INSTRUCTION: When displaying a box, copy the template EXACTLY and only replace the {{variable}} placeholders.
Do NOT recalculate padding or alignment - the boxes are output template with correct widths."""


# Register handler
_handler = InitHandler()
register_handler("init", _handler)
