"""
PostToolUse handler for Skill tool preprocessor output.

When the agent invokes a CAT skill via the Skill tool (rather than the user
typing /cat:* directly), this handler generates the preprocessor output
that the skill expects to find in context.

This solves the timing issue where:
- User types "/cat:status" → UserPromptSubmit generates preprocessor output
- Agent invokes Skill(skill="cat:status") → UserPromptSubmit already fired
  for original user message, no preprocessor output was generated

By using PostToolUse, we inject the preprocessor output alongside the skill
content that the Skill tool returns.
"""

import os
import re
import sys
from pathlib import Path

from . import register_handler

# Add skill_handlers to path for reusing handlers
SCRIPT_DIR = Path(__file__).parent.parent
sys.path.insert(0, str(SCRIPT_DIR))

from skill_handlers import get_handler as get_skill_handler


class SkillPreprocessorOutputHandler:
    """Handler that generates preprocessor output for CAT skills invoked via Skill tool."""

    def check(self, tool_name: str, tool_result: dict, context: dict) -> dict | None:
        """
        Check if this is a Skill tool invocation for a CAT skill.

        Args:
            tool_name: The tool that was executed
            tool_result: The tool's result dict
            context: Dict with session_id, hook_data

        Returns:
            {"additionalContext": "..."} with preprocessor output, or None
        """
        if tool_name != "Skill":
            return None

        # Get skill name from tool_input
        hook_data = context.get("hook_data", {})
        tool_input = hook_data.get("tool_input", {})
        skill_name = tool_input.get("skill", "")

        if not skill_name:
            return None

        # Extract CAT skill name (e.g., "cat:status" -> "status")
        cat_skill_name = self._extract_cat_skill_name(skill_name)
        if not cat_skill_name:
            return None

        # Get the handler for this skill
        handler = get_skill_handler(cat_skill_name)
        if not handler:
            return None

        # Determine project root
        project_root = os.environ.get("CLAUDE_PROJECT_DIR", "")
        if not project_root or not Path(project_root, ".claude", "cat").is_dir():
            if Path(".claude/cat").is_dir():
                project_root = os.getcwd()
            else:
                return None

        # Build context for skill handler
        skill_context = {
            "user_prompt": f"/cat:{cat_skill_name}",
            "session_id": context.get("session_id", ""),
            "project_root": project_root,
            "plugin_root": str(SCRIPT_DIR.parent),
            "hook_data": hook_data,
        }

        try:
            result = handler.handle(skill_context)
            if result:
                return {"additionalContext": result}
        except Exception as e:
            sys.stderr.write(f"skill_preprocessor_output: handler error for {cat_skill_name}: {e}\n")

        return None

    def _extract_cat_skill_name(self, skill_name: str) -> str | None:
        """
        Extract CAT skill name from skill parameter.

        Matches patterns like:
        - "cat:status" -> "status"
        - "cat:work" -> "work"
        - "some-other-skill" -> None (not a CAT skill)
        """
        match = re.match(r'^cat:([a-z-]+)$', skill_name, re.IGNORECASE)
        if match:
            return match.group(1).lower()
        return None


# Register handler
register_handler(SkillPreprocessorOutputHandler())
