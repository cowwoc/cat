# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []

## Mandatory Header Fields

| Field | When Required | Description |
|-------|---------------|-------------|
| `Status` | Always | `open`, `in-progress`, `blocked`, or `closed` |
| `Progress` | Always | `0%` for open, `100%` for closed |
| `Dependencies` | Always | Issue names this depends on; use `[]` if none. When decomposed, include all sub-issues |
| `Blocks` | Always | Issue names this blocks; use `[]` if none |
| `Resolution` | Closed only | `implemented`, `duplicate`, `obsolete`, `won't-fix`, or `not-applicable` |
| `Parent` | Sub-issues only | Parent issue slug |

## Resolution Patterns

### Standard Completion (implemented)
```yaml
- **Status:** closed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** [prerequisite-issue]
- **Blocks:** []

## Implementation

Changes are tracked via git file history. To find implementation commits:

git log --oneline -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md
```

### Duplicate Issue
```yaml
- **Status:** closed
- **Progress:** 100%
- **Resolution:** duplicate (v{major}.{minor}-{original-issue-name})
- **Dependencies:** [shared-dependency]
- **Blocks:** []

## Reason

Explain WHY this is a duplicate - what investigation revealed.

## Verification

List scenarios tested to confirm the duplicate issue's fix covers this case:
- Scenario A ✓
- Scenario B ✓
```

### Obsolete Issue
```yaml
- **Status:** closed
- **Progress:** 100%
- **Resolution:** obsolete ({why issue is no longer needed})
- **Dependencies:** []
- **Blocks:** []
```

### No Code Changes Needed
```yaml
- **Status:** closed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** []
- **Blocks:** []

## Resolution

**VERIFIED**: Explain why no code changes were required.

## Existing Test Coverage

List tests that already cover the functionality (if applicable).

## Implementation

No code changes needed. Find verification commit via:

git log --oneline -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md
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
| `implemented` | Issue closed (with or without code changes) | Yes if code changed |
| `duplicate` | Another issue already did this work | No - reference other issue |
| `obsolete` | Issue no longer needed (requirements changed) | No |
| `won't-fix` | Issue intentionally not implemented | No |

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
git log --oneline -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/

# Find the completion commit
git log --oneline -1 -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md

# View full implementation history
git log -p -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/STATE.md
```

For duplicate issues, find the original issue's commits using its path.
