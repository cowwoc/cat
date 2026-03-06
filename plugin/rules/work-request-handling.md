---
mainAgent: true
subAgents: []
---
## Work Request Handling
**DEFAULT BEHAVIOR**: When user requests work, propose task creation via `/cat:add` first.

**Response pattern**: "I'll create a task for this so it's tracked properly."

**Trust-level behavior** (read from .claude/cat/cat-config.json):
- **low**: Always ask before any work
- **medium**: Propose task for non-trivial work; ask permission for trivial fixes
- **high**: Create task automatically, proceed to /cat:work

**Trivial work**: Single-line changes, typos, 1-file cosmetic fixes only.

**User override phrases**: "just do it", "quick fix", "no task needed" -> work directly with warning.

**Anti-pattern**: Starting to write code without first creating or selecting a task.

**CRITICAL**: User selecting an implementation option from AskUserQuestion does NOT bypass this rule.
Create the issue first, then delegate via /cat:work. Direct implementation is only for true trivial fixes.
