"""Tests for HelpHandler."""

import sys
from pathlib import Path

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.help_handler import HelpHandler, WORKFLOW_DIAGRAM, HIERARCHY_TREE


class TestHelpHandler:
    """Tests for HelpHandler class."""

    @pytest.fixture
    def handler(self):
        """Create a HelpHandler instance."""
        return HelpHandler()

    @pytest.fixture
    def context(self):
        """Create a basic context."""
        return {
            "user_prompt": "/cat:help",
            "session_id": "test-session",
            "project_root": "/test/project",
            "plugin_root": "/test/plugin",
        }

    def test_returns_string(self, handler, context):
        """Handler returns a string."""
        result = handler.handle(context)
        assert isinstance(result, str)

    def test_contains_script_output_marker(self, handler, context):
        """Output contains SCRIPT OUTPUT marker."""
        result = handler.handle(context)
        assert "SCRIPT OUTPUT HELP DISPLAY" in result

    def test_contains_instruction(self, handler, context):
        """Output contains INSTRUCTION marker."""
        result = handler.handle(context)
        assert "INSTRUCTION:" in result

    def test_contains_command_reference(self, handler, context):
        """Output contains CAT Command Reference header."""
        result = handler.handle(context)
        assert "# CAT Command Reference" in result

    def test_contains_essential_commands(self, handler, context):
        """Output lists essential commands."""
        result = handler.handle(context)
        assert "/cat:init" in result
        assert "/cat:status" in result
        assert "/cat:work" in result

    def test_contains_planning_commands(self, handler, context):
        """Output lists planning commands."""
        result = handler.handle(context)
        assert "/cat:add" in result
        assert "/cat:remove" in result
        assert "/cat:config" in result

    def test_contains_advanced_commands(self, handler, context):
        """Output lists advanced commands."""
        result = handler.handle(context)
        assert "/cat:research" in result
        assert "/cat:cleanup" in result
        assert "/cat:spawn-subagent" in result
        assert "/cat:token-report" in result

    def test_contains_workflow_diagram(self, handler, context):
        """Output contains workflow diagram."""
        result = handler.handle(context)
        assert WORKFLOW_DIAGRAM in result

    def test_contains_hierarchy_tree(self, handler, context):
        """Output contains hierarchy tree."""
        result = handler.handle(context)
        assert HIERARCHY_TREE in result

    def test_contains_trust_levels(self, handler, context):
        """Output explains trust levels."""
        result = handler.handle(context)
        assert "Low" in result
        assert "Medium" in result
        assert "High" in result

    def test_contains_config_options(self, handler, context):
        """Output shows configuration options."""
        result = handler.handle(context)
        assert "trust" in result
        assert "verify" in result
        assert "curiosity" in result
        assert "patience" in result

    def test_empty_context_still_works(self, handler):
        """Handler works with empty context."""
        result = handler.handle({})
        assert result is not None
        assert "CAT Command Reference" in result

    def test_output_is_static(self, handler, context):
        """Output is the same regardless of context."""
        result1 = handler.handle(context)
        result2 = handler.handle({"different": "context"})
        assert result1 == result2


class TestWorkflowDiagram:
    """Tests for WORKFLOW_DIAGRAM constant."""

    def test_contains_init(self):
        """Diagram contains init command."""
        assert "/cat:init" in WORKFLOW_DIAGRAM

    def test_contains_add(self):
        """Diagram contains add command."""
        assert "/cat:add" in WORKFLOW_DIAGRAM

    def test_contains_work(self):
        """Diagram contains work command."""
        assert "/cat:work" in WORKFLOW_DIAGRAM

    def test_contains_status(self):
        """Diagram contains status command."""
        assert "/cat:status" in WORKFLOW_DIAGRAM

    def test_is_code_block(self):
        """Diagram is wrapped in code block."""
        assert WORKFLOW_DIAGRAM.startswith("```")
        assert WORKFLOW_DIAGRAM.endswith("```")


class TestHierarchyTree:
    """Tests for HIERARCHY_TREE constant."""

    def test_contains_cat_directory(self):
        """Tree shows .claude/cat/ directory."""
        assert ".claude/cat/" in HIERARCHY_TREE

    def test_contains_project_md(self):
        """Tree shows PROJECT.md."""
        assert "PROJECT.md" in HIERARCHY_TREE

    def test_contains_roadmap_md(self):
        """Tree shows ROADMAP.md."""
        assert "ROADMAP.md" in HIERARCHY_TREE

    def test_contains_state_md(self):
        """Tree shows STATE.md."""
        assert "STATE.md" in HIERARCHY_TREE

    def test_contains_plan_md(self):
        """Tree shows PLAN.md."""
        assert "PLAN.md" in HIERARCHY_TREE

    def test_contains_config(self):
        """Tree shows cat-config.json."""
        assert "cat-config.json" in HIERARCHY_TREE

    def test_is_code_block(self):
        """Tree is wrapped in code block."""
        assert HIERARCHY_TREE.startswith("```")
        assert HIERARCHY_TREE.endswith("```")
