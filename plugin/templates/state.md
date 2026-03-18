{
  "status": "open",
  "dependencies": [],
  "blocks": []
}

## Mandatory Fields

| Field | When Required | Values |
|-------|---------------|--------|
| `status` | Always | `open`, `in-progress`, `blocked`, or `closed` |
| `dependencies` | Always | Array of issue slugs this depends on; use `[]` if none |
| `blocks` | Always | Array of issue slugs this blocks; use `[]` if none |
| `resolution` | Closed only | `implemented`, `duplicate`, `obsolete`, `won't-fix`, or `not-applicable` |
| `targetBranch` | Optional | The branch this issue merges into (e.g., `v2.1`, `main`) |
| `parent` | Sub-issues only | Parent issue slug |
| `decomposedInto` | Decomposed issues only | Array of sub-issue slugs |

## Resolution Patterns

### Standard Completion (implemented)
```json
{
  "status": "closed",
  "resolution": "implemented",
  "dependencies": ["prerequisite-issue"],
  "blocks": []
}
```

Changes are tracked via git file history. To find implementation commits:

```bash
git log --oneline -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/
```

### Duplicate Issue
```json
{
  "status": "closed",
  "resolution": "duplicate (v{major}.{minor}-{original-issue-name})",
  "dependencies": ["shared-dependency"],
  "blocks": []
}
```

### Obsolete Issue
```json
{
  "status": "closed",
  "resolution": "obsolete ({why issue is no longer needed})",
  "dependencies": [],
  "blocks": []
}
```

### No Code Changes Needed
```json
{
  "status": "closed",
  "resolution": "implemented",
  "dependencies": [],
  "blocks": []
}
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
| Problem analysis, approach | plan.md | Initial planning |
| Solution summary, changes | Commit message | What was implemented |
| Dependencies | index.json | Issue ordering |
| Duplicate/obsolete explanation | index.json `resolution` field | Why resolution was chosen |

## Finding Commits

Implementation commits are tracked via index.json file history:

```bash
# Find all commits for this issue
git log --oneline -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/

# Find the completion commit
git log --oneline -1 -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/index.json

# View full implementation history
git log -p -- .cat/issues/v{X}/v{X}.{Y}/{issue-name}/index.json
```

For duplicate issues, find the original issue's commits using its path.
