"""
Detect concatenated commit messages after git operations.

Trigger: PostToolUse for Bash (git rebase/commit/merge commands)
"""

import re
import subprocess
from . import register_handler


class DetectConcatenatedCommitHandler:
    """Detect concatenated commit messages from ad-hoc squashing."""

    def check(self, command: str, context: dict) -> dict | None:
        # Only process git commands
        if "git" not in command:
            return None

        # Only check commands that can create commits
        if not re.search(r"(rebase|commit|merge)", command):
            return None

        # Get the most recent commit message
        try:
            result = subprocess.run(
                ["git", "log", "-1", "--format=%B"],
                capture_output=True,
                text=True,
                timeout=5
            )
            commit_msg = result.stdout.strip()
        except (subprocess.TimeoutExpired, Exception):
            return None

        if not commit_msg:
            return None

        # Count Co-Authored-By lines
        co_authored_count = len(re.findall(r"^Co-Authored-By:", commit_msg, re.MULTILINE))

        if co_authored_count > 1:
            try:
                result = subprocess.run(
                    ["git", "log", "-1", "--format=%h"],
                    capture_output=True,
                    text=True,
                    timeout=5
                )
                commit_hash = result.stdout.strip()
            except Exception:
                commit_hash = "HEAD"

            return {
                "warning": f"""
⚠️  CONCATENATED COMMIT MESSAGE DETECTED

Commit {commit_hash} has {co_authored_count} 'Co-Authored-By' lines.
This indicates a concatenated message from ad-hoc squashing.

CLAUDE.md § Always Use git-squash Skill:
  Ad-hoc 'git rebase -i' with squash produces concatenated messages.
  Squashed commits need UNIFIED messages describing the final result.

RECOMMENDATION:
  Use the git-squash skill which enforces writing a new unified message.
  The skill prompts you to describe what the final code DOES.

TO FIX THIS COMMIT:
  1. git reset --soft HEAD~1  # Unstage commit
  2. git commit  # Write unified message
  Or use: git commit --amend  # Rewrite message"""
            }

        return None


register_handler(DetectConcatenatedCommitHandler())
