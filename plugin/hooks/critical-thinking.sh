"""
Critical thinking reminder handler.

Always injects the critical thinking requirements reminder.
"""

from . import register_handler

CRITICAL_THINKING_REMINDER = """## 🧠 CRITICAL THINKING REQUIREMENTS

**MANDATORY**: Apply evidence-based critical thinking
- FIRST gather evidence through investigation, testing, or research
- THEN identify flaws, edge cases, or counter-examples based on that evidence
- Propose alternative approaches only when evidence shows they're superior
- Avoid challenging assumptions without supporting evidence
- If user approach is sound, state specific technical reasons for agreement

**EXAMPLES OF CRITICAL ANALYSIS:**

Instead of: "That's a good approach"
Use: "That approach addresses the immediate issue. Based on testing X, I can confirm it works. However, there's a potential edge case Y that we should consider because Z."

Instead of: "You're absolutely right"
Use: "The core logic is sound. My investigation shows X evidence supporting this. However, we should also consider scenario Y where this might need adjustment."

**APPLY TO CURRENT PROMPT**: Gather evidence first, then provide critical analysis based on that evidence."""


class CriticalThinkingHandler:
    """Always inject critical thinking reminder."""

    def check(self, prompt: str, session_id: str) -> str | None:
        """Always return the critical thinking reminder."""
        return CRITICAL_THINKING_REMINDER


# Register handler
register_handler(CriticalThinkingHandler())
