# Changelog: v1.10 - Workflow Completion

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-20

**Workflow Refinements & Display Improvements**

Final v1.0 release completing the core rewrite with workflow refinements, display improvements, and planning structure for v1.1 commercialization.

## New Features

- **Review Feedback Loop**: Approval gate now spawns subagent for review feedback implementation
- **Task Branch Forking**: Task branches fork from current branch instead of main
- **Inline Diff Display**: Updated diff output to inline context style (Proposal I)
- **Progress Phase Indicators**: 4-phase progress display replaces 17-step tracker
- **Render Box Skill**: Centralized ASCII box rendering with emoji-aware alignment
- **Terminal Width Config**: Add terminal width setting to `/cat:config` wizard
- **Context Limit Enforcement**: Subagents have enforced context limits

## Improvements

- Config-driven approach selection with confidence-based fork wizard
- Expanded exploration subagent role for preparation and verification
- Positive prescriptive language replaces negative language in skills
- Validation-driven status display using scripts (M140-M145)
- Base branch configuration replaces hardcoded main references
- Optimized git-merge-linear skill for worktree-based merging

## Bugfixes

- Fix multi-file diff parsing dropping first files
- Fix box_header() alignment using pad() for emoji-aware width
- Fix status.sh arithmetic bug and empty array key guard
- Fix token estimate vs measurement confusion (M146)
- Fix commit type validation for retrospectives (M139)

## Planning

- v1.1 Commercialization: 12 tasks for licensing, legal, and enterprise features
- Remote lock metadata support for distributed task coordination
- Context-aware stakeholder selection

## Retrospectives

- R002: 10 mistakes analyzed
- R003: Status display resolution
- R008: 13 mistakes analyzed

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 29 issues

```bash
git log --oneline --grep="Issue ID: v1.10-"
```
