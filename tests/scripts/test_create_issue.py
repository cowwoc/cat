"""Tests for create-issue.py script."""

import json
import subprocess
import sys
from pathlib import Path

import pytest


class TestCreateIssueScript:
    """Tests for create-issue.py script functionality."""

    @pytest.fixture
    def script_path(self):
        """Get path to create-issue.py script."""
        return Path(__file__).parent.parent.parent / "plugin" / "scripts" / "create-issue.py"

    @pytest.fixture
    def temp_cat_structure(self, tmp_path):
        """Create a temporary CAT structure with git repo."""
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)

        # Create issues directory
        issues_dir = cat_dir / "issues"
        issues_dir.mkdir()

        # Create v2/v2.1 structure
        v2_dir = issues_dir / "v2"
        v2_dir.mkdir()

        v2_1_dir = v2_dir / "v2.1"
        v2_1_dir.mkdir()

        # Create STATE.md
        state_file = v2_1_dir / "STATE.md"
        state_file.write_text(
            "# Minor Version 2.1 State\n\n"
            "## Status\n"
            "- **Status:** open\n"
            "- **Progress:** 0%\n"
        )

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
        subprocess.run(
            ["git", "add", "."],
            cwd=tmp_path,
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["git", "commit", "-m", "Initial commit"],
            cwd=tmp_path,
            check=True,
            capture_output=True,
        )

        return tmp_path

    def test_creates_issue_directory(self, script_path, temp_cat_structure):
        """Script creates issue directory."""
        data = {
            "major": "2",
            "minor": "1",
            "issue_name": "test-issue",
            "state_content": "# State\n\n- **Status:** open\n",
            "plan_content": "# Plan\n\n## Goal\nTest goal\n",
            "commit_description": "Test issue",
        }

        result = subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            capture_output=True,
            text=True,
        )

        assert result.returncode == 0
        issue_dir = temp_cat_structure / ".claude" / "cat" / "issues" / "v2" / "v2.1" / "test-issue"
        assert issue_dir.exists()

    def test_writes_state_file(self, script_path, temp_cat_structure):
        """Script writes STATE.md file."""
        state_content = "# State\n\n- **Status:** open\n- **Progress:** 0%\n"
        data = {
            "major": "2",
            "minor": "1",
            "issue_name": "test-issue",
            "state_content": state_content,
            "plan_content": "# Plan\n\n## Goal\nTest goal\n",
            "commit_description": "Test issue",
        }

        subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True,
        )

        state_file = (
            temp_cat_structure / ".claude" / "cat" / "issues" / "v2" / "v2.1" / "test-issue" / "STATE.md"
        )
        assert state_file.exists()
        assert state_file.read_text() == state_content

    def test_writes_plan_file(self, script_path, temp_cat_structure):
        """Script writes PLAN.md file."""
        plan_content = "# Plan\n\n## Goal\nTest goal\n## Execution\nStep 1\n"
        data = {
            "major": "2",
            "minor": "1",
            "issue_name": "test-issue",
            "state_content": "# State\n\n- **Status:** open\n",
            "plan_content": plan_content,
            "commit_description": "Test issue",
        }

        subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True,
        )

        plan_file = (
            temp_cat_structure / ".claude" / "cat" / "issues" / "v2" / "v2.1" / "test-issue" / "PLAN.md"
        )
        assert plan_file.exists()
        assert plan_file.read_text() == plan_content

    def test_updates_parent_state(self, script_path, temp_cat_structure):
        """Script updates parent version STATE.md."""
        data = {
            "major": "2",
            "minor": "1",
            "issue_name": "new-feature",
            "state_content": "# State\n\n- **Status:** open\n",
            "plan_content": "# Plan\n\n## Goal\nTest\n",
            "commit_description": "Test issue",
        }

        subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True,
        )

        parent_state = temp_cat_structure / ".claude" / "cat" / "issues" / "v2" / "v2.1" / "STATE.md"
        content = parent_state.read_text()
        assert "## Issues Pending" in content
        assert "- new-feature" in content

    def test_creates_git_commit(self, script_path, temp_cat_structure):
        """Script creates git commit."""
        data = {
            "major": "2",
            "minor": "1",
            "issue_name": "test-issue",
            "state_content": "# State\n\n- **Status:** open\n",
            "plan_content": "# Plan\n\n## Goal\nTest\n",
            "commit_description": "Add test feature",
        }

        subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True,
        )

        # Check git log
        result = subprocess.run(
            ["git", "log", "--oneline", "-1"],
            cwd=temp_cat_structure,
            capture_output=True,
            text=True,
        )
        assert "planning: add issue test-issue to 2.1" in result.stdout

    def test_commit_message_format(self, script_path, temp_cat_structure):
        """Script creates commit with correct message format."""
        data = {
            "major": "2",
            "minor": "1",
            "issue_name": "test-issue",
            "state_content": "# State\n\n- **Status:** open\n",
            "plan_content": "# Plan\n\n## Goal\nTest\n",
            "commit_description": "Add authentication feature",
        }

        subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            check=True,
            capture_output=True,
        )

        # Check full commit message
        result = subprocess.run(
            ["git", "log", "-1", "--pretty=%B"],
            cwd=temp_cat_structure,
            capture_output=True,
            text=True,
        )
        message = result.stdout.strip()
        assert message.startswith("planning: add issue test-issue to 2.1")
        assert "Add authentication feature" in message

    def test_returns_success_json(self, script_path, temp_cat_structure):
        """Script returns success JSON on completion."""
        data = {
            "major": "2",
            "minor": "1",
            "issue_name": "test-issue",
            "state_content": "# State\n\n- **Status:** open\n",
            "plan_content": "# Plan\n\n## Goal\nTest\n",
            "commit_description": "Test",
        }

        result = subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            capture_output=True,
            text=True,
        )

        output = json.loads(result.stdout)
        assert output["success"] is True
        assert "path" in output

    def test_error_when_missing_field(self, script_path, temp_cat_structure):
        """Script returns error when required field is missing."""
        data = {
            "major": "2",
            "minor": "1",
            # Missing issue_name
            "state_content": "# State\n",
            "plan_content": "# Plan\n",
        }

        result = subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            capture_output=True,
            text=True,
        )

        assert result.returncode == 1
        output = json.loads(result.stdout)
        assert output["success"] is False
        assert "error" in output

    def test_error_when_parent_not_exists(self, script_path, temp_cat_structure):
        """Script returns error when parent version doesn't exist."""
        data = {
            "major": "99",
            "minor": "99",
            "issue_name": "test-issue",
            "state_content": "# State\n",
            "plan_content": "# Plan\n",
            "commit_description": "Test",
        }

        result = subprocess.run(
            [sys.executable, str(script_path), "--json", json.dumps(data)],
            cwd=temp_cat_structure,
            capture_output=True,
            text=True,
        )

        assert result.returncode == 1
        output = json.loads(result.stdout)
        assert output["success"] is False
        assert "does not exist" in output["error"]
