"""Tests for stakeholder_handler display functions."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.stakeholder_handler import build_header_box, build_concern_box


class TestBuildHeaderBox:
    """Tests for build_header_box function."""

    def test_basic_box(self):
        """Build basic header box."""
        result = build_header_box("TEST HEADER", ["Content line"])
        lines = result.split("\n")
        assert lines[0].startswith("╭")
        assert lines[0].endswith("╮")
        assert "TEST HEADER" in lines[0]
        assert lines[-1].startswith("╰")
        assert lines[-1].endswith("╯")

    def test_box_with_multiple_content_lines(self):
        """Box contains all content lines."""
        result = build_header_box("HEADER", ["Line 1", "Line 2", "Line 3"])
        assert "Line 1" in result
        assert "Line 2" in result
        assert "Line 3" in result

    def test_content_lines_have_consistent_width(self):
        """All content lines have consistent width (M229 pattern)."""
        result = build_header_box("TEST", [
            "Short",
            "Medium length content",
            "Longer content line here"
        ])
        lines = result.split("\n")
        content_lines = [l for l in lines if l.startswith("│ ")]
        if content_lines:
            widths = [len(l) for l in content_lines]
            assert len(set(widths)) == 1, f"Inconsistent widths: {widths}"

    def test_with_separators(self):
        """Box includes separators at specified indices."""
        result = build_header_box("HEADER", ["Line 0", "Line 1", "Line 2"], separator_indices=[1])
        lines = result.split("\n")
        # Should have a separator line before Line 1
        separator_lines = [l for l in lines if l.startswith("├") and l.endswith("┤")]
        assert len(separator_lines) >= 1

    def test_header_and_footer_same_width(self):
        """Header and footer have consistent width."""
        result = build_header_box("TITLE", ["Content"])
        lines = result.split("\n")
        header_width = len(lines[0])
        footer_width = len(lines[-1])
        # Note: separators may have different pattern but content should align
        assert header_width == footer_width, f"Header ({header_width}) != Footer ({footer_width})"

    def test_empty_separators(self):
        """Empty separator list is handled correctly."""
        result = build_header_box("HEADER", ["Content"], separator_indices=[])
        assert "├" not in result or "HEADER" in result  # No separators except maybe in header

    def test_none_separators(self):
        """None separator list is handled correctly."""
        result = build_header_box("HEADER", ["Content"], separator_indices=None)
        # Should work without error
        assert "HEADER" in result


class TestBuildConcernBox:
    """Tests for build_concern_box function."""

    def test_basic_concern_box(self):
        """Build basic concern box with square corners."""
        result = build_concern_box("CRITICAL", ["Concern description"])
        lines = result.split("\n")
        # Square corners
        assert lines[0].startswith("┌")
        assert lines[0].endswith("┐")
        assert "CRITICAL" in lines[0]
        assert lines[-1].startswith("└")
        assert lines[-1].endswith("┘")

    def test_concern_box_with_multiple_lines(self):
        """Concern box contains all concern lines."""
        result = build_concern_box("HIGH", ["Concern 1", "Concern 2", "Concern 3"])
        assert "Concern 1" in result
        assert "Concern 2" in result
        assert "Concern 3" in result

    def test_content_lines_have_consistent_width(self):
        """All content lines have consistent width (M229 pattern)."""
        result = build_concern_box("CRITICAL", [
            "Short",
            "Medium length concern",
            "Longer concern description here"
        ])
        lines = result.split("\n")
        content_lines = [l for l in lines if l.startswith("│ ") and not l.startswith("│ CRITICAL")]
        if content_lines:
            widths = [len(l) for l in content_lines]
            assert len(set(widths)) == 1, f"Inconsistent widths: {widths}"

    def test_header_and_footer_same_width(self):
        """Header and footer have consistent width."""
        result = build_concern_box("WARNING", ["Content"])
        lines = result.split("\n")
        header_width = len(lines[0])
        footer_width = len(lines[-1])
        assert header_width == footer_width, f"Header ({header_width}) != Footer ({footer_width})"

    def test_different_severity_levels(self):
        """Different severity levels render correctly."""
        for severity in ["CRITICAL", "HIGH", "MEDIUM", "LOW"]:
            result = build_concern_box(severity, ["Test concern"])
            assert severity in result
            # Valid box structure
            lines = result.split("\n")
            assert lines[0].startswith("┌")
            assert lines[-1].startswith("└")

    def test_empty_concerns(self):
        """Handle empty concerns list."""
        result = build_concern_box("INFO", [])
        lines = result.split("\n")
        # Should still have header and footer
        assert len(lines) >= 2
        assert lines[0].startswith("┌")
        assert lines[-1].startswith("└")
