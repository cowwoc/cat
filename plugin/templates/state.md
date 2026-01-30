# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** {{TIMESTAMP}}

## Mandatory Header Fields

| Field | When Required | Description |
|-------|---------------|-------------|
| `Status` | Always | `pending`, `in_progress`, or `completed` |
| `Progress` | Always | `0%` for pending, `100%` for completed |
| `Dependencies` | Always | Task names this depends on; use `[]` if none |
| `Last Updated` | Pending only | Date of last state change |
| `Resolution` | Completed only | `implemented`, `duplicate`, or `obsolete` |
| `Completed` | Completed only | Completion date |
| `Duplicate Of` | Duplicate only | Issue ID that implemented the fix |
| `Reason` | Obsolete only | Why task is no longer needed |

## Resolution Patterns

### Standard Completion (implemented)
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** [prerequisite-task]
- **Completed:** {{TIMESTAMP}}
```

### Duplicate Task
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v{major}.{minor}-{original-task-name}
- **Dependencies:** [shared-dependency]
- **Completed:** {{TIMESTAMP}}

## Resolution Details

Explain WHY this is a duplicate - what investigation revealed.

## Verification

List scenarios tested to confirm the duplicate task's fix covers this case:
- Scenario A ✓
- Scenario B ✓
```

### Obsolete Task
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** obsolete
- **Reason:** {why task is no longer needed}
- **Completed:** {{TIMESTAMP}}
```

### No Code Changes Needed
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Completed:** {{TIMESTAMP}}

## Resolution

**VERIFIED**: Explain why no code changes were required.

## Existing Test Coverage

List tests that already cover the functionality (if applicable).
```

## Optional Sections

Add these sections ONLY when they provide unique value not captured elsewhere.

### Investigation Updates (pending tasks)

Use when investigation reveals new information AFTER PLAN.md was written:

```markdown
## Error Pattern (UPDATED {{DATE}})

Details discovered during investigation...

## Root Cause

Technical explanation of the issue...
```

**When to use**: Only when findings differ from or extend PLAN.md content.
Avoid duplicating information already in PLAN.md.

### Verification Results (gate/validation tasks)

```markdown
## Previous Run ({{DATE}})

**Result:** X% success rate (N/M files)

Summary of what was tested and what needs to be fixed.
```

## Resolution Types

| Resolution | When to Use | Commit? |
|------------|-------------|---------|
| `implemented` | Task completed (with or without code changes) | Yes if code changed |
| `duplicate` | Another task already did this work | No - reference other task |
| `obsolete` | Task no longer needed (requirements changed) | No |

## What Belongs Where

| Information | Location | Notes |
|-------------|----------|-------|
| Problem analysis, approach | PLAN.md | Initial planning |
| Solution summary, changes | Commit message | What was implemented |
| Dependencies | STATE.md | Task ordering |
| Investigation findings | STATE.md | Only if discovered AFTER plan |
| Duplicate/obsolete explanation | STATE.md | Why resolution was chosen |
| Verification results | STATE.md | Proof that resolution is valid |
| Test coverage evidence | STATE.md | For "no changes needed" cases |

## Finding Commits

For implemented tasks:
```bash
git log --oneline --grep="Issue ID: v{major}.{minor}-{task-name}"
```

For duplicate tasks, search for the original task's ID:
```bash
git log --oneline --grep="Issue ID: v{major}.{minor}-{original-task-name}"
```
