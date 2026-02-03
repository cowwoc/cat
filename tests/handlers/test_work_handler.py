"""Tests for WorkHandler."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.work_handler import (
    WorkHandler,
    build_separator,
    build_header_top,
    build_simple_box,
)


class TestBuildSeparator:
    """Tests for build_separator function."""

    def test_returns_string(self):
        """Function returns a string."""
        result = build_separator(10)
        assert isinstance(result, str)

    def test_starts_with_left_connector(self):
        """Separator starts with left T-connector."""
        result = build_separator(10)
        assert result.startswith("├")

    def test_ends_with_right_connector(self):
        """Separator ends with right T-connector."""
        result = build_separator(10)
        assert result.endswith("┤")

    def test_contains_dashes(self):
        """Separator contains horizontal dashes."""
        result = build_separator(10)
        assert "─" in result

    def test_dash_count(self):
        """Dash count is max_width + 2."""
        result = build_separator(10)
        dash_count = result.count("─")
        assert dash_count == 12  # 10 + 2

    def test_various_widths(self):
        """Works with various widths."""
        for width in [5, 20, 50]:
            result = build_separator(width)
            assert result.startswith("├")
            assert result.endswith("┤")
            assert result.count("─") == width + 2


class TestBuildHeaderTop:
    """Tests for build_header_top function."""

    def test_returns_string(self):
        """Function returns a string."""
        result = build_header_top("Header", 20)
        assert isinstance(result, str)

    def test_starts_with_corner(self):
        """Header top starts with top-left corner."""
        result = build_header_top("Test", 20)
        assert result.startswith("╭")

    def test_ends_with_corner(self):
        """Header top ends with top-right corner."""
        result = build_header_top("Test", 20)
        assert result.endswith("╮")

    def test_contains_header_text(self):
        """Header text is included."""
        result = build_header_top("My Header", 30)
        assert "My Header" in result

    def test_has_prefix_dashes(self):
        """Header has prefix dashes."""
        result = build_header_top("Test", 20)
        assert "─── " in result

    def test_has_suffix_dashes(self):
        """Header has suffix dashes after text."""
        result = build_header_top("Test", 20)
        # After "Test " there should be dashes before ╮
        parts = result.split("Test ")
        assert len(parts) == 2
        assert parts[1].endswith("╮")
        assert "─" in parts[1]


class TestWorkHandler:
    """Tests for WorkHandler class."""

    @pytest.fixture
    def handler(self):
        """Create a WorkHandler instance."""
        return WorkHandler()

    @pytest.fixture
    def context(self):
        """Create a basic context."""
        return {
            "user_prompt": "/cat:work",
            "session_id": "test-session",
            "project_root": "/test/project",
        }

    def test_returns_string(self, handler, context):
        """Handler returns a string."""
        result = handler.handle(context)
        assert isinstance(result, str)

    def test_contains_precomputed_marker(self, handler, context):
        """Output contains PRE-COMPUTED marker."""
        result = handler.handle(context)
        assert "PRE-COMPUTED WORK PROGRESS FORMAT" in result

    def test_contains_instruction(self, handler, context):
        """Output contains INSTRUCTION markers."""
        result = handler.handle(context)
        assert "INSTRUCTION:" in result

    def test_contains_progress_templates(self, handler, context):
        """Output contains progress template section."""
        result = handler.handle(context)
        assert "## Progress Display Templates" in result

    def test_contains_header_format(self, handler, context):
        """Output contains header format."""
        result = handler.handle(context)
        assert "### Header Format" in result
        assert "🐱 >" in result

    def test_contains_phase_symbols(self, handler, context):
        """Output contains phase symbols table."""
        result = handler.handle(context)
        assert "### Phase Symbols" in result
        assert "○" in result  # Pending
        assert "●" in result  # Complete
        assert "◉" in result  # Active
        assert "✗" in result  # Failed

    def test_contains_example_transitions(self, handler, context):
        """Output contains example transitions."""
        result = handler.handle(context)
        assert "### Example Transitions" in result
        assert "Preparing" in result
        assert "Executing" in result
        assert "Reviewing" in result
        assert "Merging" in result

    def test_empty_context_works(self, handler):
        """Handler works with empty context."""
        result = handler.handle({})
        assert result is not None
        assert "PRE-COMPUTED WORK PROGRESS FORMAT" in result


class TestWorkHandlerBoxes:
    """Tests for WorkHandler pre-computed boxes."""

    @pytest.fixture
    def handler(self):
        """Create a WorkHandler instance."""
        return WorkHandler()

    @pytest.fixture
    def context(self):
        """Create a basic context."""
        return {"user_prompt": "/cat:work"}

    def test_contains_task_complete_with_next(self, handler, context):
        """Output contains task complete with next task box."""
        result = handler.handle(context)
        assert "TASK_COMPLETE_WITH_NEXT_TASK" in result
        assert "✓ Task Complete" in result

    def test_contains_scope_complete(self, handler, context):
        """Output contains scope complete box."""
        result = handler.handle(context)
        assert "SCOPE_COMPLETE" in result
        assert "✓ Scope Complete" in result

    def test_contains_task_complete_low_trust(self, handler, context):
        """Output contains low trust task complete box."""
        result = handler.handle(context)
        assert "TASK_COMPLETE_LOW_TRUST" in result

    def test_task_complete_box_has_placeholders(self, handler, context):
        """Task complete box has placeholders for substitution."""
        result = handler.handle(context)
        assert "{task-name}" in result
        assert "{next-task-name}" in result

    def test_task_complete_box_has_commands(self, handler, context):
        """Task complete box has relevant commands."""
        result = handler.handle(context)
        # Low trust version should have /cat:work
        assert "`/cat:work`" in result

    def test_auto_continue_box_has_stop_abort(self, handler, context):
        """Auto-continue box has stop/abort instructions."""
        result = handler.handle(context)
        assert '"stop"' in result
        assert '"abort"' in result

    def test_boxes_have_structure(self, handler, context):
        """All boxes have proper box structure."""
        result = handler.handle(context)
        # Should have top border
        assert "╭" in result
        # Should have bottom border
        assert "╰" in result
        # Should have vertical lines
        assert "│" in result
        # Should have separators
        assert "├" in result


class TestWorkHandlerTaskIdExtraction:
    """Tests for task ID extraction from prompt."""

    @pytest.fixture
    def handler(self):
        """Create a WorkHandler instance."""
        return WorkHandler()

    def test_prompt_with_task_id(self, handler):
        """Handler extracts task ID from prompt."""
        context = {"user_prompt": "/cat:work 2.0-my-task"}
        result = handler.handle(context)
        # The handler should work regardless
        assert result is not None
        assert "PRE-COMPUTED WORK PROGRESS FORMAT" in result

    def test_prompt_with_version_scope(self, handler):
        """Handler handles version scope."""
        context = {"user_prompt": "/cat:work 2.0"}
        result = handler.handle(context)
        assert result is not None

    def test_prompt_without_scope(self, handler):
        """Handler handles no scope."""
        context = {"user_prompt": "/cat:work"}
        result = handler.handle(context)
        assert result is not None

    def test_prompt_with_extra_text(self, handler):
        """Handler handles extra text in prompt."""
        context = {"user_prompt": "please run /cat:work 2.0-task now"}
        result = handler.handle(context)
        assert result is not None


class TestBuildTaskCompleteWithNext:
    """Tests for _build_task_complete_with_next method."""

    @pytest.fixture
    def handler(self):
        """Create a WorkHandler instance."""
        return WorkHandler()

    def test_returns_string(self, handler):
        """Method returns a string."""
        result = handler._build_task_complete_with_next(58)
        assert isinstance(result, str)

    def test_has_header(self, handler):
        """Box has task complete header."""
        result = handler._build_task_complete_with_next(58)
        assert "✓ Task Complete" in result

    def test_has_merged_message(self, handler):
        """Box has merged to main message."""
        result = handler._build_task_complete_with_next(58)
        assert "merged to main" in result

    def test_has_next_task_placeholder(self, handler):
        """Box has next task placeholder."""
        result = handler._build_task_complete_with_next(58)
        assert "{next-task-name}" in result

    def test_has_auto_continue_message(self, handler):
        """Box has auto-continue message."""
        result = handler._build_task_complete_with_next(58)
        assert "Auto-continuing" in result


class TestBuildScopeComplete:
    """Tests for _build_scope_complete method."""

    @pytest.fixture
    def handler(self):
        """Create a WorkHandler instance."""
        return WorkHandler()

    def test_returns_string(self, handler):
        """Method returns a string."""
        result = handler._build_scope_complete(58)
        assert isinstance(result, str)

    def test_has_header(self, handler):
        """Box has scope complete header."""
        result = handler._build_scope_complete(58)
        assert "✓ Scope Complete" in result

    def test_has_complete_message(self, handler):
        """Box has all tasks complete message."""
        result = handler._build_scope_complete(58)
        assert "all tasks complete" in result


class TestBuildTaskCompleteLowTrust:
    """Tests for _build_task_complete_low_trust method."""

    @pytest.fixture
    def handler(self):
        """Create a WorkHandler instance."""
        return WorkHandler()

    def test_returns_string(self, handler):
        """Method returns a string."""
        result = handler._build_task_complete_low_trust(58)
        assert isinstance(result, str)

    def test_has_header(self, handler):
        """Box has task complete header."""
        result = handler._build_task_complete_low_trust(58)
        assert "✓ Task Complete" in result

    def test_has_manual_continue_command(self, handler):
        """Box has manual continue command."""
        result = handler._build_task_complete_low_trust(58)
        assert "`/cat:work`" in result
        assert "to continue" in result

    def test_has_next_up_section(self, handler):
        """Box has next up section."""
        result = handler._build_task_complete_low_trust(58)
        assert "Next Up" in result


class TestBuildSimpleBox:
    """Tests for build_simple_box function (M229 regression pattern)."""

    def test_basic_box(self):
        """Build basic box with icon and title."""
        result = build_simple_box("📊", "Test Title", ["Content line"])
        lines = result.split("\n")
        assert lines[0].startswith("╭")
        assert lines[0].endswith("╮")
        assert "📊 Test Title" in lines[0]
        assert lines[-1].startswith("╰")
        assert lines[-1].endswith("╯")

    def test_box_with_multiple_content_lines(self):
        """Box contains all content lines."""
        result = build_simple_box("✅", "Header", ["Line 1", "Line 2", "Line 3"])
        assert "Line 1" in result
        assert "Line 2" in result
        assert "Line 3" in result

    def test_content_lines_have_consistent_width(self):
        """All content lines have consistent width (M229 regression test)."""
        result = build_simple_box("📊", "TEST", [
            "Short",
            "Medium length content",
            "Longer content line here"
        ])
        lines = result.split("\n")
        content_lines = [l for l in lines if l.startswith("│ ")]
        if content_lines:
            widths = [len(l) for l in content_lines]
            assert len(set(widths)) == 1, f"Inconsistent widths: {widths}"

    def test_header_and_footer_same_width(self):
        """Header and footer have consistent width."""
        result = build_simple_box("✅", "TITLE", ["Content"])
        lines = result.split("\n")
        header_width = len(lines[0])
        footer_width = len(lines[-1])
        assert header_width == footer_width, f"Header ({header_width}) != Footer ({footer_width})"

    def test_all_lines_same_width(self):
        """All lines in box have consistent width (M229 regression test)."""
        result = build_simple_box("🔧", "Settings", [
            "Option 1: value",
            "Option 2: longer value here",
            "Option 3: x"
        ])
        lines = result.split("\n")
        widths = [len(l) for l in lines]
        assert len(set(widths)) == 1, f"Inconsistent line widths: {widths}"

    def test_empty_content(self):
        """Box handles empty content list."""
        result = build_simple_box("ℹ️", "INFO", [])
        lines = result.split("\n")
        # Should have header and footer at minimum
        assert len(lines) >= 2
        assert lines[0].startswith("╭")
        assert lines[-1].startswith("╰")

    def test_emoji_icon(self):
        """Box handles emoji icons correctly."""
        result = build_simple_box("🐱", "CAT", ["Content"])
        assert "🐱 CAT" in result

    def test_long_content(self):
        """Box handles long content lines."""
        long_line = "A" * 100
        result = build_simple_box("📊", "Test", [long_line])
        assert long_line in result
        lines = result.split("\n")
        # All lines should still be same width
        widths = [len(l) for l in lines]
        assert len(set(widths)) == 1, f"Inconsistent widths with long content: {widths}"
