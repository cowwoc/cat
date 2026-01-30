# Changelog: {version-name}

> **NOTE**: This template is for MINOR and MAJOR version changelogs only.
> Task-level changelogs are NOT created as separate files - task changelog content
> is embedded in commit messages instead.

**Completed**: {YYYY-MM-DD}

## Summary

[One-line description of what this version accomplished]

## Tasks Completed

| Task | Type | Description | Resolution |
|------|------|-------------|------------|
| {task-name} | {type} | {brief description} | implemented |
| {dup-task} | - | {what it duplicated} | duplicate of {orig-task} |

**Resolution types:**
- `implemented` - Task completed normally with its own commit
- `duplicate of {task}` - Work done by another task
- `obsolete` - Task no longer needed

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
git log --oneline --grep="Issue ID: v1.0-"

# For specific task within the version:
git log --oneline --grep="Issue ID: v1.0-{task-name}"
```

### Finding Commits for Duplicate Tasks

Duplicate tasks have no commit with their own Issue ID. To find the resolving commit:

1. Check the task's STATE.md for the `Duplicate Of` field
2. Search for that original task's ID:

```bash
# If task-b is duplicate of v1.0-task-a:
git log --oneline --grep="Issue ID: v1.0-task-a"
```

See [task-resolution.md](../concepts/task-resolution.md) for details.
