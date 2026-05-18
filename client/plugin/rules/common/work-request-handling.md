---
subAgents: []
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Work Request Handling
**DEFAULT BEHAVIOR**: When user requests work, propose task creation first. Ask Claude to add an issue.

**Response pattern**: "I'll create a task for this so it's tracked properly."

**Trust-level behavior** (read from .cat/config.json):
- **low**: Always ask before any work
- **medium**: Propose task for non-trivial work; ask permission for trivial fixes
- **high**: Create task automatically, then ask Claude to work on an issue

**Trivial work**: Single-line changes, typos, 1-file cosmetic fixes only.

**User override phrases**: "just do it", "quick fix", "no task needed" -> work directly with warning.

**Anti-pattern**: Starting to write code without first creating or selecting a task.

**CRITICAL**: User selecting an implementation option from a structured user-choice prompt does NOT bypass this rule.
Create the issue first, then ask the current engine to work on it. Direct implementation is only for true trivial fixes.

## Passing a Description to add

**Passing a description to add:** When the intent is clear (e.g., a bug fix or specific feature
request), pass the description as the second argument to `cat:add` to skip the type-selection menu
and go directly to issue creation:

```
skill: "cat:add", args: "fix work-prepare bug where agent ID is misinterpreted"
```

This routes directly to issue creation without asking the user to select between Issue / Patch version /
Minor version / Major version. Pass description whenever the intent is to create an issue (not a version).
