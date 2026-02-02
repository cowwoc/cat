"""
Tests for check-existing-work.sh (M362, M363).

M362: Original issue - detect existing work on task branch
M363: Should be a handler/script, not LLM-executed instructions
"""

import unittest
import subprocess
import tempfile
import os
import shutil
import json


class TestCheckExistingWork(unittest.TestCase):
    """Test existing work detection script."""

    SCRIPT_PATH = os.path.join(
        os.path.dirname(os.path.dirname(__file__)),
        "check-existing-work.sh"
    )

    @classmethod
    def setUpClass(cls):
        """Create a temporary git repo for testing."""
        cls.temp_dir = tempfile.mkdtemp()
        cls.main_repo = os.path.join(cls.temp_dir, "main")
        cls.worktree_with_commits = os.path.join(cls.temp_dir, "worktree-with-commits")
        cls.worktree_no_commits = os.path.join(cls.temp_dir, "worktree-no-commits")

        # Create main repo
        os.makedirs(cls.main_repo)
        subprocess.run(["git", "init"], cwd=cls.main_repo, capture_output=True)
        subprocess.run(["git", "config", "user.email", "test@test.com"], cwd=cls.main_repo, capture_output=True)
        subprocess.run(["git", "config", "user.name", "Test"], cwd=cls.main_repo, capture_output=True)

        # Create initial commit on main
        with open(os.path.join(cls.main_repo, "README.md"), "w") as f:
            f.write("# Test\n")
        subprocess.run(["git", "add", "README.md"], cwd=cls.main_repo, capture_output=True)
        subprocess.run(["git", "commit", "-m", "Initial commit"], cwd=cls.main_repo, capture_output=True)

        # Create base branch
        subprocess.run(["git", "branch", "v2.1"], cwd=cls.main_repo, capture_output=True)

        # Create worktree WITH commits
        subprocess.run([
            "git", "worktree", "add", "-b", "task-with-work",
            cls.worktree_with_commits, "v2.1"
        ], cwd=cls.main_repo, capture_output=True)

        # Add commits to the worktree
        with open(os.path.join(cls.worktree_with_commits, "feature.txt"), "w") as f:
            f.write("Feature 1\n")
        subprocess.run(["git", "add", "feature.txt"], cwd=cls.worktree_with_commits, capture_output=True)
        subprocess.run(["git", "commit", "-m", "Add feature 1"], cwd=cls.worktree_with_commits, capture_output=True)

        with open(os.path.join(cls.worktree_with_commits, "feature2.txt"), "w") as f:
            f.write("Feature 2\n")
        subprocess.run(["git", "add", "feature2.txt"], cwd=cls.worktree_with_commits, capture_output=True)
        subprocess.run(["git", "commit", "-m", "Add feature 2"], cwd=cls.worktree_with_commits, capture_output=True)

        # Create worktree WITHOUT commits
        subprocess.run([
            "git", "worktree", "add", "-b", "task-no-work",
            cls.worktree_no_commits, "v2.1"
        ], cwd=cls.main_repo, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        """Clean up temporary directory."""
        # Remove worktrees first
        subprocess.run(["git", "worktree", "remove", cls.worktree_with_commits, "--force"],
                      cwd=cls.main_repo, capture_output=True)
        subprocess.run(["git", "worktree", "remove", cls.worktree_no_commits, "--force"],
                      cwd=cls.main_repo, capture_output=True)
        shutil.rmtree(cls.temp_dir)

    def run_script(self, worktree, base_branch):
        """Run the check-existing-work.sh script."""
        result = subprocess.run(
            [self.SCRIPT_PATH, "--worktree", worktree, "--base-branch", base_branch],
            capture_output=True,
            text=True
        )
        return result

    def test_detects_existing_work(self):
        """M362: Should detect when worktree has commits ahead of base."""
        result = self.run_script(self.worktree_with_commits, "v2.1")
        self.assertEqual(result.returncode, 0)

        output = json.loads(result.stdout)
        self.assertTrue(output["has_existing_work"])
        self.assertEqual(output["existing_commits"], 2)
        self.assertIn("feature", output["commit_summary"].lower())

    def test_detects_no_existing_work(self):
        """M362: Should detect when worktree has no commits ahead of base."""
        result = self.run_script(self.worktree_no_commits, "v2.1")
        self.assertEqual(result.returncode, 0)

        output = json.loads(result.stdout)
        self.assertFalse(output["has_existing_work"])
        self.assertEqual(output["existing_commits"], 0)
        self.assertEqual(output["commit_summary"], "")

    def test_missing_worktree_arg(self):
        """Should fail with clear error if --worktree missing."""
        result = subprocess.run(
            [self.SCRIPT_PATH, "--base-branch", "v2.1"],
            capture_output=True,
            text=True
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("--worktree is required", result.stderr)

    def test_missing_base_branch_arg(self):
        """Should fail with clear error if --base-branch missing."""
        result = subprocess.run(
            [self.SCRIPT_PATH, "--worktree", self.worktree_no_commits],
            capture_output=True,
            text=True
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("--base-branch is required", result.stderr)

    def test_invalid_worktree_path(self):
        """Should fail with clear error for non-existent worktree."""
        result = self.run_script("/nonexistent/path", "v2.1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Cannot access worktree", result.stderr)

    def test_output_is_valid_json(self):
        """M363: Output must be valid JSON for handler integration."""
        result = self.run_script(self.worktree_with_commits, "v2.1")
        self.assertEqual(result.returncode, 0)

        # Should not raise
        output = json.loads(result.stdout)
        self.assertIn("has_existing_work", output)
        self.assertIn("existing_commits", output)
        self.assertIn("commit_summary", output)


if __name__ == "__main__":
    unittest.main()
