# Changelog: v2.0 - Commercialization

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-26

**Commercialization & Display Overhaul**

Major release preparing CAT for commercial use with licensing infrastructure, comprehensive display redesign, and workflow hardening. 74 tasks completed across 310 commits.

## Licensing & Commercial Features

- **License Validation**: JWT-based license token generation and validation
- **Feature Gating**: Tier-based feature entitlement mapping (Free/Pro/Enterprise)
- **Update Check**: Startup version check with upgrade notifications
- **Legal Review**: LICENSE.md reviewed for commercial release compliance

## Display Redesign

- **Ultra-Compact Status**: Redesigned `/cat:status` with condensed visual layout
- **Render-Diff Skill**: New skill for approval gate code review with 4-column table format
- **Dynamic Box Sizing**: Boxes automatically expand to fit content width
- **Emoji-Aware Alignment**: Correct padding for emoji characters in box borders

## Workflow Improvements

- **Version Boundary Gates**: Approval gates at version completion milestones
- **Git Workflow Wizard**: `/cat:init` now configures merge style, squash preferences, and branching
- **Soft Decomposition Threshold**: Suggest task decomposition when approaching context limits
- **Context-Aware Acceptance Criteria**: `/cat:add` options adapt to task type
- **Local Config Override**: `cat-config.local.json` for machine-specific settings
- **Task Tools Integration**: Native Claude Code task tracking replaces custom backup system

## Research & Planning

- **Recursive Drill-Down**: `/cat:research` supports multi-level exploration with scorecards
- **Executive Summary**: Research results include strategic recommendations
- **Skill Builder Rewrite**: 12 core skills rewritten with validation-driven approach

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 74 issues across 310 commits

```bash
git log --oneline --grep="Issue ID: v2.0-"
```
