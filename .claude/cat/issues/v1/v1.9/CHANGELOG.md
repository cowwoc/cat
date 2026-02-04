# Changelog: v1.9 - Display Standards

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-17

**Display Standards & Workflow Hardening**

Comprehensive display standardization, test infrastructure, and workflow stability improvements.

## New Features

- **Fork-in-the-Road Wizard**: Improved approach selection with wizard-style presentation
- **Exit Gate Dependencies**: Task dependencies for exit gate validation
- **Test Framework**: Added bats test framework with 66+ tests for hooks and scripts
- **Language Supplements**: Stakeholder reviews can load language-specific guidance

## Configuration

- New settings schema: `trust`, `verify`, `curiosity`, `patience` replace previous options
- Worktree isolation protection (M101) prevents commits to wrong worktree
- Task lock checking before offering tasks (M097)
- Hook to block direct lock file deletion (M096)
- Commit message guidance: don't list modified files (redundant with diff)
- Require failing test cases for bugfix tasks
- Strengthen token measurement requirements (A017)

## Bugfixes

- Fix bold rendering in display templates (M125)
- Fix box formatting for display standard compliance
- Fix subagent token measurement session ID issue (M109)
- Fix inconsistent task path patterns (M108)
- Fix parse_error false positives when command succeeds (M100)
- Fix HEREDOC message extraction in commit type validation
- Fix emoji display width calculations in box templates
- Fix docs vs config validation for Claude-facing files

## Documentation

- Display standards with markdown rendering rules (A018)
- Simplified emoji width handling
- Task locking protocol documentation

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 14 issues

```bash
git log --oneline --grep="Issue ID: v1.9-"
```
