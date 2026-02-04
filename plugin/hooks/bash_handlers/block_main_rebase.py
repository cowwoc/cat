"""
Block git rebase on main branch and checkout changes in main worktree.

M205: Block ANY checkout in main worktree.
"""

import os
import re
import subprocess
from . import register_handler


class BlockMainRebaseHandler:
    """Prevent rebase on main branch and checkout in main worktree."""

    def check(self, command: str, context: dict) -> dict | None:
        command_lower = command.lower()

        # Check for git checkout/switch in main worktree
        # Pattern handles: git -C /path, git --git-dir=/path, git -c config.key=value
        if re.search(r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch)\s+', command_lower):
            # Check if command cd's to /workspace
            if re.search(r"cd\s+(/workspace|['\"]*/workspace['\"]*)(\s|&&|;|$)", command):
                # Extract target
                match = re.search(r'git\s+(?:checkout|switch)\s+([^\s;&|]+)', command)
                target = match.group(1) if match else "unknown"

                if target not in ("--", "-b", "-B"):
                    return {
                        "decision": "block",
                        "reason": f"""🚨 GIT CHECKOUT IN MAIN WORKTREE BLOCKED (M205)

❌ Attempted: git checkout {target} in main worktree
✅ Correct:   Use task worktrees - never change main worktree's branch

WHY THIS IS BLOCKED:
• The main worktree (/workspace) should keep its current branch
• Task worktrees exist precisely to avoid touching main workspace state
• Changing main worktree's branch disrupts operations

WHAT TO DO INSTEAD:
• For task work: Use the task worktree at /workspace/.worktrees/<branch>
• For cleanup: Delete the worktree directory, don't checkout in main"""
                    }

            # Check if currently in /workspace (main worktree)
            cwd = os.getcwd()
            if cwd == "/workspace":
                # Verify it's the main worktree, not a subdir
                try:
                    git_common = subprocess.run(
                        ["git", "rev-parse", "--git-common-dir"],
                        capture_output=True, text=True, timeout=5
                    ).stdout.strip()
                    git_dir = subprocess.run(
                        ["git", "rev-parse", "--git-dir"],
                        capture_output=True, text=True, timeout=5
                    ).stdout.strip()

                    if git_common == git_dir or git_common == ".git":
                        match = re.search(r'git\s+(?:checkout|switch)\s+([^\s;&|]+)', command)
                        target = match.group(1) if match else "unknown"
                        if target not in ("--", "-b", "-B"):
                            return {
                                "decision": "block",
                                "reason": f"Blocked (M205): Cannot checkout '{target}' in main worktree. Use task worktrees instead."
                            }
                except Exception:
                    pass

        # Check for git rebase command
        # Pattern handles: git -C /path, git --git-dir=/path, git -c config.key=value
        if not re.search(r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+rebase', command_lower):
            return None

        # Check if rebasing on main
        current_branch = self._get_current_branch(command)
        if current_branch == "main":
            return {
                "decision": "block",
                "reason": """🚨 REBASE ON MAIN BLOCKED

❌ Attempted: git rebase on main branch
✅ Correct:   Main branch should never be rebased

WHY THIS IS BLOCKED:
• Rebasing main rewrites commit history
• Merged commits get recreated as direct commits
• This breaks the audit trail

TO REBASE A TASK BRANCH ONTO MAIN:
Run from your task's worktree, not main:

  cd /workspace/.worktrees/<task-branch>
  git rebase main"""
            }

        return None

    def _get_current_branch(self, command: str) -> str:
        """Get the current branch, accounting for cd in command."""
        # Check if command cd's to /workspace
        if re.search(r"cd\s+(/workspace|['\"]*/workspace['\"]*)(\s|&&|;|$)", command):
            return "main"

        # Check if command cd's elsewhere
        cd_match = re.search(r"^cd\s+['\"]?([^'\";&|]+)['\"]?", command)
        if cd_match:
            target_dir = cd_match.group(1).strip()
            if os.path.isdir(target_dir):
                try:
                    result = subprocess.run(
                        ["git", "-C", target_dir, "branch", "--show-current"],
                        capture_output=True, text=True, timeout=5
                    )
                    return result.stdout.strip()
                except Exception:
                    pass

        # Fallback to current directory
        try:
            result = subprocess.run(
                ["git", "branch", "--show-current"],
                capture_output=True, text=True, timeout=5
            )
            return result.stdout.strip()
        except Exception:
            return ""


register_handler(BlockMainRebaseHandler())
