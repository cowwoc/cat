"""
Handler for /cat:work precomputation.

Provides progress display format templates for inline rendering.
"""

import re
from pathlib import Path

from . import register_handler
from .status_handler import display_width, build_line, build_border


def build_separator(max_width: int) -> str:
    """Build a horizontal separator line (├───┤)."""
    dash_count = max_width + 2
    dashes = "─" * dash_count
    return "├" + dashes + "┤"


def build_header_top(header: str, max_width: int) -> str:
    """Build top border with embedded header (╭─── Header ───╮)."""
    inner_width = max_width + 2
    header_width = display_width(header)
    prefix_dashes = "─── "  # 4 chars
    suffix_dashes_count = inner_width - 4 - header_width - 1
    if suffix_dashes_count < 1:
        suffix_dashes_count = 1
    suffix_dashes = "─" * suffix_dashes_count
    return "╭" + prefix_dashes + header + " " + suffix_dashes + "╮"


class WorkHandler:
    """Handler for /cat:work skill."""

    def _build_task_complete_with_next(self, box_width: int) -> str:
        """Build Task Complete box for auto-continue mode (trust >= medium)."""
        header = "✓ Task Complete"

        content_lines = [
            "",
            "**{task-name}** merged to main.",
            "",
        ]

        separator_content = [
            "**Next:** {next-task-name}",
            "{goal from PLAN.md}",
            "",
            "Auto-continuing in 3s...",
            "• Type \"stop\" to pause after this task",
            "• Type \"abort\" to cancel immediately",
        ]

        footer_content = [""]

        lines = []
        lines.append(build_header_top(header, box_width))
        for content in content_lines:
            lines.append(build_line(content, box_width))
        lines.append(build_separator(box_width))
        for content in separator_content:
            lines.append(build_line(content, box_width))
        lines.append(build_separator(box_width))
        for content in footer_content:
            lines.append(build_line(content, box_width))
        lines.append(build_border(box_width, is_top=False))

        return "\n".join(lines)

    def _build_scope_complete(self, box_width: int) -> str:
        """Build Scope Complete box."""
        header = "✓ Scope Complete"

        content_lines = [
            "",
            "**{scope description}** - all tasks complete!",
            "",
            "{For minor: \"v0.5 complete\"}",
            "{For major: \"v0.x complete\"}",
            "{For all: \"All versions complete!\"}",
            "",
        ]

        lines = []
        lines.append(build_header_top(header, box_width))
        for content in content_lines:
            lines.append(build_line(content, box_width))
        lines.append(build_border(box_width, is_top=False))

        return "\n".join(lines)

    def _build_task_complete_low_trust(self, box_width: int) -> str:
        """Build Task Complete box for low trust (user must invoke)."""
        header = "✓ Task Complete"

        content_lines = [
            "",
            "**{task-name}** merged to main.",
            "",
        ]

        separator_content = [
            "**Next Up:** {next-task-name}",
            "{goal from PLAN.md}",
            "",
            "`/cat:work` to continue",
        ]

        footer_content = [""]

        lines = []
        lines.append(build_header_top(header, box_width))
        for content in content_lines:
            lines.append(build_line(content, box_width))
        lines.append(build_separator(box_width))
        for content in separator_content:
            lines.append(build_line(content, box_width))
        lines.append(build_separator(box_width))
        for content in footer_content:
            lines.append(build_line(content, box_width))
        lines.append(build_border(box_width, is_top=False))

        return "\n".join(lines)

    def handle(self, context: dict) -> str | None:
        """Provide progress format templates for the work skill."""
        user_prompt = context.get("user_prompt", "")

        # Extract task ID from prompt if provided (e.g., "/cat:work 2.0-task-name")
        task_id = ""
        match = re.search(r'/cat:work\s+(\S+)', user_prompt)
        if match:
            task_id = match.group(1)

        return f"""PRE-COMPUTED WORK PROGRESS FORMAT:

## Progress Display Templates

Use these templates directly in your output. Do NOT call any external scripts.

### Header Format (display at workflow start)

```
🐱 > {{TASK_ID}}
────────────────────────────────────────────────────────────────────
```

### Progress Banner Format (update at each phase transition)

```
{{P1}} Preparing ────── {{P2}} Executing ────── {{P3}} Reviewing ────── {{P4}} Merging
                          {{METRICS}}
```

### Phase Symbols

| Symbol | Code | Meaning |
|--------|------|---------|
| ○ | Pending | Phase not started |
| ● | Complete | Phase finished |
| ◉ | Active | Currently in this phase |
| ✗ | Failed | Phase failed |

### Example Transitions

**Starting (Preparing active):**
```
🐱 > 2.0-fix-config-documentation
────────────────────────────────────────────────────────────────────

◉ Preparing ────── ○ Executing ────── ○ Reviewing ────── ○ Merging
```

**Executing with metrics:**
```
🐱 > 2.0-fix-config-documentation
────────────────────────────────────────────────────────────────────

● Preparing ────── ◉ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

**Reviewing with metrics:**
```
🐱 > 2.0-fix-config-documentation
────────────────────────────────────────────────────────────────────

● Preparing ────── ● Executing ────── ◉ Reviewing ────── ○ Merging
                      75K · 3 commits
```

**Passed (success):**
```
🐱 > 2.0-fix-config-documentation > PASSED
────────────────────────────────────────────────────────────────────

● Preparing ────── ● Executing ────── ● Reviewing ────── ● Merging
                      75K · 3 commits    approved            → main
```

**Failed:**
```
🐱 > 2.0-fix-config-documentation > FAILED
────────────────────────────────────────────────────────────────────

● Preparing ────── ● Executing ────── ✗ Reviewing ────── ○ Merging
                      75K · 3 commits    BLOCKED: security
```

INSTRUCTION: Render progress displays inline using these templates. Update the banner at each phase transition.

PRE-COMPUTED WORK BOXES (copy exactly when rendering):

--- TASK_COMPLETE_WITH_NEXT_TASK ---
{self._build_task_complete_with_next(58)}

--- SCOPE_COMPLETE ---
{self._build_scope_complete(58)}

--- TASK_COMPLETE_LOW_TRUST ---
{self._build_task_complete_low_trust(58)}

INSTRUCTION: Use the appropriate pre-computed box above. Replace placeholders ({{task-name}}, {{next-task-name}}, {{goal from PLAN.md}}, {{scope description}}) with actual values but keep box structure exact. Maintain spacing and alignment."""


# Register handler
_handler = WorkHandler()
register_handler("work", _handler)
