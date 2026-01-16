"""
Handler for /cat:config precomputation.

Runs the precompute-config-display.sh script and returns the result.
"""

from pathlib import Path

from . import register_handler
from .base import run_script


class ConfigHandler:
    """Handler for /cat:config skill."""

    def handle(self, context: dict) -> str | None:
        """Run config display script and return result."""
        project_root = context.get("project_root")
        plugin_root = context.get("plugin_root")

        if not project_root:
            return None

        config_file = Path(project_root) / ".claude" / "cat" / "cat-config.json"
        if not config_file.exists():
            return None

        display_script = Path(plugin_root) / "scripts" / "precompute-config-display.sh"
        output = run_script(display_script, str(config_file))

        if not output:
            return None

        return f"""PRE-COMPUTED CONFIG DISPLAY (copy exactly):

{output}

INSTRUCTION: Output the above box EXACTLY as shown. Do NOT recompute or modify alignment."""


# Register handler
_handler = ConfigHandler()
register_handler("config", _handler)
