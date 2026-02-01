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
| `Dependencies` | Always | Issue names this depends on; use `[]` if none |
| `Last Updated` | Pending only | Date of last state change |
| `Resolution` | Completed only | `implemented`, `duplicate`, or `obsolete` |
| `Completed` | Completed only | Completion date |
| `Duplicate Of` | Duplicate only | Issue ID that implemented the fix |
| `Reason` | Obsolete only | Why issue is no longer needed |

## Resolution Patterns

### Standard Completion (implemented)
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** [prerequisite-issue]
- **Completed:** {{TIMESTAMP}}

## Implementation

Changes are tracked via git file history. To find implementation commits:

git log --oneline -- .claude/cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md
```

### Duplicate Issue
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v{major}.{minor}-{original-issue-name}
- **Dependencies:** [shared-dependency]
- **Completed:** {{TIMESTAMP}}

## Resolution Details

Explain WHY this is a duplicate - what investigation revealed.

## Verification

List scenarios tested to confirm the duplicate issue's fix covers this case:
- Scenario A ✓
- Scenario B ✓
```

### Obsolete Issue
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** obsolete
- **Reason:** {why issue is no longer needed}
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

## Implementation

No code changes needed. Find verification commit via:

git log --oneline -- .claude/cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md
```

## Optional Sections

Add these sections ONLY when they provide unique value not captured elsewhere.

### Investigation Updates (pending issues)

Use when investigation reveals new information AFTER PLAN.md was written:

```markdown
## Error Pattern (UPDATED {{DATE}})

Details discovered during investigation...

## Root Cause

Technical explanation of the issue...
```

**When to use**: Only when findings differ from or extend PLAN.md content.
Avoid duplicating information already in PLAN.md.

### Verification Results (gate/validation issues)

```markdown
## Previous Run ({{DATE}})

**Result:** X% success rate (N/M files)

Summary of what was tested and what needs to be fixed.
```

## Resolution Types

| Resolution | When to Use | Commit? |
|------------|-------------|---------|
| `implemented` | Issue completed (with or without code changes) | Yes if code changed |
| `duplicate` | Another issue already did this work | No - reference other issue |
| `obsolete` | Issue no longer needed (requirements changed) | No |

## What Belongs Where

| Information | Location | Notes |
|-------------|----------|-------|
| Problem analysis, approach | PLAN.md | Initial planning |
| Solution summary, changes | Commit message | What was implemented |
| Dependencies | STATE.md | Issue ordering |
| Investigation findings | STATE.md | Only if discovered AFTER plan |
| Duplicate/obsolete explanation | STATE.md | Why resolution was chosen |
| Verification results | STATE.md | Proof that resolution is valid |
| Test coverage evidence | STATE.md | For "no changes needed" cases |

## Finding Commits

Implementation commits are tracked via STATE.md file history:

```bash
# Find all commits for this issue
git log --oneline -- .claude/cat/issues/v{X}/v{X}.{Y}/{issue-name}/

# Find the completion commit
git log --oneline -1 -- .claude/cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md

# View full implementation history
git log -p -- .claude/cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md
```

For duplicate issues, find the original issue's commits using its path.
