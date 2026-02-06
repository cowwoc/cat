"""
Handler for /cat:monitor-subagents precomputation.

Collects subagent status from worktrees and returns formatted JSON output,
replacing the shell script invocation to hide Bash tool calls from users.
"""

import json
import os
import re
import subprocess
from pathlib import Path

from . import register_handler

# Constants matching monitor-subagents.sh
THRESHOLD_TOKENS = 80000  # 40% of 200K context
SESSION_BASE = Path.home() / ".config" / "claude" / "projects" / "-workspace"


class MonitorSubagentsHandler:
    """Handler for /cat:monitor-subagents skill."""

    def handle(self, context: dict) -> str | None:
        """
        Collect subagent status and return formatted JSON output.

        Args:
            context: Dict containing project_root, session_id, etc.

        Returns:
            Formatted string with subagent status JSON, or None on error.
        """
        project_root = context.get("project_root", "")
        if not project_root:
            project_root = os.getcwd()

        worktree_dir = Path(project_root) / ".claude" / "cat" / "worktrees"

        # Collect subagent data
        subagents = []
        summary = {
            "total": 0,
            "running": 0,
            "complete": 0,
            "warning": 0
        }

        # Get worktree list from git
        worktrees = self._get_worktrees(project_root)

        for worktree_path in worktrees:
            # Skip if not a subagent worktree
            if "-sub-" not in worktree_path:
                continue

            summary["total"] += 1

            # Extract subagent ID (8-char hex after -sub-)
            match = re.search(r'sub-([a-f0-9]+)', worktree_path)
            subagent_id = match.group(1) if match else "unknown"

            # Extract task name (part before -sub-)
            issue_name = Path(worktree_path).name
            issue_name = re.sub(r'-sub-.*$', '', issue_name)

            # Check for completion marker
            completion_file = Path(worktree_path) / ".completion.json"
            if completion_file.exists():
                status, tokens, compactions = self._read_completion(completion_file)
                summary["complete"] += 1
            else:
                # Check session file for running status
                status, tokens, compactions = self._check_running_status(worktree_path)
                if tokens >= THRESHOLD_TOKENS:
                    status = "warning"
                    summary["warning"] += 1
                else:
                    summary["running"] += 1

            subagents.append({
                "id": subagent_id,
                "issue": issue_name,
                "status": status,
                "tokens": tokens,
                "compactions": compactions,
                "worktree": worktree_path
            })

        # Build result JSON
        result = {
            "subagents": subagents,
            "summary": summary
        }

        result_json = json.dumps(result, indent=2)

        # Format output for skill consumption
        return f"""SCRIPT OUTPUT MONITOR SUBAGENTS:

{result_json}

INSTRUCTION: Output the above JSON EXACTLY as shown. Do NOT recompute or invoke monitor-subagents.sh.

If no subagents are active (total = 0), report "No active subagents found."
Otherwise, display the subagent table and summary."""

    def _get_worktrees(self, project_root: str) -> list[str]:
        """Get list of worktree paths from git."""
        try:
            result = subprocess.run(
                ["git", "worktree", "list", "--porcelain"],
                cwd=project_root,
                capture_output=True,
                text=True,
                timeout=10
            )
            if result.returncode != 0:
                return []

            worktrees = []
            for line in result.stdout.splitlines():
                if line.startswith("worktree "):
                    worktree_path = line[9:]  # Remove "worktree " prefix
                    worktrees.append(worktree_path)
            return worktrees
        except (subprocess.TimeoutExpired, subprocess.SubprocessError):
            return []

    def _read_completion(self, completion_file: Path) -> tuple[str, int, int]:
        """Read completion data from .completion.json."""
        try:
            with open(completion_file) as f:
                data = json.load(f)
            tokens = data.get("tokensUsed", 0)
            compactions = data.get("compactionEvents", 0)
            return "complete", tokens, compactions
        except (json.JSONDecodeError, OSError):
            return "complete", 0, 0

    def _check_running_status(self, worktree_path: str) -> tuple[str, int, int]:
        """Check session file for running subagent status."""
        session_id_file = Path(worktree_path) / ".session_id"

        if not session_id_file.exists():
            return "running", 0, 0

        try:
            session_id = session_id_file.read_text().strip()
            session_file = SESSION_BASE / f"{session_id}.jsonl"

            if not session_file.exists():
                return "running", 0, 0

            tokens, compactions = self._parse_session_file(session_file)
            return "running", tokens, compactions
        except OSError:
            return "running", 0, 0

    def _parse_session_file(self, session_file: Path) -> tuple[int, int]:
        """Parse session JSONL file for token usage and compactions."""
        total_tokens = 0
        compactions = 0

        try:
            with open(session_file) as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        entry = json.loads(line)
                        entry_type = entry.get("type")

                        if entry_type == "assistant":
                            usage = entry.get("message", {}).get("usage", {})
                            total_tokens += usage.get("input_tokens", 0)
                            total_tokens += usage.get("output_tokens", 0)
                        elif entry_type == "summary":
                            compactions += 1
                    except json.JSONDecodeError:
                        continue
        except OSError:
            pass

        return total_tokens, compactions


# Register handler
_handler = MonitorSubagentsHandler()
register_handler("monitor-subagents", _handler)
