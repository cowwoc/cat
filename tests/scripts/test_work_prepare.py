"""Tests for work-prepare.py script."""

import json
import subprocess
import sys
from pathlib import Path

import pytest


class TestWorkPrepareScript:
    """Tests for work-prepare.py script functionality."""

    @pytest.fixture
    def script_path(self):
        """Get path to work-prepare.py script."""
        return Path(__file__).parent.parent.parent / "plugin" / "scripts" / "work-prepare.py"

    @pytest.fixture
    def temp_cat_structure(self, tmp_path):
        """Create a temporary CAT structure with git repo and sample issues."""
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)

        # Create config
        config_file = cat_dir / "cat-config.json"
        config_file.write_text(json.dumps({"version": "2.0"}))

        # Create issues directory structure
        issues_dir = cat_dir / "issues"
        v2_dir = issues_dir / "v2"
        v2_1_dir = v2_dir / "v2.1"
        v2_1_dir.mkdir(parents=True)

        # Create a sample issue
        issue_dir = v2_1_dir / "test-issue"
        issue_dir.mkdir()

        # STATE.md
        state_file = issue_dir / "STATE.md"
        state_file.write_text(
            "# State\n\n"
            "- **Status:** open\n"
            "- **Progress:** 0%\n"
            "- **Dependencies:** []\n"
            "- **Last Updated:** 2026-02-06\n"
        )

        # PLAN.md
        plan_file = issue_dir / "PLAN.md"
        plan_file.write_text(
            "# Plan: test-issue\n\n"
            "## Goal\n"
            "Test the work-prepare script with a sample issue.\n\n"
            "## Files to Create\n"
            "- `file1.py` - First file\n"
            "- `file2.py` - Second file\n\n"
            "## Files to Modify\n"
            "- `existing.py` - Modify existing\n\n"
            "## Execution Steps\n"
            "1. Create file1.py\n"
            "2. Create file2.py\n"
            "3. Modify existing.py\n"
        )

        # Create locks directory
        locks_dir = cat_dir / "locks"
        locks_dir.mkdir()

        # Initialize git repo
        subprocess.run(["git", "init"], cwd=tmp_path, check=True, capture_output=True)
        subprocess.run(
            ["git", "config", "user.name", "Test User"],
            cwd=tmp_path,
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["git", "config", "user.email", "test@example.com"],
            cwd=tmp_path,
            check=True,
            capture_output=True,
        )

        # Create initial commit
        subprocess.run(["git", "add", "."], cwd=tmp_path, check=True, capture_output=True)
        subprocess.run(
            ["git", "commit", "-m", "Initial commit"],
            cwd=tmp_path,
            check=True,
            capture_output=True,
        )

        # Create v2.1 branch
        subprocess.run(
            ["git", "checkout", "-b", "v2.1"],
            cwd=tmp_path,
            check=True,
            capture_output=True,
        )

        return tmp_path

    def test_argument_parsing(self, script_path, temp_cat_structure):
        """Script requires session-id, project-dir, and trust-level arguments."""
        # Missing session-id
        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--project-dir", str(temp_cat_structure),
                "--trust-level", "medium"
            ],
            capture_output=True,
            text=True,
        )
        assert result.returncode != 0

        # Missing project-dir
        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--trust-level", "medium"
            ],
            capture_output=True,
            text=True,
        )
        assert result.returncode != 0

        # Missing trust-level
        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--project-dir", str(temp_cat_structure)
            ],
            capture_output=True,
            text=True,
        )
        assert result.returncode != 0

    def test_missing_cat_structure(self, script_path, tmp_path):
        """Script returns ERROR when CAT structure is missing."""
        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--project-dir", str(tmp_path),
                "--trust-level", "medium"
            ],
            capture_output=True,
            text=True,
        )

        assert result.returncode == 1
        output = json.loads(result.stdout)
        assert output["status"] == "ERROR"
        assert "cat" in output["message"].lower()

    def test_token_estimation(self, script_path, temp_cat_structure):
        """Script estimates tokens based on PLAN.md content."""
        # The test issue has:
        # - 2 files to create (5000 each = 10000)
        # - 1 file to modify (3000 each = 3000)
        # - 3 steps (2000 each = 6000)
        # - Base overhead: 10000
        # Expected: ~29000 tokens

        # Import estimate_tokens function for direct testing
        sys.path.insert(0, str(script_path.parent))
        from pathlib import Path as P
        import importlib.util
        spec = importlib.util.spec_from_file_location("work_prepare", script_path)
        work_prepare = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(work_prepare)

        plan_path = temp_cat_structure / ".claude" / "cat" / "issues" / "v2" / "v2.1" / "test-issue" / "PLAN.md"
        estimated = work_prepare.estimate_tokens(plan_path)

        # Expected: 2*5000 + 1*3000 + 3*2000 + 10000 = 29000
        assert estimated == 29000, f"Expected 29000 tokens, got {estimated}"

    def test_no_tasks_diagnostic_info(self, script_path, temp_cat_structure):
        """Script returns diagnostic info when no tasks available."""
        # Close the only available issue
        issue_dir = temp_cat_structure / ".claude" / "cat" / "issues" / "v2" / "v2.1" / "test-issue"
        state_file = issue_dir / "STATE.md"
        state_file.write_text(
            "# State\n\n"
            "- **Status:** closed\n"
            "- **Progress:** 100%\n"
            "- **Dependencies:** []\n"
            "- **Last Updated:** 2026-02-06\n"
        )

        # Commit the change
        subprocess.run(
            ["git", "add", "."],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True
        )
        subprocess.run(
            ["git", "commit", "-m", "Close issue"],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True
        )

        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--project-dir", str(temp_cat_structure),
                "--trust-level", "medium"
            ],
            capture_output=True,
            text=True,
        )

        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert output["status"] == "NO_TASKS"
        assert "closed_count" in output
        assert "total_count" in output
        assert output["closed_count"] == 1
        assert output["total_count"] == 1

    def test_blocked_task_detection(self, script_path, temp_cat_structure):
        """Script detects and reports blocked tasks."""
        # Create a dependency issue
        v2_1_dir = temp_cat_structure / ".claude" / "cat" / "issues" / "v2" / "v2.1"

        dep_issue_dir = v2_1_dir / "dependency-issue"
        dep_issue_dir.mkdir()
        dep_state = dep_issue_dir / "STATE.md"
        dep_state.write_text(
            "# State\n\n"
            "- **Status:** in-progress\n"
            "- **Progress:** 50%\n"
            "- **Dependencies:** []\n"
            "- **Last Updated:** 2026-02-06\n"
        )
        dep_plan = dep_issue_dir / "PLAN.md"
        dep_plan.write_text("# Plan\n\n## Goal\nDependency issue\n")

        # Update test-issue to depend on dependency-issue
        issue_dir = v2_1_dir / "test-issue"
        state_file = issue_dir / "STATE.md"
        state_file.write_text(
            "# State\n\n"
            "- **Status:** open\n"
            "- **Progress:** 0%\n"
            "- **Dependencies:** [dependency-issue]\n"
            "- **Last Updated:** 2026-02-06\n"
        )

        # Commit changes
        subprocess.run(
            ["git", "add", "."],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True
        )
        subprocess.run(
            ["git", "commit", "-m", "Add dependency"],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True
        )

        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--project-dir", str(temp_cat_structure),
                "--trust-level", "medium"
            ],
            capture_output=True,
            text=True,
        )

        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert output["status"] == "NO_TASKS"
        assert "blocked_tasks" in output
        assert len(output["blocked_tasks"]) > 0

        blocked = output["blocked_tasks"][0]
        assert blocked["issue_id"] == "test-issue"
        assert "dependency-issue" in blocked["blocked_by"]

    def test_cross_version_dependency_detection(self, script_path, temp_cat_structure):
        """Script detects dependencies across different versions."""
        issues_dir = temp_cat_structure / ".claude" / "cat" / "issues"

        # Create v2.0 directory with a dependency
        v2_0_dir = issues_dir / "v2" / "v2.0"
        v2_0_dir.mkdir(parents=True)

        dep_issue_dir = v2_0_dir / "v2-0-dependency"
        dep_issue_dir.mkdir()
        dep_state = dep_issue_dir / "STATE.md"
        dep_state.write_text(
            "# State\n\n"
            "- **Status:** open\n"
            "- **Progress:** 0%\n"
            "- **Dependencies:** []\n"
            "- **Last Updated:** 2026-02-06\n"
        )
        dep_plan = dep_issue_dir / "PLAN.md"
        dep_plan.write_text("# Plan\n\n## Goal\nV2.0 dependency\n")

        # Update test-issue to depend on v2.0 issue
        v2_1_dir = issues_dir / "v2" / "v2.1"
        issue_dir = v2_1_dir / "test-issue"
        state_file = issue_dir / "STATE.md"
        state_file.write_text(
            "# State\n\n"
            "- **Status:** open\n"
            "- **Progress:** 0%\n"
            "- **Dependencies:** [v2-0-dependency]\n"
            "- **Last Updated:** 2026-02-06\n"
        )

        # Commit changes
        subprocess.run(
            ["git", "add", "."],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True
        )
        subprocess.run(
            ["git", "commit", "-m", "Add cross-version dependency"],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True
        )

        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--project-dir", str(temp_cat_structure),
                "--trust-level", "medium"
            ],
            capture_output=True,
            text=True,
        )

        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert output["status"] == "NO_TASKS"
        assert "blocked_tasks" in output

        # Should find the cross-version dependency
        blocked = output["blocked_tasks"][0]
        assert "v2-0-dependency" in blocked["blocked_by"]

    def test_json_output_contract(self, script_path, temp_cat_structure):
        """Script returns JSON matching expected output contract."""
        # This test would require mocking get-available-issues.sh
        # For now, we test that the script at least produces valid JSON
        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--project-dir", str(temp_cat_structure),
                "--trust-level", "medium"
            ],
            capture_output=True,
            text=True,
        )

        # Should return valid JSON regardless of status
        assert result.returncode in [0, 1]
        output = json.loads(result.stdout)
        assert "status" in output
        assert output["status"] in ["READY", "NO_TASKS", "LOCKED", "BLOCKED", "ERROR", "OVERSIZED"]

    def test_exclude_pattern_argument(self, script_path, temp_cat_structure):
        """Script accepts exclude-pattern argument."""
        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--session-id", "12345678-1234-1234-1234-123456789abc",
                "--project-dir", str(temp_cat_structure),
                "--trust-level", "medium",
                "--exclude-pattern", "test*"
            ],
            capture_output=True,
            text=True,
        )

        # Should execute without argument errors
        assert result.returncode in [0, 1]
        output = json.loads(result.stdout)
        assert "status" in output
