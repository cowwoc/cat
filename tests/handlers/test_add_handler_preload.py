"""Tests for AddHandler preload functionality."""

import json
import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.add_handler import AddHandler


class TestAddHandlerPreload:
    """Tests for AddHandler version data preloading."""

    @pytest.fixture
    def handler(self):
        """Create an AddHandler instance."""
        return AddHandler()

    @pytest.fixture
    def temp_cat_structure(self, tmp_path):
        """Create a temporary CAT structure for testing."""
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)

        # Create ROADMAP.md
        roadmap = cat_dir / "ROADMAP.md"
        roadmap.write_text("# Roadmap\n\n## Version 2\n")

        # Create issues directory
        issues_dir = cat_dir / "issues"
        issues_dir.mkdir()

        # Create v2 major version
        v2_dir = issues_dir / "v2"
        v2_dir.mkdir()

        # Create v2.1 minor version (open)
        v2_1_dir = v2_dir / "v2.1"
        v2_1_dir.mkdir()

        state_21 = v2_1_dir / "STATE.md"
        state_21.write_text("**Status:** in-progress\n")

        plan_21 = v2_1_dir / "PLAN.md"
        plan_21.write_text("## Goals\nPre-Demo Polish\n")

        # Create some issues in v2.1
        (v2_1_dir / "issue-1").mkdir()
        (v2_1_dir / "issue-2").mkdir()

        # Create v2.2 minor version (closed)
        v2_2_dir = v2_dir / "v2.2"
        v2_2_dir.mkdir()

        state_22 = v2_2_dir / "STATE.md"
        state_22.write_text("**Status:** closed\n")

        plan_22 = v2_2_dir / "PLAN.md"
        plan_22.write_text("## Goals\nCompleted Features\n")

        # Create cat-config.json
        config = cat_dir / "cat-config.json"
        config.write_text('{"gitWorkflow": {"branchingStrategy": "feature"}}')

        return tmp_path

    def test_preload_returns_handler_data_format(self, handler, temp_cat_structure):
        """Preload returns HANDLER_DATA JSON string."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        assert result is not None
        assert result.startswith("HANDLER_DATA: ")

    def test_preload_returns_valid_json(self, handler, temp_cat_structure):
        """Preload returns valid JSON."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)
        assert isinstance(data, dict)

    def test_preload_has_required_fields(self, handler, temp_cat_structure):
        """Preload result has all required fields."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        assert "planning_valid" in data
        assert "versions" in data
        assert "branch_strategy" in data
        assert "branch_pattern" in data

    def test_preload_planning_valid_true(self, handler, temp_cat_structure):
        """Preload returns planning_valid=true when structure exists."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        assert data["planning_valid"] is True

    def test_preload_excludes_closed_versions(self, handler, temp_cat_structure):
        """Preload excludes versions with status=closed."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        versions = data["versions"]
        version_numbers = [v["version"] for v in versions]

        assert "2.1" in version_numbers
        assert "2.2" not in version_numbers

    def test_preload_includes_version_details(self, handler, temp_cat_structure):
        """Preload includes version status, summary, issue_count."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        v21 = next(v for v in data["versions"] if v["version"] == "2.1")
        assert v21["status"] == "in-progress"
        assert "Pre-Demo Polish" in v21["summary"]
        assert v21["issue_count"] == 2

    def test_preload_includes_existing_issues(self, handler, temp_cat_structure):
        """Preload includes list of existing issue names."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        v21 = next(v for v in data["versions"] if v["version"] == "2.1")
        assert "existing_issues" in v21
        assert "issue-1" in v21["existing_issues"]
        assert "issue-2" in v21["existing_issues"]

    def test_preload_reads_branch_strategy(self, handler, temp_cat_structure):
        """Preload reads branch strategy from cat-config.json."""
        result = handler.handle({"project_root": str(temp_cat_structure)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        assert data["branch_strategy"] == "feature"

    def test_preload_error_when_no_cat_dir(self, handler, tmp_path):
        """Preload returns error when .claude/cat doesn't exist."""
        result = handler.handle({"project_root": str(tmp_path)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        assert data["planning_valid"] is False
        assert "error" in data
        assert "planning structure" in data["error"].lower()

    def test_preload_error_when_no_roadmap(self, handler, tmp_path):
        """Preload returns error when ROADMAP.md doesn't exist."""
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)

        result = handler.handle({"project_root": str(tmp_path)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        assert data["planning_valid"] is False
        assert "error" in data
        assert "ROADMAP.md" in data["error"]

    def test_preload_empty_versions_when_no_issues_dir(self, handler, tmp_path):
        """Preload returns empty versions list when issues dir doesn't exist."""
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)
        (cat_dir / "ROADMAP.md").write_text("# Roadmap\n")

        result = handler.handle({"project_root": str(tmp_path)})
        json_str = result.replace("HANDLER_DATA: ", "")
        data = json.loads(json_str)

        assert data["planning_valid"] is True
        assert data["versions"] == []
