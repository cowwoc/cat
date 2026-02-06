"""Tests for get-issue-complete-box.py script."""

import sys
from pathlib import Path

import pytest

# Add plugin/scripts to path to import the module
SCRIPT_DIR = Path(__file__).parent.parent.parent / "plugin" / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))
sys.path.insert(0, str(SCRIPT_DIR / "lib"))

from emoji_widths import display_width, get_emoji_widths


class TestGetIssueCompleteBox:
    """Tests for get-issue-complete-box.py script functionality."""

    @pytest.fixture(autouse=True)
    def setup_emoji_widths(self):
        """Setup emoji widths for display width calculations."""
        self.ew = get_emoji_widths()

    def dw(self, s):
        """Calculate display width of string."""
        return display_width(s, self.ew)

    def extract_line_widths(self, box_output: str) -> list[int]:
        """Extract display width of each line in the box output."""
        lines = box_output.split("\n")
        return [self.dw(line) for line in lines]

    def test_scope_complete_box_alignment(self):
        """Test that Scope Complete box has aligned borders (all lines same width)."""
        # Import here to avoid module-level import issues
        from get_issue_complete_box import build_scope_complete_box

        box = build_scope_complete_box("v2.1")
        widths = self.extract_line_widths(box)

        # All lines should have the same display width
        assert len(set(widths)) == 1, (
            f"Box lines have inconsistent widths: {widths}\n"
            f"Box output:\n{box}"
        )

    def test_issue_complete_box_alignment(self):
        """Test that Issue Complete box has aligned borders (all lines same width)."""
        # Import here to avoid module-level import issues
        from get_issue_complete_box import build_issue_complete_box

        box = build_issue_complete_box(
            issue_name="test-issue",
            next_issue="next-test-issue",
            next_goal="Test the next feature",
            base_branch="v2.1"
        )
        widths = self.extract_line_widths(box)

        # All lines should have the same display width
        assert len(set(widths)) == 1, (
            f"Box lines have inconsistent widths: {widths}\n"
            f"Box output:\n{box}"
        )

    def test_scope_complete_box_no_bold_markers(self):
        """Test that Scope Complete box content does not contain markdown bold markers."""
        # Import here to avoid module-level import issues
        from get_issue_complete_box import build_scope_complete_box

        box = build_scope_complete_box("v2.1")

        # Check that the content line doesn't have ** markers around scope
        # The box should contain "v2.1 - all issues complete!" not "**v2.1** - all issues complete!"
        assert "**v2.1**" not in box, "Scope Complete box should not contain markdown bold markers"
        assert "v2.1 - all issues complete!" in box, "Scope Complete box should contain plain text scope"

    def test_scope_complete_box_various_scopes(self):
        """Test Scope Complete box alignment with various scope names."""
        # Import here to avoid module-level import issues
        from get_issue_complete_box import build_scope_complete_box

        test_scopes = ["v2.1", "v10.25", "release-2024", "feature-xyz"]

        for scope in test_scopes:
            box = build_scope_complete_box(scope)
            widths = self.extract_line_widths(box)

            # All lines should have the same display width
            assert len(set(widths)) == 1, (
                f"Box lines have inconsistent widths for scope '{scope}': {widths}\n"
                f"Box output:\n{box}"
            )
