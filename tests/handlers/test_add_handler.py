"""Tests for AddHandler."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.add_handler import AddHandler


class TestAddHandler:
    """Tests for AddHandler class."""

    @pytest.fixture
    def handler(self):
        """Create an AddHandler instance."""
        return AddHandler()

    def test_returns_handler_data_without_item_type(self, handler):
        """Handler returns HANDLER_DATA when item_type is missing (preload mode)."""
        result = handler.handle({})
        assert result is not None
        assert result.startswith("HANDLER_DATA: ")

    def test_returns_none_for_invalid_item_type(self, handler):
        """Handler returns None for unknown item_type."""
        result = handler.handle({"item_type": "invalid"})
        assert result is None


class TestAddHandlerTaskDisplay:
    """Tests for AddHandler task display."""

    @pytest.fixture
    def handler(self):
        """Create an AddHandler instance."""
        return AddHandler()

    @pytest.fixture
    def task_context(self):
        """Create a task context."""
        return {
            "item_type": "task",
            "item_name": "parse-tokens",
            "version": "2.0",
            "task_type": "Feature",
            "dependencies": [],
        }

    def test_task_returns_string(self, handler, task_context):
        """Task display returns a string."""
        result = handler.handle(task_context)
        assert isinstance(result, str)

    def test_task_contains_script_output_marker(self, handler, task_context):
        """Task output contains SCRIPT OUTPUT marker."""
        result = handler.handle(task_context)
        assert "SCRIPT OUTPUT ADD DISPLAY" in result

    def test_task_contains_instruction(self, handler, task_context):
        """Task output contains INSTRUCTION marker."""
        result = handler.handle(task_context)
        assert "INSTRUCTION:" in result

    def test_task_contains_task_name(self, handler, task_context):
        """Task output contains task name."""
        result = handler.handle(task_context)
        assert "parse-tokens" in result

    def test_task_contains_version(self, handler, task_context):
        """Task output contains version."""
        result = handler.handle(task_context)
        assert "Version: 2.0" in result

    def test_task_contains_type(self, handler, task_context):
        """Task output contains task type."""
        result = handler.handle(task_context)
        assert "Type: Feature" in result

    def test_task_contains_dependencies(self, handler, task_context):
        """Task output shows dependencies."""
        result = handler.handle(task_context)
        assert "Dependencies: None" in result

    def test_task_with_dependencies(self, handler, task_context):
        """Task output shows actual dependencies."""
        task_context["dependencies"] = ["setup-env", "create-config"]
        result = handler.handle(task_context)
        assert "setup-env" in result
        assert "create-config" in result

    def test_task_contains_next_command(self, handler, task_context):
        """Task output contains next command suggestion."""
        result = handler.handle(task_context)
        assert "/cat:work 2.0-parse-tokens" in result

    def test_task_contains_checkmark_emoji(self, handler, task_context):
        """Task output contains checkmark emoji in header."""
        result = handler.handle(task_context)
        assert "✅ Task Created" in result

    def test_task_box_structure(self, handler, task_context):
        """Task output has box structure."""
        result = handler.handle(task_context)
        assert "╭─" in result  # Top left corner with header
        assert "╰" in result  # Bottom left corner
        assert "╮" in result  # Top right corner
        assert "╯" in result  # Bottom right corner
        assert "│" in result  # Vertical lines

    def test_task_uses_defaults(self, handler):
        """Task uses default values when not provided."""
        minimal_context = {"item_type": "task"}
        result = handler.handle(minimal_context)
        assert result is not None
        assert "unknown-task" in result
        assert "Version: 0.0" in result
        assert "Type: Feature" in result


class TestAddHandlerVersionDisplay:
    """Tests for AddHandler version display."""

    @pytest.fixture
    def handler(self):
        """Create an AddHandler instance."""
        return AddHandler()

    @pytest.fixture
    def version_context(self):
        """Create a version context."""
        return {
            "item_type": "version",
            "item_name": "New Features",
            "version": "2.1",
            "version_type": "minor",
            "parent_info": "v2",
            "path": ".claude/cat/issues/v2/v2.1",
        }

    def test_version_returns_string(self, handler, version_context):
        """Version display returns a string."""
        result = handler.handle(version_context)
        assert isinstance(result, str)

    def test_version_contains_script_output_marker(self, handler, version_context):
        """Version output contains SCRIPT OUTPUT marker."""
        result = handler.handle(version_context)
        assert "SCRIPT OUTPUT ADD DISPLAY" in result

    def test_version_contains_instruction(self, handler, version_context):
        """Version output contains INSTRUCTION marker."""
        result = handler.handle(version_context)
        assert "INSTRUCTION:" in result

    def test_version_contains_version_name(self, handler, version_context):
        """Version output contains version name."""
        result = handler.handle(version_context)
        assert "v2.1: New Features" in result

    def test_version_contains_parent_info(self, handler, version_context):
        """Version output contains parent info."""
        result = handler.handle(version_context)
        assert "Parent: v2" in result

    def test_version_contains_path(self, handler, version_context):
        """Version output contains path."""
        result = handler.handle(version_context)
        assert "Path: .claude/cat/issues/v2/v2.1" in result

    def test_version_contains_next_command(self, handler, version_context):
        """Version output contains next command suggestion."""
        result = handler.handle(version_context)
        assert "/cat:add" in result

    def test_version_contains_checkmark_emoji(self, handler, version_context):
        """Version output contains checkmark emoji in header."""
        result = handler.handle(version_context)
        assert "✅ Version Created" in result

    def test_version_box_structure(self, handler, version_context):
        """Version output has box structure."""
        result = handler.handle(version_context)
        assert "╭─" in result
        assert "╰" in result
        assert "╮" in result
        assert "╯" in result
        assert "│" in result

    def test_version_without_parent(self, handler):
        """Version without parent info omits that line."""
        context = {
            "item_type": "version",
            "item_name": "Major Release",
            "version": "3.0",
        }
        result = handler.handle(context)
        assert result is not None
        assert "Parent:" not in result

    def test_version_without_path(self, handler):
        """Version without path omits that line."""
        context = {
            "item_type": "version",
            "item_name": "Major Release",
            "version": "3.0",
            "parent_info": "root",
        }
        result = handler.handle(context)
        assert result is not None
        assert "Parent: root" in result
        assert "Path:" not in result

    def test_version_uses_defaults(self, handler):
        """Version uses default values when not provided."""
        minimal_context = {"item_type": "version"}
        result = handler.handle(minimal_context)
        assert result is not None
        assert "v0.0: New Version" in result
