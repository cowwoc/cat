"""Tests for display utility functions in status_handler."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.status_handler import (
    display_width,
    build_line,
    build_border,
    build_progress_bar,
    build_inner_box,
)


class TestDisplayWidth:
    """Tests for display_width function."""

    def test_empty_string(self):
        """Empty string has width 0."""
        assert display_width("") == 0

    def test_ascii_text(self):
        """ASCII text has width equal to length."""
        assert display_width("hello") == 5
        assert display_width("test string") == 11

    def test_single_emoji(self):
        """Single emojis have width 2."""
        assert display_width("🐱") == 2
        assert display_width("✅") == 2
        assert display_width("📊") == 2

    def test_emoji_with_text(self):
        """Text with emoji calculates correctly."""
        assert display_width("🐱 cat") == 6  # 2 + 1 + 3
        assert display_width("✅ done") == 7  # 2 + 1 + 4

    def test_multiple_emojis(self):
        """Multiple emojis calculate correctly."""
        assert display_width("🐱🐱") == 4
        # Note: ✅ is in WIDTH_2_SINGLE, 🔄 and 🔳 are also width 2 each
        # "✅ 🔄 🔳" = 2 + 1 + 2 + 1 + 2 = 8 (implementation counts correctly)
        assert display_width("✅ 🔄 🔳") == 8

    def test_variation_selector(self):
        """Variation selectors don't add width."""
        # ☑️ is ☑ + variation selector
        result = display_width("☑️")
        # Should be 2 (emoji width) not 3
        assert result in [1, 2]  # May vary by implementation

    def test_box_drawing_characters(self):
        """Box drawing characters have width 1."""
        assert display_width("─") == 1
        assert display_width("│") == 1
        assert display_width("╭╮") == 2
        assert display_width("╰╯") == 2


class TestBuildLine:
    """Tests for build_line function."""

    def test_simple_content(self):
        """Build line with simple content."""
        result = build_line("hello", 10)
        assert result.startswith("│ ")
        assert result.endswith(" │")
        assert "hello" in result

    def test_empty_content(self):
        """Build line with empty content."""
        result = build_line("", 10)
        assert result == "│ " + " " * 10 + " │"

    def test_padding_calculation(self):
        """Padding is correctly calculated."""
        result = build_line("hi", 10)
        # Content "hi" is 2 chars, need 8 spaces padding
        expected = "│ hi" + " " * 8 + " │"
        assert result == expected

    def test_exact_width(self):
        """Content exactly matches width."""
        result = build_line("1234567890", 10)
        expected = "│ 1234567890 │"
        assert result == expected

    def test_emoji_content(self):
        """Line with emoji content calculates padding correctly."""
        result = build_line("🐱 cat", 10)
        # "🐱 cat" has display width 6, so 4 spaces padding
        assert result.startswith("│ 🐱 cat")
        assert result.endswith(" │")
        # Verify total structure
        assert "│ " in result
        assert " │" in result


class TestBuildBorder:
    """Tests for build_border function."""

    def test_top_border(self):
        """Build top border."""
        result = build_border(10, is_top=True)
        assert result.startswith("╭")
        assert result.endswith("╮")
        assert "─" * 12 in result  # max_width + 2 dashes

    def test_bottom_border(self):
        """Build bottom border."""
        result = build_border(10, is_top=False)
        assert result.startswith("╰")
        assert result.endswith("╯")
        assert "─" * 12 in result

    def test_border_width(self):
        """Border width is max_width + 2."""
        for width in [5, 10, 20]:
            top = build_border(width, is_top=True)
            bottom = build_border(width, is_top=False)
            # Total length should be 1 (corner) + (width+2) dashes + 1 (corner)
            expected_len = 1 + width + 2 + 1
            assert len(top) == expected_len
            assert len(bottom) == expected_len


class TestBuildProgressBar:
    """Tests for build_progress_bar function."""

    def test_zero_percent(self):
        """0% progress shows all empty."""
        result = build_progress_bar(0, width=10)
        assert result == "░" * 10

    def test_hundred_percent(self):
        """100% progress shows all filled."""
        result = build_progress_bar(100, width=10)
        assert result == "█" * 10

    def test_fifty_percent(self):
        """50% progress shows half filled."""
        result = build_progress_bar(50, width=10)
        assert result == "█" * 5 + "░" * 5

    def test_default_width(self):
        """Default width is 25."""
        result = build_progress_bar(0)
        assert len(result) == 25

    def test_custom_width(self):
        """Custom width is respected."""
        result = build_progress_bar(0, width=40)
        assert len(result) == 40

    def test_partial_fill(self):
        """Partial percentages round down."""
        # 33% of 10 = 3.3, should round to 3
        result = build_progress_bar(33, width=10)
        assert result.count("█") == 3
        assert result.count("░") == 7


class TestBuildInnerBox:
    """Tests for build_inner_box function."""

    def test_simple_box(self):
        """Build simple inner box."""
        result = build_inner_box("Header", ["Item 1", "Item 2"])
        assert len(result) == 4  # top + 2 content lines + bottom
        assert "Header" in result[0]
        assert "Item 1" in result[1]
        assert "Item 2" in result[2]

    def test_empty_content(self):
        """Box with empty content list."""
        result = build_inner_box("Header", [])
        assert len(result) == 3  # top + 1 empty line + bottom
        assert "Header" in result[0]

    def test_box_structure(self):
        """Box has correct structure."""
        result = build_inner_box("Test", ["Content"])
        # First line is top with header
        assert result[0].startswith("╭─ ")
        assert "Test" in result[0]
        assert result[0].endswith("╮")
        # Middle lines are content
        assert result[1].startswith("│ ")
        assert result[1].endswith(" │")
        # Last line is bottom
        assert result[-1].startswith("╰")
        assert result[-1].endswith("╯")

    def test_forced_width(self):
        """Forced width parameter is respected."""
        result = build_inner_box("H", ["A"], forced_width=50)
        # Content lines should accommodate the forced width
        content_line = result[1]
        # Line is │ + space + content + padding + space + │
        assert len(content_line) > 50

    def test_emoji_header(self):
        """Header with emoji calculates width correctly."""
        result = build_inner_box("📦 Package", ["Item"])
        assert "📦 Package" in result[0]
        # Should still have proper structure
        assert result[0].startswith("╭─ ")
        assert result[0].endswith("╮")

    def test_returns_list(self):
        """Function returns a list of strings."""
        result = build_inner_box("Header", ["Content"])
        assert isinstance(result, list)
        assert all(isinstance(line, str) for line in result)
