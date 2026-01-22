"""
Validate rebase target - warn when using origin/X instead of local X.

Trigger: PostToolUse for Bash (git rebase commands)
"""

import re
import subprocess
from . import register_handler


class ValidateRebaseTargetHandler:
    """Warn about rebase target when local branch exists and differs."""

    def check(self, command: str, context: dict) -> dict | None:
        # Check for git rebase with origin/ prefix
        command_lower = command.lower()
        pattern = r"(^|[;&|])\s*git\s+rebase\s+(-[a-z]+\s+|--[a-z-]+\s+)*origin/"

        if not re.search(pattern, command_lower):
            return None

        # Extract branch name after origin/
        match = re.search(r"origin/([a-zA-Z0-9_-]+)", command)
        if not match:
            return None

        remote_branch = match.group(1)

        # Check if local branch exists
        try:
            subprocess.run(
                ["git", "rev-parse", "--verify", remote_branch],
                capture_output=True,
                check=True,
                timeout=5
            )
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            # Local branch doesn't exist
            return None

        # Get local and remote commits
        try:
            local_result = subprocess.run(
                ["git", "rev-parse", remote_branch],
                capture_output=True,
                text=True,
                timeout=5
            )
            local_commit = local_result.stdout.strip()

            remote_result = subprocess.run(
                ["git", "rev-parse", f"origin/{remote_branch}"],
                capture_output=True,
                text=True,
                timeout=5
            )
            remote_commit = remote_result.stdout.strip()
        except (subprocess.TimeoutExpired, Exception):
            return None

        if local_commit and remote_commit and local_commit != remote_commit:
            return {
                "warning": f"""
⚠️  REBASE TARGET WARNING
═══════════════════════════════════════════════════════════════

You used: git rebase origin/{remote_branch}
Local branch exists: {remote_branch}

LOCAL vs REMOTE DIFFER:
  Local  {remote_branch}: {local_commit[:7]}
  Remote origin/{remote_branch}: {remote_commit[:7]}

Per git-workflow.md § Branch Reference Resolution:
  ✅ PREFER: git rebase {remote_branch}  (uses local branch)
  ⚠️  USED: git rebase origin/{remote_branch}  (uses remote)

WHEN TO USE WHICH:
  • Use local {remote_branch}: Default when user says "rebase on {remote_branch}"
  • Use origin/{remote_branch}: Only if user explicitly requests remote

If user said "rebase on {remote_branch}", next time use:
  git rebase {remote_branch}

═══════════════════════════════════════════════════════════════"""
            }

        return None


register_handler(ValidateRebaseTargetHandler())
