"""
Handler for /cat:token-report precomputation.

Runs compute-token-table.py and returns the formatted result.
"""

import json
import os
from pathlib import Path

from . import register_handler
from .base import run_python_script


class TokenReportHandler:
    """Handler for /cat:token-report skill."""

    def handle(self, context: dict) -> str | None:
        """Run token table computation and return result."""
        session_id = context.get("session_id")
        plugin_root = context.get("plugin_root")

        if not session_id:
            return None

        # Build session file path
        session_file = Path.home() / ".config" / "claude" / "projects" / "-workspace" / f"{session_id}.jsonl"

        if not session_file.exists():
            return None

        compute_script = Path(plugin_root) / "scripts" / "compute-token-table.py"
        if not compute_script.exists():
            return None

        # Run with SESSION_FILE env var
        output = run_python_script(
            compute_script,
            env={"SESSION_FILE": str(session_file)}
        )

        if not output:
            return None

        # Parse JSON output
        try:
            data = json.loads(output)
            if "error" in data:
                return None

            lines = "\n".join(data.get("lines", []))
            summary = data.get("summary", {})
            total_tokens = summary.get("total_tokens", 0)
            subagent_count = summary.get("subagent_count", 0)

            if not lines:
                return None

            return f"""SCRIPT OUTPUT TOKEN REPORT:

{lines}

Summary: {subagent_count} subagents, {total_tokens} total tokens

INSTRUCTION: Output the above table EXACTLY as shown. Do NOT recompute or modify alignment.

Legend:
- Percentages show context utilization per subagent
- Warning emoji inside Context column indicates high (>=40%) usage
- Critical emoji inside Context column indicates exceeded (>=80%) limit"""

        except json.JSONDecodeError:
            return None


# Register handler
_handler = TokenReportHandler()
register_handler("token-report", _handler)
