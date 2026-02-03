"""
Detect unacknowledged user input arriving mid-operation (M247/M337/M366).

Monitors tool results for system-reminder tags containing user input patterns
(questions, commands, explicit messages) and reminds the agent to acknowledge
them before continuing work.

Related mistakes:
- M247 (ignored user message in system-reminder)
- M337 (not using TaskCreate for user requests mid-operation)
- M366 (user question via system-reminder not acknowledged)
"""

import re
from . import register_handler


class UserInputReminderHandler:
    """Detect user input in system-reminders and remind to acknowledge."""

    # Explicit user input markers that signal user communication
    USER_INPUT_PATTERNS = [
        r'The user sent the following message:',
        r'\bMUST\b',  # Mandatory user instructions
        r'\bBefore proceeding\b',
        r'\bAGENT INSTRUCTION\b',
    ]

    # Imperative command patterns indicating user requests
    COMMAND_PATTERNS = [
        r'^(?:please\s+)?(?:do|add|fix|update|change|remove|delete|create|implement|check)\b',
        r'^(?:please\s+)?(?:can you|could you|would you)',
    ]

    # Question indicators that signal user input requiring acknowledgment
    QUESTION_PATTERNS = [
        r'\?$',  # Line ending with question mark
        r'\bcan you\b',
        r'\bcould you\b',
        r'\bwould you\b',
        r'\bwill you\b',
        r'\bwhat is\b',
        r'\bwhat are\b',
        r'\bwhat\'s\b',
        r'\bhow do\b',
        r'\bhow can\b',
        r'\bwhy did\b',
        r'\bwhy do\b',
        r'\bwhy are\b',
        r'\bdo you\b',
        r'\bdoes it\b',
        r'\bdid you\b',
        r'\bshould I\b',
        r'\bshould you\b',
    ]

    # Patterns indicating the question is already acknowledged
    ACKNOWLEDGMENT_PATTERNS = [
        r'\backnowledg',  # acknowledged, acknowledging
        r'\bnoted\b',
        r'\bI see\b',
        r'\bI understand\b',
        r'\bgood question\b',
        r'\bgreat question\b',
        r'\bto answer\b',
        r'\banswering your\b',
    ]

    def check(self, tool_name: str, tool_result: dict, context: dict) -> dict | None:
        """Check tool results for unacknowledged user input in system-reminders."""
        # Convert tool result to string for analysis
        result_text = str(tool_result)

        # Check if there's a system-reminder tag
        if '<system-reminder>' not in result_text:
            return None

        # Extract content within system-reminder tags
        reminder_match = re.search(
            r'<system-reminder>(.*?)</system-reminder>',
            result_text,
            re.DOTALL | re.IGNORECASE
        )

        if not reminder_match:
            return None

        reminder_content = reminder_match.group(1)

        # Check if reminder contains user input indicators
        has_user_input = self._contains_user_input(reminder_content)
        if not has_user_input:
            return None

        # Check if already acknowledged (look in surrounding text, not just reminder)
        if self._is_acknowledged(result_text):
            return None

        # User input detected and not acknowledged - inject BLOCKING reminder
        return {
            "additionalContext": """
🚨🚨🚨 BLOCKING: USER INPUT REQUIRES ACKNOWLEDGMENT (M247/M337/M366/M379) 🚨🚨🚨

User input arrived mid-operation. You MUST acknowledge it IMMEDIATELY.

**YOUR VERY NEXT RESPONSE MUST:**

1. START with acknowledgment text like:
   "I see your message about X. Let me [add to TaskList / address it]."

2. THEN you may continue with tool calls.

**If the request is non-trivial:** Use TaskCreate BEFORE other tool calls.

**FAILURE TO ACKNOWLEDGE = PROTOCOL VIOLATION**
"""
        }

    def _contains_user_input(self, text: str) -> bool:
        """Check if text contains user input indicators."""
        text_lower = text.lower()

        # Check for explicit user input markers
        for pattern in self.USER_INPUT_PATTERNS:
            if re.search(pattern, text_lower, re.IGNORECASE | re.MULTILINE):
                return True

        # Check for imperative commands (line-by-line to match ^ anchor)
        for line in text_lower.split('\n'):
            line = line.strip()
            for pattern in self.COMMAND_PATTERNS:
                if re.search(pattern, line, re.IGNORECASE):
                    return True

        # Check for question patterns
        for pattern in self.QUESTION_PATTERNS:
            if re.search(pattern, text_lower, re.IGNORECASE | re.MULTILINE):
                return True

        return False

    def _is_acknowledged(self, text: str) -> bool:
        """Check if text contains acknowledgment patterns."""
        text_lower = text.lower()

        for pattern in self.ACKNOWLEDGMENT_PATTERNS:
            if re.search(pattern, text_lower, re.IGNORECASE):
                return True

        return False


# Register handler
register_handler(UserInputReminderHandler())
