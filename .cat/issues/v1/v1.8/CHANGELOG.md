# Changelog: v1.8 - Version Migration

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-15

**Version Migration & Workflow Stability**

Introduces automated version migration system and workflow stability improvements.

## New Features

- **Version Migration System**: Automated migrations for CAT upgrades with backup/restore
- **Version Entry/Exit Gates**: Pre/post-upgrade validation for safe version transitions
- **Duplicate Detection**: Exploration step now detects duplicate tasks (M087)

## Documentation

- README: Added version and autoRemoveWorktrees config options
- README: Updated problem section with visual diagram
- Token tracking guidance for compaction scenarios
- Pre-edit self-check for main agent (M088)
- Commit separation guidance for `.claude/rules/` (M089)
- Mandatory user preference respect in `choose-approach` skill

## Bugfixes

- Fix commit-type hook validation (M091-M094)
- Fix emoji alignment in box-drawing documentation (HTML deprecation)

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 3 issues

```bash
git log --oneline --grep="Issue ID: v1.8-"
```
