"""
Verbatim output skills reminder handler.

M407: Certain skills require verbatim copy-paste of pre-rendered output.
This handler detects invocation of those skills and injects a prominent
reminder at the START of processing (before the agent responds), rather
than relying on Stop hook (M402) which fires too late if user continues.

Skills with verbatim output requirements:
- /cat:status - must copy status box exactly
- /cat:help - must copy help box exactly
"""

import re
from . import register_handler

# Skills that require verbatim copy-paste of their rendered output
VERBATIM_OUTPUT_SKILLS = {
    "status": "status box with issue lists",
    "help": "help box with command reference",
}

VERBATIM_REMINDER_TEMPLATE = """## ⚠️ VERBATIM OUTPUT REQUIRED (M407)

The skill you're about to execute (`/cat:{skill}`) has pre-rendered output that MUST be copied verbatim.

**DO NOT:**
- Summarize the output
- Interpret or reformat it
- Add your own commentary before/around the box
- Describe what the output shows

**DO:**
- Copy-paste the ENTIRE {description} exactly as shown
- Include all box borders (╭── ╰──)
- Preserve all formatting

The MANDATORY OUTPUT REQUIREMENT in the skill is not a suggestion - it is a blocking requirement.
Your response MUST contain the rendered box characters or the Stop hook (M402) will block you."""


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
