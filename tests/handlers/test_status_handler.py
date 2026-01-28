"""Tests for status_handler display utilities.

Note: StatusHandler class was removed - status display now uses silent preprocessing
via get-status-display.sh script. This file tests the shared utility functions that
remain in status_handler.py.
"""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.status_handler import (
    VALID_STATUSES,
    STATUS_ALIASES,
    get_task_status,
    get_task_dependencies,
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


class TestGetTaskDependencies:
    """Tests for get_task_dependencies function."""

    def test_missing_file_returns_empty(self, tmp_path):
        """Missing STATE.md returns empty list."""
        result = get_task_dependencies(tmp_path / "nonexistent" / "STATE.md")
        assert result == []

    def test_no_dependencies_section(self, tmp_path):
        """File without dependencies section returns empty list."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** pending\n")
        result = get_task_dependencies(state_file)
        assert result == []

    def test_inline_format(self, tmp_path):
        """Parses '- **Dependencies:** [a, b]' format."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Dependencies:** [task-a, task-b]\n")
        result = get_task_dependencies(state_file)
        assert result == ["task-a", "task-b"]

    def test_section_format(self, tmp_path):
        """Parses '## Dependencies' section format."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("""# State

- **Status:** pending

## Dependencies
- task-a
- task-b (required for X)
""")
        result = get_task_dependencies(state_file)
        assert result == ["task-a", "task-b"]

    def test_empty_dependencies(self, tmp_path):
        """Empty dependencies list returns empty list."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Dependencies:** []\n")
        result = get_task_dependencies(state_file)
        assert result == []

    def test_filters_none_dependency(self, tmp_path):
        """Filters out 'None' as a dependency."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("""## Dependencies
- None
""")
        result = get_task_dependencies(state_file)
        assert result == []
