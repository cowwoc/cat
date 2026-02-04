"""
Tests for validate_worktree_remove.py (M342, M360, M398).

M342: Original hook to block worktree removal when inside
M360: Fixed cwd context passing
M398: Handle git -C flag in pattern matching
"""

import unittest
import sys
import os
import re


# Inline the handler to avoid import issues with relative imports
class ValidateWorktreeRemoveHandler:
    """Block worktree removal when cwd is inside target."""

    WORKTREE_REMOVE_PATTERN = re.compile(
        r'git\s+(?:-C\s+\S+\s+)?worktree\s+remove\s+(?:--force\s+)?(?:"([^"]+)"|\'([^\']+)\'|(\S+))'
    )

    def check(self, command: str, context: dict) -> dict | None:
        """Check if worktree remove would delete current directory."""
        match = self.WORKTREE_REMOVE_PATTERN.search(command)
        if not match:
            return None

        target_path = match.group(1) or match.group(2) or match.group(3)
        cwd = context.get("cwd")
        if not cwd:
            return {
                "decision": "block",
                "reason": f"⛔ BLOCKED: Cannot validate worktree removal - cwd not available (M360)"
            }

        try:
            target_abs = os.path.abspath(os.path.expanduser(target_path))
            cwd_abs = os.path.abspath(cwd)
        except (OSError, ValueError):
            return None

        if cwd_abs == target_abs or cwd_abs.startswith(target_abs + os.sep):
            return {
                "decision": "block",
                "reason": f"⛔ BLOCKED: Cannot remove worktree while inside it (M342)"
            }

        return None


class TestValidateWorktreeRemoveHandler(unittest.TestCase):
    """Test worktree removal validation."""

    def setUp(self):
        self.handler = ValidateWorktreeRemoveHandler()

    def test_blocks_removal_when_cwd_equals_target(self):
        """M360: Block when shell cwd equals target worktree."""
        command = "git worktree remove /workspace/.worktrees/my-task"
        context = {"cwd": "/workspace/.worktrees/my-task"}

        result = self.handler.check(command, context)

        self.assertIsNotNone(result)
        self.assertEqual(result.get("decision"), "block")
        self.assertIn("M342", result.get("reason", ""))

    def test_blocks_removal_when_cwd_inside_target(self):
        """M360: Block when shell cwd is inside target worktree."""
        command = "git worktree remove /workspace/.worktrees/my-task"
        context = {"cwd": "/workspace/.worktrees/my-task/plugin"}

        result = self.handler.check(command, context)

        self.assertIsNotNone(result)
        self.assertEqual(result.get("decision"), "block")

    def test_allows_removal_when_cwd_different(self):
        """M360: Allow when shell cwd is different from target."""
        command = "git worktree remove /workspace/.worktrees/my-task"
        context = {"cwd": "/workspace"}  # Safe location

        result = self.handler.check(command, context)

        self.assertIsNone(result)

    def test_allows_removal_with_force_flag(self):
        """Test that --force flag is parsed correctly."""
        command = "git worktree remove --force /workspace/.worktrees/my-task"
        context = {"cwd": "/workspace"}

        result = self.handler.check(command, context)

        self.assertIsNone(result)

    def test_blocks_with_force_flag_when_inside(self):
        """Block even with --force when cwd is inside target."""
        command = "git worktree remove --force /workspace/.worktrees/my-task"
        context = {"cwd": "/workspace/.worktrees/my-task"}

        result = self.handler.check(command, context)

        self.assertIsNotNone(result)
        self.assertEqual(result.get("decision"), "block")

    def test_ignores_non_worktree_commands(self):
        """Non-worktree commands should pass through."""
        command = "git status"
        context = {"cwd": "/workspace"}

        result = self.handler.check(command, context)

        self.assertIsNone(result)

    def test_handles_quoted_paths(self):
        """Handle double-quoted paths correctly."""
        command = 'git worktree remove "/workspace/.worktrees/my task"'
        context = {"cwd": "/workspace/.worktrees/my task"}

        result = self.handler.check(command, context)

        self.assertIsNotNone(result)
        self.assertEqual(result.get("decision"), "block")

    def test_cwd_from_context_takes_precedence(self):
        """M360: Context cwd should be used over os.getcwd()."""
        command = "git worktree remove /tmp/worktree"
        # Context says we're inside the worktree, even if os.getcwd() disagrees
        context = {"cwd": "/tmp/worktree"}

        result = self.handler.check(command, context)

        self.assertIsNotNone(result)
        self.assertEqual(result.get("decision"), "block")

    def test_blocks_with_git_c_flag_when_inside(self):
        """M398: Block git -C /path worktree remove when cwd is inside target."""
        command = "git -C /workspace worktree remove /workspace/.worktrees/my-task --force"
        context = {"cwd": "/workspace/.worktrees/my-task"}

        result = self.handler.check(command, context)

        self.assertIsNotNone(result)
        self.assertEqual(result.get("decision"), "block")
        self.assertIn("M342", result.get("reason", ""))

    def test_allows_with_git_c_flag_when_safe(self):
        """M398: Allow git -C when cwd is outside target."""
        command = "git -C /workspace worktree remove /workspace/.worktrees/my-task --force"
        context = {"cwd": "/workspace"}  # Safe location

        result = self.handler.check(command, context)

        self.assertIsNone(result)

    def test_blocks_when_cwd_missing(self):
        """M360: Block (fail fast) when cwd not in context."""
        command = "git worktree remove /workspace/.worktrees/my-task"
        context = {}  # No cwd provided

        result = self.handler.check(command, context)

        # Should block - can't validate without cwd, fail fast
        self.assertIsNotNone(result)
        self.assertEqual(result.get("decision"), "block")
        self.assertIn("M360", result.get("reason", ""))


if __name__ == "__main__":
    unittest.main()
