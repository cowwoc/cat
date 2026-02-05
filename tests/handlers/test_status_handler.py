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

    def test_missing_file_returns_open(self, tmp_path):
        """Missing STATE.md returns open."""
        result = get_task_status(tmp_path / "nonexistent" / "STATE.md")
        assert result == "open"

    def test_status_colon_format(self, tmp_path):
        """Plain 'Status: value' format does not match bold regex, returns default."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("Status: closed\n")
        result = get_task_status(state_file)
        assert result == "open"

    def test_status_bold_format(self, tmp_path):
        """Parses '- **Status:** value' format."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** in-progress\n")
        result = get_task_status(state_file)
        assert result == "in-progress"

    def test_lowercase_status(self, tmp_path):
        """Plain lowercase 'status:' format does not match bold regex, returns default."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("status: open\n")
        result = get_task_status(state_file)
        assert result == "open"

    def test_status_with_extra_content(self, tmp_path):
        """Parses status with extra content in file using bold format."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("""# Task State

Some description here.

- **Status:** closed

More content below.
""")
        result = get_task_status(state_file)
        assert result == "closed"

    def test_no_status_returns_open(self, tmp_path):
        """File without status line returns open."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("# Just some content\nNo status here.\n")
        result = get_task_status(state_file)
        assert result == "open"

    def test_empty_file_returns_open(self, tmp_path):
        """Empty file returns open."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("")
        result = get_task_status(state_file)
        assert result == "open"


class TestStatusValidation:
    """Tests for status validation (M253: fail-fast on unknown status)."""

    def test_valid_statuses_constant_exists(self):
        """VALID_STATUSES constant is defined."""
        assert VALID_STATUSES is not None
        assert "open" in VALID_STATUSES
        assert "in-progress" in VALID_STATUSES
        assert "closed" in VALID_STATUSES
        assert "blocked" in VALID_STATUSES

    def test_status_aliases_constant_exists(self):
        """STATUS_ALIASES constant is defined."""
        assert STATUS_ALIASES is not None
        assert "complete" in STATUS_ALIASES
        assert STATUS_ALIASES["complete"] == "closed"

    def test_status_aliases_backward_compat(self):
        """STATUS_ALIASES includes backward compat aliases for renamed statuses."""
        assert "pending" in STATUS_ALIASES
        assert STATUS_ALIASES["pending"] == "open"
        assert "completed" in STATUS_ALIASES
        assert STATUS_ALIASES["completed"] == "closed"

    def test_valid_status_open(self, tmp_path):
        """Valid status 'open' is accepted."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** open\n")
        result = get_task_status(state_file)
        assert result == "open"

    def test_valid_status_closed(self, tmp_path):
        """Valid status 'closed' is accepted."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** closed\n")
        result = get_task_status(state_file)
        assert result == "closed"

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

    def test_alias_complete_normalized_to_closed(self, tmp_path, capsys):
        """Alias 'complete' is normalized to 'closed' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** complete\n")
        result = get_task_status(state_file)
        assert result == "closed"
        # Check warning was printed
        captured = capsys.readouterr()
        assert "Non-canonical status" in captured.err or "complete" in captured.err

    def test_alias_done_normalized_to_closed(self, tmp_path, capsys):
        """Alias 'done' is normalized to 'closed' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** done\n")
        result = get_task_status(state_file)
        assert result == "closed"

    def test_alias_pending_normalized_to_open(self, tmp_path, capsys):
        """Alias 'pending' is normalized to 'open' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** pending\n")
        result = get_task_status(state_file)
        assert result == "open"

    def test_alias_completed_normalized_to_closed(self, tmp_path, capsys):
        """Alias 'completed' is normalized to 'closed' with warning."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** completed\n")
        result = get_task_status(state_file)
        assert result == "closed"

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
        assert "open" in error_msg
        assert "closed" in error_msg

    def test_case_insensitive_status(self, tmp_path):
        """Status matching is case-insensitive."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** CLOSED\n")
        result = get_task_status(state_file)
        assert result == "closed"

    def test_status_with_whitespace(self, tmp_path):
        """Status with surrounding whitespace is handled."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:**   open  \n")
        result = get_task_status(state_file)
        assert result == "open"


class TestGetTaskDependencies:
    """Tests for get_task_dependencies function."""

    def test_missing_file_returns_empty(self, tmp_path):
        """Missing STATE.md returns empty list."""
        result = get_task_dependencies(tmp_path / "nonexistent" / "STATE.md")
        assert result == []

    def test_no_dependencies_section(self, tmp_path):
        """File without dependencies section returns empty list."""
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** open\n")
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

- **Status:** open

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
