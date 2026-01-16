"""Tests for config_handler display functions."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.config_handler import build_simple_header_box


class TestBuildSimpleHeaderBox:
    """Tests for build_simple_header_box function."""

    def test_basic_box(self):
        """Build basic box with icon and title."""
        result = build_simple_header_box("📊", "TEST TITLE", ["Content line"])
        lines = result.split("\n")
        assert lines[0].startswith("╭")
        assert lines[0].endswith("╮")
        assert "📊 TEST TITLE" in lines[0]
        assert lines[-1].startswith("╰")
        assert lines[-1].endswith("╯")

    def test_box_with_multiple_content_lines(self):
        """Box contains all content lines."""
        result = build_simple_header_box("✅", "HEADER", ["Line 1", "Line 2", "Line 3"])
        assert "Line 1" in result
        assert "Line 2" in result
        assert "Line 3" in result

    def test_empty_content(self):
        """Box handles empty content list."""
        result = build_simple_header_box("ℹ️", "INFO", [])
        lines = result.split("\n")
        # Should have header and footer at minimum
        assert len(lines) >= 2
        assert lines[0].startswith("╭")
        assert lines[-1].startswith("╰")

    def test_content_lines_have_consistent_width(self):
        """All content lines have consistent width (M229 pattern)."""
        result = build_simple_header_box("📊", "TEST", [
            "Short",
            "Medium length content",
            "Longer content line here"
        ])
        lines = result.split("\n")
        # Content lines are everything between header and footer
        content_lines = [l for l in lines if l.startswith("│")]
        if content_lines:
            widths = [len(l) for l in content_lines]
            assert len(set(widths)) == 1, f"Inconsistent widths: {widths}"

    def test_header_and_footer_same_width(self):
        """Header and footer have consistent width."""
        result = build_simple_header_box("✅", "TITLE", ["Content"])
        lines = result.split("\n")
        header_width = len(lines[0])
        footer_width = len(lines[-1])
        assert header_width == footer_width, f"Header ({header_width}) != Footer ({footer_width})"

    def test_box_structure(self):
        """Box has correct structure with proper borders."""
        result = build_simple_header_box("🔧", "SETTINGS", ["Option 1", "Option 2"])
        lines = result.split("\n")
        # First line: rounded top with header
        assert lines[0].startswith("╭─── ")
        assert "🔧 SETTINGS" in lines[0]
        # Content lines
        for line in lines[1:-1]:
            assert line.startswith("│ ")
            assert line.endswith(" │")
        # Last line: rounded bottom
        assert lines[-1].startswith("╰")
        assert lines[-1].endswith("╯")

    def test_long_header(self):
        """Box handles long header text."""
        result = build_simple_header_box("📊", "VERY LONG HEADER TITLE TEXT", ["Short"])
        lines = result.split("\n")
        assert "VERY LONG HEADER TITLE TEXT" in lines[0]
        # Still valid box
        assert lines[0].startswith("╭")
        assert lines[-1].startswith("╰")

    def test_emoji_content(self):
        """Box handles emoji in content lines."""
        result = build_simple_header_box("✅", "TEST", ["🔧 Setting 1", "📊 Metric"])
        assert "🔧 Setting 1" in result
        assert "📊 Metric" in result
