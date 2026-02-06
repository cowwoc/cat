# Changelog: v1.5 - Quality Gates

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-13

**Quality Gates, Progress Indicators, and Workflow Stability**

Major release with multi-perspective stakeholder reviews, visual progress indicators, and extensive workflow stability
improvements.

## New Features

- **Multi-Perspective Stakeholder Review**: New `stakeholder-review` skill providing architect, security, quality,
  tester, and performance perspectives before code approval
- **Progress Indicators**: Long-running workflows now display progress bars for file operations, verification steps, and
  batch processing
- **Duplicate Task Resolution**: Automatic detection and handling of duplicate or obsolete tasks
- **Escalation Requirements**: When prevention rules already exist for a mistake type, escalation is required for
  pattern-level analysis
- **Main Agent Boundaries (M063)**: Main agent is orchestrator only - all code implementation must be delegated to
  subagents

## Bugfixes

- **STATE.md Verification (M085)**: Approval gates now verify STATE.md is committed before presentation
- **Lock Denial Guidance (M084)**: Clear instructions when task lock acquisition fails
- **Lock Expiration (M065)**: Removed automatic expiration - requires explicit user cleanup
- **Plugin Paths**: Replaced hardcoded paths with `CLAUDE_PLUGIN_ROOT` for portability

## Improvements

- **RCA A/B Testing**: Root cause analysis method comparison for effectiveness tracking
- **Skill Workflow Compliance**: SessionStart hook enforces complete skill execution
- **Release Plugin Skill**: Streamlined version release process with branch deletion
- **Validation-Driven Skills**: Restored shrink-doc and compare-docs with validation requirements
- **Worktree Directory**: Main agent must work from worktree, not project root
- **STATE.md Commit Rules**: Refined ordering and verification via hooks (M070, M076, M077)

## Documentation

- Contributing section clarifying project scope and plugin boundaries
- STATE.md template expanded with optional sections
- Parser test anti-patterns (M062)
- Spawn-subagent updated to use Task tool

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 8 issues

```bash
git log --oneline --grep="Issue ID: v1.5-"
```
