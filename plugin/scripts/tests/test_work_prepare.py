"""
Tests for work-prepare.py check_base_branch_commits() function.

Tests the false positive filtering that prevents planning commits (decompose, add issue, etc.)
from being flagged as suspicious evidence that an issue is already complete.
"""

import unittest
from unittest.mock import patch, MagicMock
from pathlib import Path
import sys
import os
import importlib.util

# Add parent directory to path to import work-prepare
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

# Import module with hyphen in name using importlib
import importlib.util
spec = importlib.util.spec_from_file_location(
    "work_prepare",
    os.path.join(os.path.dirname(os.path.dirname(__file__)), "work-prepare.py")
)
work_prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(work_prepare)
check_base_branch_commits = work_prepare.check_base_branch_commits


class TestCheckBaseBranchCommits(unittest.TestCase):
    """Test check_base_branch_commits() false positive filtering."""

    def test_filters_decompose_commits(self):
        """config: decompose commits should be filtered out (not flagged as suspicious)."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "abc123 config: decompose port-utility-scripts into 4 sub-issues\n"

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "port-utility-scripts"
            )

        # Decompose commits should be filtered out, returning None
        self.assertIsNone(result)

    def test_filters_planning_commits(self):
        """planning: commits should be filtered out."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "def456 planning: add issue port-analysis-to-java to 2.1\n"

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "port-analysis-to-java"
            )

        self.assertIsNone(result)

    def test_filters_add_issue_commits(self):
        """config: add issue commits should be filtered out."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "ghi789 config: add issue fix-bug-123 to v2.1\n"

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "fix-bug-123"
            )

        self.assertIsNone(result)

    def test_filters_add_task_commits(self):
        """config: add task commits should be filtered out."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "jkl012 config: add task update-docs to 2.1\n"

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "update-docs"
            )

        self.assertIsNone(result)

    def test_filters_mark_commits(self):
        """config: mark commits should be filtered out."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "mno345 config: mark issue fix-auth as complete\n"

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "fix-auth"
            )

        self.assertIsNone(result)

    def test_does_not_filter_implementation_commits(self):
        """Legitimate implementation commits should NOT be filtered out."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "pqr678 feature: port analysis scripts to java\n"

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "port-analysis"
            )

        # Implementation commit should be returned as suspicious (potential duplicate work)
        self.assertIsNotNone(result)
        self.assertIn("pqr678", result)
        self.assertIn("feature: port analysis scripts to java", result)

    def test_mixed_commits_returns_only_non_planning(self):
        """Mix of planning and implementation commits should return only implementation."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = (
            "abc123 config: decompose port-scripts into sub-issues\n"
            "def456 feature: port scripts to java implementation\n"
            "ghi789 planning: add issue port-scripts to 2.1\n"
        )

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "port-scripts"
            )

        # Only the feature commit should be returned
        self.assertIsNotNone(result)
        self.assertIn("def456", result)
        self.assertIn("feature: port scripts to java implementation", result)
        self.assertNotIn("abc123", result)
        self.assertNotIn("ghi789", result)

    def test_empty_git_output_returns_none(self):
        """Empty git output should return None."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = ""

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "nonexistent-issue"
            )

        self.assertIsNone(result)

    def test_git_error_returns_none(self):
        """Git command errors should return None."""
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_result.stdout = ""

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "some-issue"
            )

        self.assertIsNone(result)

    def test_case_insensitive_filtering(self):
        """Prefix matching should be case-insensitive."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "abc123 PLANNING: add issue test-issue to 2.1\n"

        with patch('subprocess.run', return_value=mock_result):
            result = check_base_branch_commits(
                Path("/fake/path"),
                "v2.1",
                "test-issue"
            )

        # Uppercase PLANNING should be filtered (case-insensitive)
        self.assertIsNone(result)


if __name__ == '__main__':
    unittest.main()
