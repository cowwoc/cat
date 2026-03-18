# Plan: move-review-artifacts-to-cat-work

## Current State
Stakeholder review agents write concern artifacts to `${WORKTREE_PATH}/.cat/review/` (e.g., `.cat/review/security-concerns.json`). This directory is not under `.cat/work/` and the files can unintentionally be included in commits.

## Target State
All worktree-specific review artifacts are written to `${WORKTREE_PATH}/.cat/work/review/` instead (e.g., `.cat/work/review/security-concerns.json`). The path is gitignored so these runtime files are never committed.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Any code that reads from `.cat/review/` must be updated to read from `.cat/work/review/`
- **Mitigation:** Simple find-and-replace across affected agent and skill files; verify with grep after

## Files to Modify
- `plugin/agents/stakeholder-architecture.md` - update output path to `.cat/work/review/architecture-concerns.json`
- `plugin/agents/stakeholder-security.md` - update output path to `.cat/work/review/security-concerns.json`
- `plugin/agents/stakeholder-design.md` - update output path to `.cat/work/review/design-concerns.json`
- `plugin/agents/stakeholder-testing.md` - update output path to `.cat/work/review/testing-concerns.json`
- `plugin/agents/stakeholder-performance.md` - update output path to `.cat/work/review/performance-concerns.json`
- `plugin/agents/stakeholder-requirements.md` - update output path to `.cat/work/review/requirements-concerns.json`
- `plugin/agents/stakeholder-business.md` - update output path to `.cat/work/review/business-concerns.json`
- `plugin/agents/stakeholder-legal.md` - update output path to `.cat/work/review/legal-concerns.json`
- `plugin/agents/stakeholder-deployment.md` - update output path to `.cat/work/review/deployment-concerns.json`
- `plugin/agents/stakeholder-ux.md` - update output path to `.cat/work/review/ux-concerns.json`
- `plugin/skills/stakeholder-review-agent/first-use.md` - update all `.cat/review/` references to `.cat/work/review/`
- `plugin/skills/work-review-agent/first-use.md` - update all `.cat/review/` references to `.cat/work/review/`
- `.gitignore` - add `.cat/work/review/` to gitignored paths

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update all stakeholder agent files to write to `.cat/work/review/` instead of `.cat/review/`
  - Files: `plugin/agents/stakeholder-architecture.md`, `plugin/agents/stakeholder-security.md`, `plugin/agents/stakeholder-design.md`, `plugin/agents/stakeholder-testing.md`, `plugin/agents/stakeholder-performance.md`, `plugin/agents/stakeholder-requirements.md`, `plugin/agents/stakeholder-business.md`, `plugin/agents/stakeholder-legal.md`, `plugin/agents/stakeholder-deployment.md`, `plugin/agents/stakeholder-ux.md`
- Update skill files that reference `.cat/review/`
  - Files: `plugin/skills/stakeholder-review-agent/first-use.md`, `plugin/skills/work-review-agent/first-use.md`
- Add `.cat/work/review/` to `.gitignore`
  - Files: `.gitignore`

## Post-conditions
- [ ] `grep -r '\.cat/review/' plugin/agents/ plugin/skills/` returns no results
- [ ] `grep '\.cat/work/review/' plugin/agents/stakeholder-architecture.md` confirms new path
- [ ] `.gitignore` contains `.cat/work/review/`
