"""
Verify commit type after git commit completes.

Defense-in-depth - catches commits that slip through PreToolUse validation.
This is a WARNING hook (does not block) since the commit already happened.

Trigger: PostToolUse for Bash (git commit commands)
"""

import re
import subprocess
from . import register_handler


CLAUDE_FACING_PATTERNS = [
    "CLAUDE.md",
    ".claude/",
    "hooks/",
    "skills/",
    "workflows/",
    "commands/",
    "retrospectives/",
    "mistakes.json",      # Legacy single file
    "mistakes-",          # Split files: mistakes-YYYY-MM.json
    "retrospectives-",    # Split files: retrospectives-YYYY-MM.json
    "index.json",         # Retrospective index
]

SOURCE_PATTERNS = [
    r"\.java$",
    r"\.py$",
    r"\.js$",
    r"\.ts$",
    r"\.go$",
    r"\.rs$",
    r"src/",
    r"lib/",
]


class VerifyCommitTypeHandler:
    """Verify commit type matches file types."""

    def check(self, command: str, context: dict) -> dict | None:
        # Only check git commit commands
        if "git commit" not in command:
            return None

        # Skip amend (user is already fixing)
        if "--amend" in command:
            return None

        # Get commit message and files
        try:
            msg_result = subprocess.run(
                ["git", "log", "-1", "--format=%B"],
                capture_output=True,
                text=True,
                timeout=5
            )
            commit_msg = msg_result.stdout.strip()

            files_result = subprocess.run(
                ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"],
                capture_output=True,
                text=True,
                timeout=5
            )
            commit_files = files_result.stdout.strip()

            hash_result = subprocess.run(
                ["git", "log", "-1", "--format=%h"],
                capture_output=True,
                text=True,
                timeout=5
            )
            commit_hash = hash_result.stdout.strip()
        except (subprocess.TimeoutExpired, Exception):
            return None

        if not commit_msg:
            return None

        # Extract commit type
        type_match = re.match(r"^([a-z]+):", commit_msg)
        if not type_match:
            return None

        commit_type = type_match.group(1)

        # Check for docs: used on Claude-facing files
        if commit_type == "docs":
            for pattern in CLAUDE_FACING_PATTERNS:
                if pattern in commit_files:
                    return {
                        "warning": f"""
⚠️  POST-COMMIT WARNING: 'docs:' used for Claude-facing file

Commit {commit_hash} contains Claude-facing files (matched: {pattern})
Claude-facing files should use 'config:', not 'docs:'

Rule (M089): docs: = user-facing, config: = Claude-facing

TO FIX: git commit --amend
  Then change 'docs:' to 'config:' in the commit message"""
                    }

        # Check for config: used on source code
        if commit_type == "config":
            for pattern in SOURCE_PATTERNS:
                if re.search(pattern, commit_files, re.MULTILINE):
                    return {
                        "warning": f"""
⚠️  POST-COMMIT WARNING: 'config:' used for source code

Commit {commit_hash} contains source code files (matched: {pattern})
Source code should use: feature:, bugfix:, refactor:, test:, or performance:

TO FIX: git commit --amend"""
                    }

        return None


register_handler(VerifyCommitTypeHandler())
