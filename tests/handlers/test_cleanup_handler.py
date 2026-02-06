"""Tests for CleanupHandler."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.cleanup_handler import CleanupHandler


class TestCleanupHandler:
    """Tests for CleanupHandler class."""

    @pytest.fixture
    def handler(self):
        """Create a CleanupHandler instance."""
        return CleanupHandler()

    def test_returns_none_without_phase(self, handler):
        """Handler returns None when phase is missing."""
        result = handler.handle({})
        assert result is None

    def test_returns_none_for_invalid_phase(self, handler):
        """Handler returns None for unknown phase."""
        result = handler.handle({"phase": "invalid"})
        assert result is None

    def test_survey_phase(self, handler):
        """Handler handles survey phase."""
        result = handler.handle({"phase": "survey"})
        assert result is not None

    def test_plan_phase(self, handler):
        """Handler handles plan phase."""
        result = handler.handle({"phase": "plan"})
        assert result is not None

    def test_verify_phase(self, handler):
        """Handler handles verify phase."""
        result = handler.handle({"phase": "verify"})
        assert result is not None


class TestCleanupSurveyPhase:
    """Tests for CleanupHandler survey phase."""

    @pytest.fixture
    def handler(self):
        """Create a CleanupHandler instance."""
        return CleanupHandler()

    @pytest.fixture
    def survey_context(self, sample_worktree_data, sample_lock_data,
                       sample_branch_data, sample_stale_remote_data):
        """Create a survey phase context."""
        return {
            "phase": "survey",
            "worktrees": sample_worktree_data,
            "locks": sample_lock_data,
            "branches": sample_branch_data,
            "stale_remotes": sample_stale_remote_data,
            "context_file": ".claude/context.md",
        }

    def test_returns_string(self, handler, survey_context):
        """Survey returns a string."""
        result = handler.handle(survey_context)
        assert isinstance(result, str)

    def test_contains_script_output_marker(self, handler, survey_context):
        """Output contains SCRIPT OUTPUT marker."""
        result = handler.handle(survey_context)
        assert "SCRIPT OUTPUT SURVEY DISPLAY" in result

    def test_contains_instruction(self, handler, survey_context):
        """Output contains INSTRUCTION marker."""
        result = handler.handle(survey_context)
        assert "INSTRUCTION:" in result

    def test_contains_survey_header(self, handler, survey_context):
        """Output contains survey header."""
        result = handler.handle(survey_context)
        assert "🔍 Survey Results" in result

    def test_contains_worktrees_section(self, handler, survey_context):
        """Output contains worktrees section."""
        result = handler.handle(survey_context)
        assert "📁 Worktrees" in result

    def test_contains_locks_section(self, handler, survey_context):
        """Output contains locks section."""
        result = handler.handle(survey_context)
        assert "🔒 Task Locks" in result

    def test_contains_branches_section(self, handler, survey_context):
        """Output contains branches section."""
        result = handler.handle(survey_context)
        assert "🌿 CAT Branches" in result

    def test_contains_stale_remotes_section(self, handler, survey_context):
        """Output contains stale remotes section."""
        result = handler.handle(survey_context)
        assert "⏳ Stale Remotes" in result

    def test_contains_context_file(self, handler, survey_context):
        """Output contains context file info."""
        result = handler.handle(survey_context)
        assert "📝 Context:" in result
        assert ".claude/context.md" in result

    def test_contains_worktree_data(self, handler, survey_context):
        """Output contains worktree data."""
        result = handler.handle(survey_context)
        assert "1.0-task-a" in result
        assert "1.0-task-b" in result

    def test_contains_lock_data(self, handler, survey_context):
        """Output contains lock data."""
        result = handler.handle(survey_context)
        assert "abc123de" in result  # First 8 chars of session

    def test_contains_branch_data(self, handler, survey_context):
        """Output contains branch data."""
        result = handler.handle(survey_context)
        assert "1.0-task-c" in result

    def test_contains_counts(self, handler, survey_context):
        """Output contains summary counts."""
        result = handler.handle(survey_context)
        assert "2 worktrees" in result
        assert "2 locks" in result
        assert "3 branches" in result
        assert "1 stale remotes" in result

    def test_empty_lists_show_none(self, handler):
        """Empty lists show 'None found'."""
        context = {
            "phase": "survey",
            "worktrees": [],
            "locks": [],
            "branches": [],
            "stale_remotes": [],
        }
        result = handler.handle(context)
        assert "None found" in result

    def test_box_structure(self, handler, survey_context):
        """Survey output has box structure."""
        result = handler.handle(survey_context)
        assert "╭─" in result
        assert "╰" in result
        assert "│" in result


class TestCleanupPlanPhase:
    """Tests for CleanupHandler plan phase."""

    @pytest.fixture
    def handler(self):
        """Create a CleanupHandler instance."""
        return CleanupHandler()

    @pytest.fixture
    def plan_context(self):
        """Create a plan phase context."""
        return {
            "phase": "plan",
            "locks_to_remove": ["1.0-task-a", "1.0-task-b"],
            "worktrees_to_remove": [
                {"path": "/workspace/.worktrees/1.0-task-a", "branch": "1.0-task-a"},
            ],
            "branches_to_remove": ["1.0-task-a", "1.0-task-b"],
            "stale_remotes": [
                {"branch": "origin/1.0-old", "staleness": "5 days"},
            ],
        }

    def test_returns_string(self, handler, plan_context):
        """Plan returns a string."""
        result = handler.handle(plan_context)
        assert isinstance(result, str)

    def test_contains_script_output_marker(self, handler, plan_context):
        """Output contains SCRIPT OUTPUT marker."""
        result = handler.handle(plan_context)
        assert "SCRIPT OUTPUT PLAN DISPLAY" in result

    def test_contains_instruction(self, handler, plan_context):
        """Output contains INSTRUCTION marker."""
        result = handler.handle(plan_context)
        assert "INSTRUCTION:" in result

    def test_contains_plan_header(self, handler, plan_context):
        """Output contains plan header."""
        result = handler.handle(plan_context)
        assert "🧹 Cleanup Plan" in result

    def test_contains_locks_section(self, handler, plan_context):
        """Output contains locks to remove section."""
        result = handler.handle(plan_context)
        assert "🔒 Locks to Remove:" in result

    def test_contains_worktrees_section(self, handler, plan_context):
        """Output contains worktrees to remove section."""
        result = handler.handle(plan_context)
        assert "📁 Worktrees to Remove:" in result

    def test_contains_branches_section(self, handler, plan_context):
        """Output contains branches to remove section."""
        result = handler.handle(plan_context)
        assert "🌿 Branches to Remove:" in result

    def test_contains_stale_remotes_section(self, handler, plan_context):
        """Output contains stale remotes section."""
        result = handler.handle(plan_context)
        assert "⏳ Stale Remotes" in result

    def test_contains_lock_items(self, handler, plan_context):
        """Output contains lock items to remove."""
        result = handler.handle(plan_context)
        assert "1.0-task-a" in result
        assert "1.0-task-b" in result

    def test_contains_worktree_items(self, handler, plan_context):
        """Output contains worktree items with path and branch."""
        result = handler.handle(plan_context)
        assert "/workspace/.worktrees/1.0-task-a" in result
        assert "→ 1.0-task-a" in result

    def test_contains_total_count(self, handler, plan_context):
        """Output contains total items count."""
        result = handler.handle(plan_context)
        assert "Total items to remove: 5" in result  # 2 locks + 1 worktree + 2 branches

    def test_contains_confirmation_prompt(self, handler, plan_context):
        """Output contains confirmation prompt."""
        result = handler.handle(plan_context)
        assert "Confirm cleanup?" in result
        assert "yes/no" in result

    def test_empty_sections_show_none(self, handler):
        """Empty sections show '(none)'."""
        context = {
            "phase": "plan",
            "locks_to_remove": [],
            "worktrees_to_remove": [],
            "branches_to_remove": [],
            "stale_remotes": [],
        }
        result = handler.handle(context)
        assert "(none)" in result

    def test_box_structure(self, handler, plan_context):
        """Plan output has box structure."""
        result = handler.handle(plan_context)
        assert "╭─" in result
        assert "╰" in result
        assert "│" in result


class TestCleanupVerifyPhase:
    """Tests for CleanupHandler verify phase."""

    @pytest.fixture
    def handler(self):
        """Create a CleanupHandler instance."""
        return CleanupHandler()

    @pytest.fixture
    def verify_context(self):
        """Create a verify phase context."""
        return {
            "phase": "verify",
            "remaining_worktrees": ["/workspace/.worktrees/active-task"],
            "remaining_branches": ["active-task", "main"],
            "remaining_locks": [],
            "removed_counts": {
                "locks": 2,
                "worktrees": 1,
                "branches": 3,
            },
        }

    def test_returns_string(self, handler, verify_context):
        """Verify returns a string."""
        result = handler.handle(verify_context)
        assert isinstance(result, str)

    def test_contains_script_output_marker(self, handler, verify_context):
        """Output contains SCRIPT OUTPUT marker."""
        result = handler.handle(verify_context)
        assert "SCRIPT OUTPUT VERIFY DISPLAY" in result

    def test_contains_instruction(self, handler, verify_context):
        """Output contains INSTRUCTION marker."""
        result = handler.handle(verify_context)
        assert "INSTRUCTION:" in result

    def test_contains_verify_header(self, handler, verify_context):
        """Output contains verify header."""
        result = handler.handle(verify_context)
        assert "✅ Cleanup Complete" in result

    def test_contains_removed_summary(self, handler, verify_context):
        """Output contains removed summary."""
        result = handler.handle(verify_context)
        assert "Removed:" in result
        assert "2 lock(s)" in result
        assert "1 worktree(s)" in result
        assert "3 branch(es)" in result

    def test_contains_remaining_worktrees(self, handler, verify_context):
        """Output contains remaining worktrees section."""
        result = handler.handle(verify_context)
        assert "📁 Remaining Worktrees:" in result
        assert "active-task" in result

    def test_contains_remaining_branches(self, handler, verify_context):
        """Output contains remaining branches section."""
        result = handler.handle(verify_context)
        assert "🌿 Remaining CAT Branches:" in result
        assert "main" in result

    def test_contains_remaining_locks(self, handler, verify_context):
        """Output contains remaining locks section."""
        result = handler.handle(verify_context)
        assert "🔒 Remaining Locks:" in result
        # Should show (none) since remaining_locks is empty
        assert "(none)" in result

    def test_empty_remaining_shows_none(self, handler):
        """Empty remaining lists show '(none)'."""
        context = {
            "phase": "verify",
            "remaining_worktrees": [],
            "remaining_branches": [],
            "remaining_locks": [],
            "removed_counts": {"locks": 0, "worktrees": 0, "branches": 0},
        }
        result = handler.handle(context)
        # Should have multiple "(none)" entries
        assert result.count("(none)") >= 3

    def test_box_structure(self, handler, verify_context):
        """Verify output has box structure."""
        result = handler.handle(verify_context)
        assert "╭─" in result
        assert "╰" in result
        assert "│" in result

    def test_missing_removed_counts(self, handler):
        """Handler handles missing removed_counts gracefully."""
        context = {
            "phase": "verify",
            "remaining_worktrees": [],
            "remaining_branches": [],
            "remaining_locks": [],
            # No removed_counts
        }
        result = handler.handle(context)
        assert result is not None
        assert "0 lock(s)" in result
        assert "0 worktree(s)" in result
        assert "0 branch(es)" in result
