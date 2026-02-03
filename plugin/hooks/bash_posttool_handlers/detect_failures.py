"""
Detect command failures and suggest learning from mistakes.

Trigger: PostToolUse for Bash
"""

import re
from . import register_handler


FAILURE_PATTERNS = re.compile(
    r"BUILD FAILED|FAILED|ERROR:|error:|Exception|FATAL|fatal:",
    re.IGNORECASE
)


class DetectFailuresHandler:
    """Detect bash command failures."""

    def check(self, command: str, context: dict) -> dict | None:
        tool_result = context.get("tool_result", {})

        # Get exit code
        exit_code = tool_result.get("exit_code")
        if exit_code is None:
            exit_code = tool_result.get("exitCode", 0)

        # Skip if successful
        if exit_code == 0:
            return None

        # Get output
        stdout = tool_result.get("stdout", "") or ""
        stderr = tool_result.get("stderr", "") or ""
        output = stdout + stderr

        # Check for failure patterns
        if FAILURE_PATTERNS.search(output):
            return {
                "warning": f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️  Failure detected (exit code: {exit_code})
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Consider:
1. Fix the immediate issue
2. If this could recur, use learn skill
   to implement prevention

See: .claude/skills/learn/SKILL.md"""
            }

        return None


register_handler(DetectFailuresHandler())
