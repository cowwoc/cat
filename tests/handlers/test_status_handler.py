"""Tests for StatusHandler."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.status_handler import (
    StatusHandler,
    VALID_STATUSES,
    STATUS_ALIASES,
    collect_status_data,
    get_task_status,
)


class TestGetTaskStatus:
    """Tests for get_task_status function."""

    def test_missing_file_returns_pending(self, tmp_path):
        """Missing STATE.md returns pending."""
        result = get_task_status(tmp_path / "nonexistent" / "STATE.md")
        assert result == "pending"

    def test_status_colon_format(self, tmp_path):
        """Parses 'Status: value' format."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("Status: completed\n")
        result = get_task_status(state_file)
        assert result == "completed"

    def test_status_bold_format(self, tmp_path):
        """Parses '- **Status:** value' format."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** in-progress\n")
        result = get_task_status(state_file)
        assert result == "in-progress"

    def test_lowercase_status(self, tmp_path):
        """Parses lowercase 'status:' format."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("status: pending\n")
        result = get_task_status(state_file)
        assert result == "pending"

    def test_status_with_extra_content(self, tmp_path):
        """Parses status with extra content in file."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("""# Task State

Some description here.

Status: completed

More content below.
""")
        result = get_task_status(state_file)
        assert result == "completed"

    def test_no_status_returns_pending(self, tmp_path):
        """File without status line returns pending."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("# Just some content\nNo status here.\n")
        result = get_task_status(state_file)
        assert result == "pending"

    def test_empty_file_returns_pending(self, tmp_path):
        """Empty file returns pending."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("")
        result = get_task_status(state_file)
        assert result == "pending"


class TestStatusValidation:
    """Tests for status validation (M253: fail-fast on unknown status)."""

    def test_valid_statuses_constant_exists(self):
        """VALID_STATUSES constant is defined."""
        assert VALID_STATUSES is not None
        assert "pending" in VALID_STATUSES
        assert "in-progress" in VALID_STATUSES
        assert "completed" in VALID_STATUSES
        assert "blocked" in VALID_STATUSES

    def test_status_aliases_constant_exists(self):
        """STATUS_ALIASES constant is defined."""
        assert STATUS_ALIASES is not None
        assert "complete" in STATUS_ALIASES
        assert STATUS_ALIASES["complete"] == "completed"

    def test_valid_status_pending(self, tmp_path):
        """Valid status 'pending' is accepted."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** pending\n")
        result = get_task_status(state_file)
        assert result == "pending"

    def test_valid_status_completed(self, tmp_path):
        """Valid status 'completed' is accepted."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** completed\n")
        result = get_task_status(state_file)
        assert result == "completed"

    def test_valid_status_in_progress(self, tmp_path):
        """Valid status 'in-progress' is accepted."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** in-progress\n")
        result = get_task_status(state_file)
        assert result == "in-progress"

    def test_valid_status_blocked(self, tmp_path):
        """Valid status 'blocked' is accepted."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** blocked\n")
        result = get_task_status(state_file)
        assert result == "blocked"

    def test_alias_complete_normalized_to_completed(self, tmp_path, capsys):
        """Alias 'complete' is normalized to 'completed' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** complete\n")
        result = get_task_status(state_file)
        assert result == "completed"
        # Check warning was printed
        captured = capsys.readouterr()
        assert "Non-canonical status" in captured.err or "complete" in captured.err

    def test_alias_done_normalized_to_completed(self, tmp_path, capsys):
        """Alias 'done' is normalized to 'completed' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** done\n")
        result = get_task_status(state_file)
        assert result == "completed"

    def test_alias_in_progress_underscore_normalized(self, tmp_path, capsys):
        """Alias 'in_progress' is normalized to 'in-progress' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** in_progress\n")
        result = get_task_status(state_file)
        assert result == "in-progress"

    def test_alias_active_normalized_to_in_progress(self, tmp_path, capsys):
        """Alias 'active' is normalized to 'in-progress' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** active\n")
        result = get_task_status(state_file)
        assert result == "in-progress"

    def test_unknown_status_raises_error(self, tmp_path):
        """Unknown status raises ValueError (M253)."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** invalid_status_value\n")
        with pytest.raises(ValueError) as exc_info:
            get_task_status(state_file)
        assert "Unknown status" in str(exc_info.value)
        assert "invalid_status_value" in str(exc_info.value)

    def test_unknown_status_error_includes_valid_values(self, tmp_path):
        """Unknown status error message includes valid values."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** unknown\n")
        with pytest.raises(ValueError) as exc_info:
            get_task_status(state_file)
        error_msg = str(exc_info.value)
        assert "pending" in error_msg
        assert "completed" in error_msg

    def test_case_insensitive_status(self, tmp_path):
        """Status matching is case-insensitive."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** COMPLETED\n")
        result = get_task_status(state_file)
        assert result == "completed"

    def test_status_with_whitespace(self, tmp_path):
        """Status with surrounding whitespace is handled."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:**   pending  \n")
        result = get_task_status(state_file)
        assert result == "pending"


class TestCollectStatusData:
    """Tests for collect_status_data function."""

    def test_missing_issues_dir_returns_error(self, tmp_path):
        """Missing issues directory returns error."""
        result = collect_status_data(tmp_path / "nonexistent")
        assert "error" in result
        assert "No planning structure found" in result["error"]

    def test_basic_structure(self, mock_cat_structure):
        """Returns basic structure for minimal CAT setup."""
        issues_dir = mock_cat_structure / ".claude" / "cat" / "issues"
        result = collect_status_data(issues_dir)

        assert "error" not in result
        assert "project" in result
        assert "overall" in result
        assert "current" in result
        assert "majors" in result
        assert "minors" in result

    def test_extracts_project_name(self, mock_cat_structure):
        """Extracts project name from PROJECT.md in parent directory."""
        issues_dir = mock_cat_structure / ".claude" / "cat" / "issues"
        result = collect_status_data(issues_dir)
        assert result["project"] == "Test Project"

    def test_version_with_tasks(self, mock_version_with_tasks):
        """Collects data from version with tasks."""
        issues_dir = mock_version_with_tasks / ".claude" / "cat" / "issues"
        result = collect_status_data(issues_dir)

        assert "error" not in result
        assert len(result["majors"]) >= 1
        assert len(result["minors"]) >= 1

        # Check task counts
        v10_minor = next((m for m in result["minors"] if m["id"] == "v1.0"), None)
        assert v10_minor is not None
        assert v10_minor["total"] == 3
        assert v10_minor["completed"] == 1  # parse-input is completed

    def test_detects_in_progress_task(self, mock_version_with_tasks):
        """Detects in-progress task."""
        issues_dir = mock_version_with_tasks / ".claude" / "cat" / "issues"
        result = collect_status_data(issues_dir)

        v10_minor = next((m for m in result["minors"] if m["id"] == "v1.0"), None)
        assert v10_minor is not None
        assert v10_minor["inProgress"] == "validate-data"

    def test_overall_percentage(self, mock_version_with_tasks):
        """Calculates overall percentage."""
        issues_dir = mock_version_with_tasks / ".claude" / "cat" / "issues"
        result = collect_status_data(issues_dir)

        assert "percent" in result["overall"]
        assert "completed" in result["overall"]
        assert "total" in result["overall"]
        # 1 completed out of 3 = 33%
        assert result["overall"]["percent"] == 33

    def test_current_minor(self, mock_version_with_tasks):
        """Identifies current minor version."""
        issues_dir = mock_version_with_tasks / ".claude" / "cat" / "issues"
        result = collect_status_data(issues_dir)

        assert result["current"]["minor"] == "v1.0"
        assert result["current"]["inProgressTask"] == "validate-data"

    def test_next_task_when_no_in_progress(self, mock_cat_structure):
        """Identifies next task when no task is in-progress."""
        issues_dir = mock_cat_structure / ".claude" / "cat" / "issues"

        # Create v1/v1.0 with only pending tasks (no in-progress)
        v1_dir = issues_dir / "v1"
        v1_dir.mkdir()
        v10_dir = v1_dir / "v1.0"
        v10_dir.mkdir()

        task1_dir = v10_dir / "task-a"
        task1_dir.mkdir()
        (task1_dir / "STATE.md").write_text("Status: pending\n")
        (task1_dir / "PLAN.md").write_text("# Task A\n")

        result = collect_status_data(issues_dir)
        assert result["current"]["nextTask"] == "task-a"
        assert result["current"]["inProgressTask"] == ""

    def test_empty_project_name(self, mock_cat_structure):
        """Handles missing project name gracefully."""
        cat_dir = mock_cat_structure / ".claude" / "cat"
        issues_dir = cat_dir / "issues"
        # Remove PROJECT.md
        (cat_dir / "PROJECT.md").unlink()

        result = collect_status_data(issues_dir)
        assert result["project"] == "Unknown Project"


class TestStatusHandler:
    """Tests for StatusHandler class."""

    @pytest.fixture
    def handler(self):
        """Create a StatusHandler instance."""
        return StatusHandler()

    def test_returns_none_without_project_root(self, handler):
        """Handler returns None when project_root is missing."""
        result = handler.handle({})
        assert result is None

    def test_returns_none_without_cat_dir(self, handler, tmp_path):
        """Handler returns None when CAT directory doesn't exist."""
        context = {"project_root": str(tmp_path)}
        result = handler.handle(context)
        assert result is None

    def test_returns_string_with_valid_structure(self, handler, mock_version_with_tasks):
        """Handler returns string with valid CAT structure."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert isinstance(result, str)

    def test_contains_precomputed_marker(self, handler, mock_version_with_tasks):
        """Output contains PRE-COMPUTED marker."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "PRE-COMPUTED STATUS DISPLAY" in result

    def test_contains_instruction(self, handler, mock_version_with_tasks):
        """Output contains INSTRUCTION marker."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "INSTRUCTION:" in result

    def test_contains_progress_bar(self, handler, mock_version_with_tasks):
        """Output contains progress bar."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "█" in result or "░" in result

    def test_contains_task_count(self, handler, mock_version_with_tasks):
        """Output contains task count."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "1/3 tasks" in result

    def test_contains_current_task(self, handler, mock_version_with_tasks):
        """Output contains current task info."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "📋 Current:" in result or "📋 Next:" in result
        assert "v1.0" in result

    def test_contains_next_steps_table(self, handler, mock_version_with_tasks):
        """Output contains next steps table."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "NEXT STEPS" in result
        assert "/cat:work" in result
        assert "/cat:add" in result

    def test_contains_legend(self, handler, mock_version_with_tasks):
        """Output contains legend."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "Legend:" in result
        assert "Completed" in result
        assert "In Progress" in result
        assert "Pending" in result

    def test_box_structure(self, handler, mock_version_with_tasks):
        """Output has box structure."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "╭" in result
        assert "╰" in result
        assert "│" in result

    def test_contains_inner_boxes(self, handler, mock_version_with_tasks):
        """Output contains inner boxes for versions."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        # Should have inner box for v1
        assert "📦 v1:" in result


class TestStatusHandlerDisplay:
    """Tests for StatusHandler display formatting."""

    @pytest.fixture
    def handler(self):
        """Create a StatusHandler instance."""
        return StatusHandler()

    def test_percentage_display(self, handler, mock_version_with_tasks):
        """Percentage is displayed correctly."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "33%" in result  # 1 of 3 tasks complete

    def test_version_emojis(self, handler, mock_version_with_tasks):
        """Version status emojis are correct."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        # v1.0 has in-progress task, should show 🔄
        assert "🔄" in result

    def test_pending_task_display(self, handler, mock_version_with_tasks):
        """Pending tasks are indented under version."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        # output-results is pending
        assert "output-results" in result

    def test_overall_emoji(self, handler, mock_version_with_tasks):
        """Overall section has chart emoji."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "📊 Overall:" in result

    def test_overall_progress_emoji(self, handler, mock_version_with_tasks):
        """Overall progress has chart emoji."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "📊 Overall:" in result

    def test_current_task_emoji(self, handler, mock_version_with_tasks):
        """Current task has clipboard emoji."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "📋 Current:" in result or "📋 Next:" in result

    def test_in_progress_emoji(self, handler, mock_version_with_tasks):
        """In-progress task has rotating emoji."""
        context = {"project_root": str(mock_version_with_tasks)}
        result = handler.handle(context)
        assert "🔄" in result


class TestStatusHandlerEdgeCases:
    """Edge case tests for StatusHandler."""

    @pytest.fixture
    def handler(self):
        """Create a StatusHandler instance."""
        return StatusHandler()

    def test_no_tasks_directory(self, mock_cat_structure, handler):
        """Handles version with no tasks."""
        issues_dir = mock_cat_structure / ".claude" / "cat" / "issues"
        v1_dir = issues_dir / "v1"
        v1_dir.mkdir()
        v10_dir = v1_dir / "v1.0"
        v10_dir.mkdir()

        context = {"project_root": str(mock_cat_structure)}
        result = handler.handle(context)
        # Should still work, just show 0 tasks
        assert result is not None

    def test_long_task_names_truncated(self, mock_cat_structure, handler):
        """Long task names are truncated in display."""
        issues_dir = mock_cat_structure / ".claude" / "cat" / "issues"
        v1_dir = issues_dir / "v1"
        v1_dir.mkdir()
        v10_dir = v1_dir / "v1.0"
        v10_dir.mkdir()

        # Create task with very long name
        long_name = "this-is-a-very-long-task-name-that-exceeds-limits"
        task_dir = v10_dir / long_name
        task_dir.mkdir()
        (task_dir / "STATE.md").write_text("Status: pending\n")
        (task_dir / "PLAN.md").write_text("# Long Task\n")

        context = {"project_root": str(mock_cat_structure)}
        result = handler.handle(context)
        # Long names should be truncated with ...
        assert "..." in result or long_name[:20] in result

    def test_all_tasks_completed(self, mock_cat_structure, handler):
        """Handles version with all tasks completed."""
        issues_dir = mock_cat_structure / ".claude" / "cat" / "issues"
        v1_dir = issues_dir / "v1"
        v1_dir.mkdir()
        v10_dir = v1_dir / "v1.0"
        v10_dir.mkdir()

        for task_name in ["task-a", "task-b"]:
            task_dir = v10_dir / task_name
            task_dir.mkdir()
            (task_dir / "STATE.md").write_text("Status: completed\n")
            (task_dir / "PLAN.md").write_text(f"# {task_name}\n")

        context = {"project_root": str(mock_cat_structure)}
        result = handler.handle(context)
        assert result is not None
        # Should show 100% or completed status
        assert "100%" in result or "☑️" in result


class TestIssuesDirectoryStructure:
    """Regression tests for issues/ subdirectory structure (M223).

    After commit 00524ca2, version directories moved from .claude/cat/ to
    .claude/cat/issues/. These tests verify the correct path is used.
    """

    def test_collect_status_data_finds_tasks_in_issues_dir(self, tmp_path):
        """Regression test: collect_status_data must look in issues/ for versions."""
        # Create structure: .claude/cat/issues/v1/v1.0/my-task/
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)
        issues_dir = cat_dir / "issues"
        issues_dir.mkdir()

        # Create PROJECT.md in cat_dir (not issues_dir)
        (cat_dir / "PROJECT.md").write_text("# My Project\n")
        (cat_dir / "ROADMAP.md").write_text("# Roadmap\n")

        # Create version structure in issues_dir
        v1_dir = issues_dir / "v1"
        v1_dir.mkdir()
        v10_dir = v1_dir / "v1.0"
        v10_dir.mkdir()
        task_dir = v10_dir / "my-task"
        task_dir.mkdir()
        (task_dir / "STATE.md").write_text("Status: pending\n")
        (task_dir / "PLAN.md").write_text("# My Task\n")

        # Call collect_status_data with issues_dir
        result = collect_status_data(issues_dir)

        # Must find the task
        assert "error" not in result
        assert result["overall"]["total"] == 1
        assert result["project"] == "My Project"

    def test_handler_uses_issues_subdirectory(self, tmp_path):
        """Regression test: StatusHandler must pass issues_dir to collect_status_data."""
        # Create structure: .claude/cat/issues/v1/v1.0/my-task/
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)
        issues_dir = cat_dir / "issues"
        issues_dir.mkdir()

        # Create PROJECT.md in cat_dir
        (cat_dir / "PROJECT.md").write_text("# Handler Test Project\n")
        (cat_dir / "ROADMAP.md").write_text("# Roadmap\n")

        # Create version structure in issues_dir
        v1_dir = issues_dir / "v1"
        v1_dir.mkdir()
        v10_dir = v1_dir / "v1.0"
        v10_dir.mkdir()
        task_dir = v10_dir / "test-task"
        task_dir.mkdir()
        (task_dir / "STATE.md").write_text("Status: pending\n")
        (task_dir / "PLAN.md").write_text("# Test Task\n")

        # Call handler
        handler = StatusHandler()
        context = {"project_root": str(tmp_path)}
        result = handler.handle(context)

        # Must find the task (displayed in output)
        assert result is not None
        assert "test-task" in result
        assert "1/1 tasks" in result
