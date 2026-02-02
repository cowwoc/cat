"""
Detect subagent validation fabrication (A001/PATTERN-001).

Monitors Task tool results for validation scores that appear without
evidence of /compare-docs skill invocation, indicating fabricated
validation claims.

Uses robust detection by checking the subagent's actual session file
for Skill tool invocations, not just text pattern matching in output.

Related escalation: ESCALATE-A001
Related mistake: M346 (no example values in output format)
"""

import re
from pathlib import Path
from . import register_handler


class DetectValidationFabricationHandler:
    """Detect fabricated validation scores and warn."""

    # Patterns that indicate validation scores
    SCORE_PATTERNS = [
        r'(?:equivalence[_\s]?score|score)[:\s]+[01]\.\d+',
        r'"equivalence_score"[:\s]+[01]\.\d+',
        r'equivalence[:\s]+[01]\.\d+',
    ]

    # Skills that legitimately produce validation scores
    VALIDATION_SKILLS = [
        "compare-docs",
        "shrink-doc",
    ]

    def check(self, tool_name: str, tool_result: dict, context: dict) -> dict | None:
        """Check Task tool results for fabricated validation scores."""
        # Only check Task tool results
        if tool_name != "Task":
            return None

        # Get result text
        result_text = str(tool_result)

        # Check for validation score patterns
        has_scores = self._contains_validation_scores(result_text)
        if not has_scores:
            return None

        # Extract agentId from the Task result
        agent_id = self._extract_agent_id(result_text)

        # Get session_id from context
        session_id = context.get("session_id", "")

        # Try robust verification via subagent session file
        if agent_id and session_id:
            has_skill_invocation = self._check_subagent_session(session_id, agent_id)
            if has_skill_invocation:
                return None  # Verified - skill was actually invoked

        # Fallback: check text patterns in output (less reliable)
        has_skill_evidence = self._check_skill_invocation_in_output(result_text)

        if not has_skill_evidence:
            return {
                "additionalContext": """⚠️ POTENTIAL VALIDATION FABRICATION DETECTED (A001/PATTERN-001)

The subagent result contains validation scores but there's no evidence of /compare-docs skill invocation.

**Problem**: Subagents may fabricate validation scores instead of actually invoking validation skills, especially when output format examples contain expected values.

**Required action**:
1. Do NOT trust these validation scores
2. Re-run the work with explicit instruction to invoke /compare-docs skill
3. Verify actual skill output in session history

**Reference**: ESCALATE-A001, M346 (no example values in output format)"""
            }

        return None

    def _contains_validation_scores(self, text: str) -> bool:
        """Check if text contains validation score patterns."""
        for pattern in self.SCORE_PATTERNS:
            if re.search(pattern, text, re.IGNORECASE):
                return True
        return False

    def _extract_agent_id(self, text: str) -> str | None:
        """Extract agentId from Task tool result.

        The agentId appears in Task results as:
        - "agentId":"abc1234" (JSON format)
        - agentId: abc1234 (text format)
        """
        # Try JSON format first
        match = re.search(r'"agentId"\s*:\s*"([a-f0-9]+)"', text)
        if match:
            return match.group(1)

        # Try text format
        match = re.search(r'agentId:\s*([a-f0-9]+)', text)
        if match:
            return match.group(1)

        return None

    def _check_subagent_session(self, parent_session_id: str, agent_id: str) -> bool:
        """Check the subagent's session file for actual Skill tool invocations.

        Subagent sessions are stored at:
        ~/.config/claude/projects/-workspace/{parent-session-id}/subagents/agent-{agentId}.jsonl
        """
        hist_dir = Path.home() / ".config" / "claude" / "projects" / "-workspace"
        subagent_file = hist_dir / parent_session_id / "subagents" / f"agent-{agent_id}.jsonl"

        if not subagent_file.exists():
            return False  # Can't verify - fall back to text matching

        try:
            content = subagent_file.read_text()

            # Check for Skill tool invocations
            if '"name":"Skill"' not in content and '"name": "Skill"' not in content:
                return False  # No Skill tool used at all

            # Check for validation skill invocations
            for skill in self.VALIDATION_SKILLS:
                if f'"skill":"{skill}"' in content or f'"skill": "{skill}"' in content:
                    return True

            return False
        except Exception:
            return False  # Can't verify - fall back to text matching

    def _check_skill_invocation_in_output(self, output: str) -> bool:
        """Check if the subagent's output contains evidence of skill invocation.

        This is a fallback when we can't access the subagent's session file.
        Less reliable than _check_subagent_session because the subagent
        could mention a skill without actually invoking it.
        """
        output_lower = output.lower()

        for skill in self.VALIDATION_SKILLS:
            # Check for skill invocation patterns in the subagent's output
            if f"/cat:{skill}" in output:
                return True  # Subagent mentioned invoking the skill
            if f'skill": "{skill}"' in output or f"skill: {skill}" in output:
                return True  # Subagent mentioned invoking the skill
            if f"invoke {skill}" in output_lower or f"invoked {skill}" in output_lower:
                return True  # Subagent mentioned invoking the skill
            if f"running {skill}" in output_lower or f"ran {skill}" in output_lower:
                return True  # Subagent mentioned running the skill

        return False


# Register handler
register_handler(DetectValidationFabricationHandler())
