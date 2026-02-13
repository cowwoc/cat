# Changelog: {version-name}

> **NOTE**: This template is for MINOR and MAJOR version changelogs only.
> Task-level changelogs are NOT created as separate files - task changelog content
> is embedded in commit messages instead.

**Completed**: {YYYY-MM-DD}

## Summary

[One-line description of what this version accomplished]

## Tasks Completed

| Task | Type | Description |
|------|------|-------------|
| {task-name} | {type} | {brief description} |

## Key Changes

[High-level summary of the changes across all tasks]

- {Change 1}
- {Change 2}

## Files Changed

[Aggregate of files created/modified across all tasks]

### Created
- `{path/to/NewFile.java}` - {purpose}

### Modified
- `{path/to/ExistingFile.java}` - {what changed}

## Technical Highlights

[Notable technical decisions or patterns established across tasks]

- {Highlight 1}
- {Highlight 2}

## Quality

- {Total tests added/modified}
- {Build status}
- {Other quality metrics}

---

## Related Commits

Find all commits for this version:

```bash
# For minor version 1.0:
git log --oneline --grep="Task ID: v1.0-"

# For specific task within the version:
git log --oneline --grep="Task ID: v1.0-{task-name}"
```
