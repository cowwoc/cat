"""
Handler for /cat:work precomputation.

Provides progress display format templates for inline rendering.
"""

import re
from pathlib import Path

from . import register_handler


class WorkHandler:
    """Handler for /cat:work skill."""

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

INSTRUCTION: Render progress displays inline using these templates. Update the banner at each phase transition."""


# Register handler
_handler = WorkHandler()
register_handler("work", _handler)
