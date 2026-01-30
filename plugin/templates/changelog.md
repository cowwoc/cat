# Changelog: {version-name}

> **NOTE**: This template is for MINOR and MAJOR version changelogs only.
> Issue-level changelogs are NOT created as separate files - issue changelog content
> is embedded in commit messages instead.

**Completed**: {YYYY-MM-DD}

## Summary

[One-line description of what this version accomplished]

## Issues Completed

| Issue | Type | Description | Resolution |
|-------|------|-------------|------------|
| {issue-name} | {type} | {brief description} | implemented |
| {dup-issue} | - | {what it duplicated} | duplicate of {orig-issue} |

**Resolution types:**
- `implemented` - Issue completed normally with its own commit
- `duplicate of {issue}` - Work done by another issue
- `obsolete` - Issue no longer needed

## Key Changes

[High-level summary of the changes across all issues]

- {Change 1}
- {Change 2}

## Files Changed

[Aggregate of files created/modified across all issues]

### Created
- `{path/to/NewFile.java}` - {purpose}

### Modified
- `{path/to/ExistingFile.java}` - {what changed}

## Technical Highlights

[Notable technical decisions or patterns established across issues]

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

# For specific issue within the version:
git log --oneline --grep="Issue ID: v1.0-{issue-name}"
```

### Finding Commits for Duplicate Issues

Duplicate issues have no commit with their own Issue ID. To find the resolving commit:

1. Check the issue's STATE.md for the `Duplicate Of` field
2. Search for that original issue's ID:

```bash
# If issue-b is duplicate of v1.0-issue-a:
git log --oneline --grep="Issue ID: v1.0-issue-a"
```

See [issue-resolution.md](../concepts/issue-resolution.md) for details.
