"""
Verbatim output skills handler.

M407/M411: Certain skills require silent invocation and verbatim output.
This handler uses user-centric framing to align with LLM helpful training:
- "The user expects..." triggers helpful compliance
- Short, direct instructions avoid analytical mode

Skills with verbatim output requirements:
- /cat:status - silent invocation, copy status box exactly
- /cat:help - silent invocation, copy help box exactly
"""

import re
from . import register_handler

# Skills that require verbatim copy-paste of their rendered output
VERBATIM_OUTPUT_SKILLS = {
    "status": "status box with issue lists",
    "help": "help box with command reference",
}

VERBATIM_REMINDER_TEMPLATE = """The user expects you to invoke `/cat:{skill}` silently and display its output verbatim.

**Silent invocation:** Call the Skill tool immediately without announcing what you're doing.
**Verbatim output:** Copy-paste the {description} exactly as rendered, including all box borders."""


class VerbatimOutputHandler:
    """Inject verbatim output reminder for skills that require copy-paste."""

    def check(self, prompt: str, session_id: str) -> str | None:
        """Check if prompt invokes a verbatim output skill."""
        # Match /cat:skillname or cat:skillname
        match = re.match(r'^[\s]*/?(cat:([a-z-]+))(?:\s|$)', prompt, re.IGNORECASE)
        if not match:
            return None

        skill_name = match.group(2).lower()

        if skill_name in VERBATIM_OUTPUT_SKILLS:
            description = VERBATIM_OUTPUT_SKILLS[skill_name]
            return VERBATIM_REMINDER_TEMPLATE.format(
                skill=skill_name,
                description=description
            )

        return None


# Register handler
register_handler(VerbatimOutputHandler())
