# Changelog: v1.1 - Workflow Refinements

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-12

**Workflow Refinements and Context Injection**

Stabilization release with workflow improvements and better context management.

## New Features

- **Direct Context Injection**: Replaced CLAUDE.md injection with `inject-session-instructions.sh` hook for cleaner
  context loading
- **Flattened Task Structure**: Changed from nested directories to task ID format (e.g., `1.0-task-name`)
- **Commit Message Changelogs**: Task changelog content now embedded in commit messages, not separate files

## Improvements

- Integrated changelog workflow with minor/major version CHANGELOG.md files
- Clarified task STATE.md belongs with implementation commit
- Required subagent for bulk operations (shrink-doc)
- Improved git skills with safety requirements

## Bugfixes

- Fix workflow gaps that caused M020-M022 mistakes
- Fix mistakes.json and retrospectives.json commit synchronization
- Retrospective action items A005, A007 implementation

## Documentation

- Comprehensive README rewrite with full documentation
- Updated tagline to "AI Agents that land on their feet"
- Added Session Instructions section to README
- Migration check for retrospective file path

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 8 issues

```bash
git log --oneline --grep="Issue ID: v1.1-"
```
