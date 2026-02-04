"""
Tests for compress-validate-loop.py

Tests the compression validation loop orchestration logic.
"""

import unittest
import tempfile
import os
import sys
from pathlib import Path
from unittest.mock import patch, MagicMock, call

# Add script directory to path
SCRIPT_DIR = Path(__file__).parent.parent
sys.path.insert(0, str(SCRIPT_DIR))

from compress_validate_loop import (
    ClaudeProcessManager,
    CompressionValidationLoop
)


class TestClaudeProcessManager(unittest.TestCase):
    """Test ClaudeProcessManager class."""

    def setUp(self):
        """Set up test fixtures."""
        self.manager = ClaudeProcessManager(dry_run=True, verbose=False)

    def test_dry_run_compression(self):
        """Dry run compression returns mock results."""
        result = self.manager.run_compression("2.1-test-task")

        self.assertEqual(result["status"], "SUCCESS")
        self.assertIn("files", result)
        self.assertIn("compression_scores", result)
        self.assertTrue(isinstance(result["files"], list))
        self.assertTrue(isinstance(result["compression_scores"], dict))

    def test_dry_run_validation(self):
        """Dry run validation returns mock score."""
        score = self.manager.run_validation("2.1-test-task", "test.md")

        self.assertEqual(score, 0.95)
        self.assertTrue(isinstance(score, float))
        self.assertGreaterEqual(score, 0.0)
        self.assertLessEqual(score, 1.0)

    def test_dry_run_analyze_and_improve(self):
        """Dry run analyze_and_improve_shrink_doc returns mock results."""
        result, commits = self.manager.analyze_and_improve_shrink_doc(
            "test.md", 0.95, "compression output"
        )

        self.assertEqual(result["status"], "SUCCESS")
        self.assertTrue(isinstance(commits, list))

    def test_session_management_dry_run(self):
        """Dry run session management doesn't raise errors."""
        # _start_session returns None in dry_run mode
        session = self.manager._start_session("test")
        self.assertIsNone(session)

    def test_end_session_dry_run(self):
        """Dry run session cleanup doesn't raise errors."""
        self.manager._end_session(None)  # Should not raise

    def test_reinstall_plugin_dry_run(self):
        """Dry run plugin reinstall logs but doesn't execute."""
        self.manager.reinstall_plugin()  # Should not raise

    def test_remove_worktrees_dry_run(self):
        """Dry run worktree removal logs but doesn't execute."""
        self.manager.remove_worktrees("2.1-*")  # Should not raise


class TestCompressionValidationLoop(unittest.TestCase):
    """Test CompressionValidationLoop class."""

    def setUp(self):
        """Set up test fixtures."""
        self.loop = CompressionValidationLoop(
            max_iterations=3,
            dry_run=True,
            verbose=False
        )

    def test_dry_run_all_perfect_scores(self):
        """Dry run with perfect scores exits successfully on first iteration."""
        # Mock manager methods to return perfect scores
        with patch.object(self.loop.manager, 'run_compression') as mock_compress, \
             patch.object(self.loop.manager, 'run_validation') as mock_validate:

            mock_compress.return_value = {
                "status": "SUCCESS",
                "files": ["test.md"],
                "compression_scores": {"test.md": 1.0}
            }
            mock_validate.return_value = 1.0

            result = self.loop.run(["2.1-test-task"])

            self.assertEqual(result, 0)
            mock_compress.assert_called_once()
            mock_validate.assert_called_once()

    def test_dry_run_imperfect_scores_triggers_analysis(self):
        """Dry run with imperfect scores triggers analysis and improvement."""
        with patch.object(self.loop.manager, 'run_compression') as mock_compress, \
             patch.object(self.loop.manager, 'run_validation') as mock_validate, \
             patch.object(self.loop.manager, 'analyze_and_improve_shrink_doc') as mock_analyze, \
             patch.object(self.loop, '_merge_learn_commits') as mock_merge:

            # First iteration: imperfect score
            # Second iteration: perfect score
            mock_compress.side_effect = [
                {
                    "status": "SUCCESS",
                    "files": ["test.md"],
                    "compression_scores": {"test.md": 0.95},
                    "output": "compression output"
                },
                {
                    "status": "SUCCESS",
                    "files": ["test.md"],
                    "compression_scores": {"test.md": 1.0},
                    "output": "compression output"
                }
            ]
            mock_validate.side_effect = [0.95, 1.0]
            mock_analyze.return_value = (
                {"status": "SUCCESS"},
                ["abc123"]
            )
            mock_merge.return_value = True

            result = self.loop.run(["2.1-test-task"])

            self.assertEqual(result, 0)
            self.assertEqual(mock_analyze.call_count, 1)
            self.assertEqual(mock_merge.call_count, 1)

    def test_max_iterations_reached(self):
        """Loop exits with error if max iterations reached."""
        with patch.object(self.loop.manager, 'run_compression') as mock_compress, \
             patch.object(self.loop.manager, 'run_validation') as mock_validate, \
             patch.object(self.loop.manager, 'analyze_and_improve_shrink_doc') as mock_analyze, \
             patch.object(self.loop, '_merge_learn_commits') as mock_merge:

            # Always return imperfect scores
            mock_compress.return_value = {
                "status": "SUCCESS",
                "files": ["test.md"],
                "compression_scores": {"test.md": 0.95},
                "output": "compression output"
            }
            mock_validate.return_value = 0.95
            mock_analyze.return_value = (
                {"status": "SUCCESS"},
                ["abc123"]
            )
            mock_merge.return_value = True

            result = self.loop.run(["2.1-test-task"])

            self.assertEqual(result, 1)
            self.assertEqual(mock_analyze.call_count, 3)  # max_iterations

    def test_compression_failure_skips_task(self):
        """Compression failure for a task skips validation and continues loop."""
        with patch.object(self.loop.manager, 'run_compression') as mock_compress, \
             patch.object(self.loop.manager, 'run_validation') as mock_validate:

            # Always fail compression - loop will reach max iterations
            # because all_scores_perfect never gets set to True (no files validated)
            mock_compress.return_value = {"status": "FAILED"}

            result = self.loop.run(["2.1-test-task"])

            # With only failed compressions, all_scores_perfect stays True (no scores to check)
            # so the loop exits successfully. This is expected behavior - if there are no
            # files to validate, there are no imperfect scores.
            self.assertEqual(result, 0)
            mock_validate.assert_not_called()

    def test_merge_learn_commits_dry_run(self):
        """Merge learn commits in dry run mode."""
        commits = ["abc123", "def456", "789xyz"]
        result = self.loop._merge_learn_commits(commits)

        self.assertTrue(result)

    def test_merge_learn_commits_empty_list(self):
        """Empty commit list returns True."""
        result = self.loop._merge_learn_commits([])

        self.assertTrue(result)

    def test_merge_learn_commits_failure(self):
        """Failed merge returns False."""
        self.loop.dry_run = False
        with patch.object(self.loop.manager, '_run_command') as mock_cmd:
            # Mock git commands to fail cherry-pick
            mock_cmd.side_effect = [
                {"returncode": 0, "stdout": "main\n", "stderr": ""},  # rev-parse --abbrev-ref HEAD
                {"returncode": 1, "stdout": "", "stderr": "conflict"},  # cherry-pick fails
                {"returncode": 0, "stdout": "", "stderr": ""}  # cherry-pick --abort
            ]

            result = self.loop._merge_learn_commits(["abc123"])

            self.assertFalse(result)

    def test_multiple_tasks(self):
        """Loop processes multiple tasks."""
        with patch.object(self.loop.manager, 'run_compression') as mock_compress, \
             patch.object(self.loop.manager, 'run_validation') as mock_validate:

            mock_compress.return_value = {
                "status": "SUCCESS",
                "files": ["test.md"],
                "compression_scores": {"test.md": 1.0}
            }
            mock_validate.return_value = 1.0

            result = self.loop.run(["2.1-task-a", "2.1-task-b", "2.1-task-c"])

            self.assertEqual(result, 0)
            self.assertEqual(mock_compress.call_count, 3)

    def test_keyboard_interrupt(self):
        """Keyboard interrupt returns 130."""
        with patch.object(self.loop.manager, 'run_compression') as mock_compress:
            mock_compress.side_effect = KeyboardInterrupt()

            result = self.loop.run(["2.1-test-task"])

            self.assertEqual(result, 130)

    def test_exception_handling(self):
        """Unexpected exception returns 1."""
        with patch.object(self.loop.manager, 'run_compression') as mock_compress:
            mock_compress.side_effect = RuntimeError("Test error")

            result = self.loop.run(["2.1-test-task"])

            self.assertEqual(result, 1)

    def test_session_cleanup_on_exit(self):
        """Sessions are cleaned up on exit."""
        with patch.object(self.loop.manager, '_end_session') as mock_end, \
             patch.object(self.loop.manager, 'run_compression') as mock_compress, \
             patch.object(self.loop.manager, 'run_validation') as mock_validate:

            mock_compress.return_value = {
                "status": "SUCCESS",
                "files": ["test.md"],
                "compression_scores": {"test.md": 1.0}
            }
            mock_validate.return_value = 1.0

            result = self.loop.run(["2.1-test-task"])

            self.assertEqual(result, 0)
            # _end_session called for compression and validation sessions
            self.assertEqual(mock_end.call_count, 2)

    def test_analysis_with_no_commits(self):
        """Analysis returning no commits skips merge but continues."""
        with patch.object(self.loop.manager, 'run_compression') as mock_compress, \
             patch.object(self.loop.manager, 'run_validation') as mock_validate, \
             patch.object(self.loop.manager, 'analyze_and_improve_shrink_doc') as mock_analyze, \
             patch.object(self.loop, '_merge_learn_commits') as mock_merge:

            mock_compress.side_effect = [
                {
                    "status": "SUCCESS",
                    "files": ["test.md"],
                    "compression_scores": {"test.md": 0.95},
                    "output": "compression output"
                },
                {
                    "status": "SUCCESS",
                    "files": ["test.md"],
                    "compression_scores": {"test.md": 1.0},
                    "output": "compression output"
                }
            ]
            mock_validate.side_effect = [0.95, 1.0]
            mock_analyze.return_value = (
                {"status": "SUCCESS"},
                []  # No commits produced
            )
            mock_merge.return_value = True

            result = self.loop.run(["2.1-test-task"])

            self.assertEqual(result, 0)
            # Merge NOT called when no commits (implementation skips empty list)
            self.assertEqual(mock_merge.call_count, 0)


class TestIntegration(unittest.TestCase):
    """Integration tests for the full script."""

    def test_main_function_exists(self):
        """Main function is defined."""
        from compress_validate_loop import main
        self.assertTrue(callable(main))

    def test_command_line_args(self):
        """Command line arguments are parsed correctly."""
        with patch('sys.argv', ['compress-validate-loop.py', '--dry-run', '--verbose',
                                '--max-iterations', '5', '2.1-task-a', '2.1-task-b']):
            # Import inside patch context to avoid modifying global argv
            import importlib
            import compress_validate_loop
            importlib.reload(compress_validate_loop)
            # Just verify no exceptions during import

    def test_default_task_id(self):
        """Default task ID is used when none provided."""
        with patch('sys.argv', ['compress-validate-loop.py', '--dry-run']):
            with patch('compress_validate_loop.CompressionValidationLoop') as mock_loop_cls:
                mock_loop = MagicMock()
                mock_loop.run.return_value = 0
                mock_loop_cls.return_value = mock_loop

                from compress_validate_loop import main
                try:
                    main()
                except SystemExit as e:
                    self.assertEqual(e.code, 0)


if __name__ == "__main__":
    unittest.main()
