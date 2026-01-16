"""Tests for research_handler display functions."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.research_handler import (
    rating_to_circles,
    sum_ratings,
    build_scorecard_row_pair,
    build_scorecard_row_triple,
    build_scorecard,
    build_comparison_row,
    build_comparison_table,
    build_concerns_box,
)


class TestRatingToCircles:
    """Tests for rating_to_circles function."""

    def test_rating_1(self):
        """Rating 1 shows 1 filled, 4 empty."""
        assert rating_to_circles(1) == "●○○○○"

    def test_rating_2(self):
        """Rating 2 shows 2 filled, 3 empty."""
        assert rating_to_circles(2) == "●●○○○"

    def test_rating_3(self):
        """Rating 3 shows 3 filled, 2 empty."""
        assert rating_to_circles(3) == "●●●○○"

    def test_rating_4(self):
        """Rating 4 shows 4 filled, 1 empty."""
        assert rating_to_circles(4) == "●●●●○"

    def test_rating_5(self):
        """Rating 5 shows all filled."""
        assert rating_to_circles(5) == "●●●●●"

    def test_rating_below_1(self):
        """Ratings below 1 are clamped to 1."""
        assert rating_to_circles(0) == "●○○○○"
        assert rating_to_circles(-5) == "●○○○○"

    def test_rating_above_5(self):
        """Ratings above 5 are clamped to 5."""
        assert rating_to_circles(6) == "●●●●●"
        assert rating_to_circles(100) == "●●●●●"

    def test_circle_length(self):
        """All ratings produce exactly 5 characters."""
        for rating in range(1, 6):
            assert len(rating_to_circles(rating)) == 5


class TestSumRatings:
    """Tests for sum_ratings function."""

    def test_all_5s(self):
        """All 5s totals to 55/55."""
        ratings = {
            "Speed": 5, "Cost": 5, "Quality": 5,
            "Architect": 5, "Security": 5, "Tester": 5,
            "Performance": 5, "UX": 5, "Sales": 5,
            "Marketing": 5, "Legal": 5
        }
        total, max_possible = sum_ratings(ratings)
        assert total == 55
        assert max_possible == 55

    def test_all_1s(self):
        """All 1s totals to 11/55."""
        ratings = {
            "Speed": 1, "Cost": 1, "Quality": 1,
            "Architect": 1, "Security": 1, "Tester": 1,
            "Performance": 1, "UX": 1, "Sales": 1,
            "Marketing": 1, "Legal": 1
        }
        total, max_possible = sum_ratings(ratings)
        assert total == 11
        assert max_possible == 55

    def test_mixed_ratings(self):
        """Mixed ratings calculate correctly."""
        ratings = {
            "Speed": 4, "Cost": 3, "Quality": 4,
            "Architect": 4, "Security": 5, "Tester": 3,
            "Performance": 3, "UX": 4, "Sales": 4,
            "Marketing": 3, "Legal": 5
        }
        total, max_possible = sum_ratings(ratings)
        assert total == 42
        assert max_possible == 55

    def test_empty_ratings(self):
        """Empty ratings dict returns 0/0."""
        total, max_possible = sum_ratings({})
        assert total == 0
        assert max_possible == 0


class TestBuildScorecardRowPair:
    """Tests for build_scorecard_row_pair function."""

    def test_basic_row(self):
        """Build basic row pair."""
        result = build_scorecard_row_pair("Marketing", 3, "Legal", 4)
        assert "Marketing" in result
        assert "Legal" in result
        assert "●●●○○" in result  # rating 3
        assert "●●●●○" in result  # rating 4

    def test_row_structure(self):
        """Row has correct box structure."""
        result = build_scorecard_row_pair("A", 1, "B", 5)
        assert result.startswith("│ ")
        assert result.endswith(" │")

    def test_consistent_width(self):
        """All row pairs have consistent width of 71 characters."""
        # Test with various label lengths and ratings
        test_cases = [
            ("Marketing", 3, "Legal", 4),
            ("A", 1, "B", 5),
            ("Performance", 5, "UX", 1),
            ("Short", 2, "Long Label", 3),
        ]
        for l1, r1, l2, r2 in test_cases:
            result = build_scorecard_row_pair(l1, r1, l2, r2)
            assert len(result) == 71, f"Row pair '{l1}/{l2}' has length {len(result)}, expected 71"


class TestBuildScorecardRowTriple:
    """Tests for build_scorecard_row_triple function."""

    def test_basic_row(self):
        """Build basic row triple."""
        result = build_scorecard_row_triple("Speed", 4, "Cost", 3, "Quality", 5)
        assert "Speed" in result
        assert "Cost" in result
        assert "Quality" in result
        assert "●●●●○" in result  # rating 4
        assert "●●●○○" in result  # rating 3
        assert "●●●●●" in result  # rating 5

    def test_row_structure(self):
        """Row has correct box structure."""
        result = build_scorecard_row_triple("A", 1, "B", 2, "C", 3)
        assert result.startswith("│ ")
        assert result.endswith(" │")

    def test_consistent_width(self):
        """All row triples have consistent width of 71 characters."""
        test_cases = [
            ("Speed", 4, "Cost", 3, "Quality", 5),
            ("Architect", 5, "Security", 4, "Tester", 3),
            ("Performance", 3, "UX", 5, "Sales", 2),
            ("A", 1, "B", 2, "C", 3),
        ]
        for l1, r1, l2, r2, l3, r3 in test_cases:
            result = build_scorecard_row_triple(l1, r1, l2, r2, l3, r3)
            assert len(result) == 71, f"Row triple '{l1}/{l2}/{l3}' has length {len(result)}, expected 71"


class TestBuildScorecard:
    """Tests for build_scorecard function."""

    def test_scorecard_structure(self):
        """Scorecard has correct number of lines."""
        ratings = {
            "Speed": 4, "Cost": 3, "Quality": 4,
            "Architect": 4, "Security": 5, "Tester": 3,
            "Performance": 3, "UX": 4, "Sales": 4,
            "Marketing": 3, "Legal": 5
        }
        lines = build_scorecard(ratings)
        assert len(lines) == 9  # header, divider, 3 content rows, marketing/legal row, dividers, footer

    def test_all_lines_same_width(self):
        """All lines in scorecard have exactly 71 characters (M229 regression test)."""
        ratings = {
            "Speed": 4, "Cost": 3, "Quality": 4,
            "Architect": 4, "Security": 5, "Tester": 3,
            "Performance": 3, "UX": 4, "Sales": 4,
            "Marketing": 3, "Legal": 5
        }
        lines = build_scorecard(ratings)
        for i, line in enumerate(lines):
            assert len(line) == 71, f"Line {i} has length {len(line)}, expected 71: '{line}'"

    def test_scorecard_box_characters(self):
        """Scorecard uses correct box-drawing characters."""
        ratings = {"Speed": 3, "Cost": 3, "Quality": 3,
                   "Architect": 3, "Security": 3, "Tester": 3,
                   "Performance": 3, "UX": 3, "Sales": 3,
                   "Marketing": 3, "Legal": 3}
        lines = build_scorecard(ratings)
        # First line is top border
        assert lines[0].startswith("┌")
        assert lines[0].endswith("┐")
        # Last line is bottom border
        assert lines[-1].startswith("└")
        assert lines[-1].endswith("┘")

    def test_scorecard_with_varied_ratings(self):
        """Scorecard handles various rating combinations."""
        # Test edge case: all 1s
        ratings_low = {k: 1 for k in ["Speed", "Cost", "Quality", "Architect",
                                       "Security", "Tester", "Performance",
                                       "UX", "Sales", "Marketing", "Legal"]}
        lines = build_scorecard(ratings_low)
        assert all(len(line) == 71 for line in lines)

        # Test edge case: all 5s
        ratings_high = {k: 5 for k in ["Speed", "Cost", "Quality", "Architect",
                                        "Security", "Tester", "Performance",
                                        "UX", "Sales", "Marketing", "Legal"]}
        lines = build_scorecard(ratings_high)
        assert all(len(line) == 71 for line in lines)


class TestBuildComparisonTable:
    """Tests for build_comparison_table function."""

    def test_table_structure(self):
        """Comparison table has correct structure."""
        options = [
            {"name": "Option A", "ratings": {"Speed": 4, "Cost": 3}},
            {"name": "Option B", "ratings": {"Speed": 3, "Cost": 4}},
        ]
        lines = build_comparison_table(options)
        # Should have header, content rows, and footer
        assert len(lines) > 10
        assert lines[0].startswith("╭")
        assert lines[-1].startswith("╰")

    def test_table_contains_option_names(self):
        """Table includes option names in header."""
        options = [
            {"name": "First Option", "ratings": {"Speed": 4}},
            {"name": "Second One", "ratings": {"Speed": 3}},
        ]
        lines = build_comparison_table(options)
        header_area = "\n".join(lines[:6])
        assert "First Option" in header_area
        assert "Second One" in header_area


class TestBuildConcernsBox:
    """Tests for build_concerns_box function."""

    def test_concerns_structure(self):
        """Concerns box has correct structure."""
        concerns = {
            "ARCHITECT": ["Concern 1", "Concern 2"],
            "SECURITY": ["Security concern"],
        }
        lines = build_concerns_box(concerns)
        assert lines[0].startswith("╭")
        assert lines[-1].startswith("╰")

    def test_concerns_includes_stakeholders(self):
        """Concerns box includes stakeholder headers."""
        concerns = {
            "ARCHITECT": ["Test concern"],
            "SECURITY": ["Security note"],
        }
        lines = build_concerns_box(concerns)
        content = "\n".join(lines)
        assert "ARCHITECT" in content
        assert "SECURITY" in content

    def test_concerns_truncates_long_text(self):
        """Long concerns are truncated with ellipsis."""
        long_concern = "A" * 200  # Very long concern
        concerns = {"ARCHITECT": [long_concern]}
        lines = build_concerns_box(concerns)
        content = "\n".join(lines)
        assert "..." in content  # Should be truncated
