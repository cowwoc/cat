# Changelog: v1.3 - Maintenance Release

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-13

**Maintenance Release**

Quick maintenance release with file renames and a critical bugfix.

## Improvements

- Renamed VERSION.md to CHANGELOG.md for clarity
- Merged v1.2 features into main branch

## Bugfixes

- Fix SESSION_ID usage in skills (M058) - skills now correctly instruct agents to read SESSION_ID from conversation context (SessionStart system-reminder) instead of expecting a shell environment variable

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 1 issue

```bash
git log --oneline --grep="Issue ID: v1.3-"
```
