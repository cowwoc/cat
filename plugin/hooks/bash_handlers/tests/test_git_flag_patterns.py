"""
Tests for git command flag pattern matching (A014).

Verifies that hook patterns correctly match git commands with various flags:
- git -C /path command
- git --git-dir=/path command
- git -c config.key=value command

This prevents bypass of hooks via flag variations (addresses M398).
"""

import unittest
import re


class TestGitFlagPatterns(unittest.TestCase):
    """Test git command patterns handle various flags."""

    def test_validate_worktree_branch_pattern_basic(self):
        """Pattern should match basic git checkout/switch/worktree."""
        # This is the pattern from validate-worktree-branch.sh line 14
        pattern = r"^git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch|worktree)"

        # Basic commands
        self.assertIsNotNone(re.search(pattern, "git checkout main"))
        self.assertIsNotNone(re.search(pattern, "git switch feature"))
        self.assertIsNotNone(re.search(pattern, "git worktree add .claude/cat/worktrees/task"))

    def test_validate_worktree_branch_pattern_with_c_flag(self):
        """Pattern should match git -C /path checkout/switch/worktree."""
        pattern = r"^git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch|worktree)"

        # With -C flag
        self.assertIsNotNone(re.search(pattern, "git -C /workspace checkout main"))
        self.assertIsNotNone(re.search(pattern, "git -C /workspace/.claude/cat/worktrees/task switch branch"))
        self.assertIsNotNone(re.search(pattern, "git -C /path worktree add .claude/cat/worktrees/task"))

    def test_validate_worktree_branch_pattern_with_git_dir(self):
        """Pattern should match git --git-dir=/path checkout/switch/worktree."""
        pattern = r"^git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch|worktree)"

        # With --git-dir flag
        self.assertIsNotNone(re.search(pattern, "git --git-dir=/workspace/.git checkout main"))
        self.assertIsNotNone(re.search(pattern, "git --git-dir=/path switch branch"))

    def test_validate_worktree_branch_pattern_with_config(self):
        """Pattern should match git -c config.key=value checkout/switch/worktree."""
        pattern = r"^git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch|worktree)"

        # With -c flag
        self.assertIsNotNone(re.search(pattern, "git -c user.name=test checkout main"))
        self.assertIsNotNone(re.search(pattern, "git -c core.editor=vim switch branch"))

    def test_validate_worktree_branch_pattern_rejects_other_commands(self):
        """Pattern should NOT match other git commands."""
        pattern = r"^git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch|worktree)"

        # Should NOT match
        self.assertIsNone(re.search(pattern, "git status"))
        self.assertIsNone(re.search(pattern, "git commit -m 'message'"))
        self.assertIsNone(re.search(pattern, "git push origin main"))
        self.assertIsNone(re.search(pattern, "git -C /workspace status"))

    def test_block_main_rebase_checkout_pattern_basic(self):
        """Pattern should match basic git checkout/switch."""
        # This is the pattern from block_main_rebase.py line 20
        pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch)\s+'

        # Basic commands
        self.assertIsNotNone(re.search(pattern, "git checkout main"))
        self.assertIsNotNone(re.search(pattern, "git switch feature"))
        self.assertIsNotNone(re.search(pattern, "cd /workspace && git checkout main"))

    def test_block_main_rebase_checkout_pattern_with_flags(self):
        """Pattern should match git checkout/switch with flags."""
        pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch)\s+'

        # With -C flag
        self.assertIsNotNone(re.search(pattern, "git -C /workspace checkout main"))
        self.assertIsNotNone(re.search(pattern, "cd /tmp && git -C /workspace switch branch"))

        # With --git-dir flag
        self.assertIsNotNone(re.search(pattern, "git --git-dir=/workspace/.git checkout main"))

        # With -c flag
        self.assertIsNotNone(re.search(pattern, "git -c user.name=test checkout main"))

    def test_block_main_rebase_rebase_pattern_basic(self):
        """Pattern should match basic git rebase."""
        # This is the pattern from block_main_rebase.py line 71
        pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+rebase'

        # Basic commands
        self.assertIsNotNone(re.search(pattern, "git rebase main"))
        self.assertIsNotNone(re.search(pattern, "git rebase -i HEAD~3"))
        self.assertIsNotNone(re.search(pattern, "cd /workspace && git rebase main"))

    def test_block_main_rebase_rebase_pattern_with_flags(self):
        """Pattern should match git rebase with flags."""
        pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+rebase'

        # With -C flag
        self.assertIsNotNone(re.search(pattern, "git -C /workspace rebase main"))
        self.assertIsNotNone(re.search(pattern, "git -C /path rebase -i HEAD~3"))

        # With --git-dir flag
        self.assertIsNotNone(re.search(pattern, "git --git-dir=/workspace/.git rebase main"))

        # With -c flag
        self.assertIsNotNone(re.search(pattern, "git -c user.name=test rebase main"))

    def test_block_main_rebase_rebase_pattern_rejects_other_commands(self):
        """Pattern should NOT match other git commands."""
        pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+rebase'

        # Should NOT match
        self.assertIsNone(re.search(pattern, "git status"))
        self.assertIsNone(re.search(pattern, "git commit -m 'message'"))
        self.assertIsNone(re.search(pattern, "git -C /workspace status"))

    def test_complex_command_chains(self):
        """Test pattern matching in complex command chains."""
        checkout_pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch)\s+'
        rebase_pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+rebase'

        # Chained commands
        self.assertIsNotNone(re.search(checkout_pattern, "cd /workspace && git -C /workspace checkout main"))
        self.assertIsNotNone(re.search(rebase_pattern, "git status; git -C /workspace rebase main"))
        self.assertIsNotNone(re.search(checkout_pattern, "git add . || git -C /workspace switch branch"))

    def test_case_sensitivity(self):
        """Patterns should work with case variations (for case-insensitive searches)."""
        # Note: block_main_rebase.py uses command_lower, so test lowercase
        pattern = r'(^|[;&|])\s*git(\s+(-C\s+\S+|--git-dir=\S+|-c\s+\S+))?\s+(checkout|switch)\s+'

        # Lowercase (as it appears in block_main_rebase.py)
        self.assertIsNotNone(re.search(pattern, "git checkout main"))
        self.assertIsNotNone(re.search(pattern, "git -c user.name=test checkout main"))


if __name__ == "__main__":
    unittest.main()
