# Plan: fix-argument-hint-compliance

## Goal
Ensure every skill that uses positional arguments (`$0`...`$N` or `$ARGUMENTS`) has a correct `argument-hint`
frontmatter field, as required by skill-builder Step 7.

## Parent Requirements
None — consistency/quality issue.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Typos in YAML frontmatter
- **Mitigation:** Mechanical changes only — add `argument-hint: "<catAgentId>"` to skills using `$0`

## Files to Modify

### Fix 1: consolidate-doc-agent incorrect hint
- `plugin/skills/consolidate-doc-agent/SKILL.md` — change `argument-hint` from
  `"<catAgentId> <path-to-document-or-directory>"` to `"<catAgentId>"` (unused `$1`)

### Fix 2: Add missing `argument-hint: "<catAgentId>"` to skills using only `$0`
- `plugin/skills/batch-read-agent/SKILL.md`
- `plugin/skills/batch-write-agent/SKILL.md`
- `plugin/skills/cleanup-agent/SKILL.md`
- `plugin/skills/collect-results-agent/SKILL.md`
- `plugin/skills/compare-docs-agent/SKILL.md`
- `plugin/skills/config-agent/SKILL.md`
- `plugin/skills/decompose-issue-agent/SKILL.md`
- `plugin/skills/empirical-test-agent/SKILL.md`
- `plugin/skills/format-documentation-agent/SKILL.md`
- `plugin/skills/get-history-agent/SKILL.md`
- `plugin/skills/get-session-id-agent/SKILL.md`
- `plugin/skills/get-subagent-status-agent/SKILL.md`
- `plugin/skills/git-amend-agent/SKILL.md`
- `plugin/skills/git-commit-agent/SKILL.md`
- `plugin/skills/git-merge-linear-agent/SKILL.md`
- `plugin/skills/git-rebase-agent/SKILL.md`
- `plugin/skills/git-rewrite-history-agent/SKILL.md`
- `plugin/skills/git-squash-agent/SKILL.md`
- `plugin/skills/grep-and-read-agent/SKILL.md`
- `plugin/skills/help-agent/SKILL.md`
- `plugin/skills/learn-agent/SKILL.md`
- `plugin/skills/load-skill-agent/SKILL.md`
- `plugin/skills/merge-subagent-agent/SKILL.md`
- `plugin/skills/optimize-execution-agent/SKILL.md`
- `plugin/skills/recover-from-drift-agent/SKILL.md`
- `plugin/skills/register-hook-agent/SKILL.md`
- `plugin/skills/remove-agent/SKILL.md`
- `plugin/skills/retrospective-agent/SKILL.md`
- `plugin/skills/safe-remove-code-agent/SKILL.md`
- `plugin/skills/safe-rm-agent/SKILL.md`
- `plugin/skills/skill-builder-agent/SKILL.md`
- `plugin/skills/stakeholder-common/SKILL.md`
- `plugin/skills/status-agent/SKILL.md`
- `plugin/skills/tdd-implementation-agent/SKILL.md`
- `plugin/skills/token-report-agent/SKILL.md`
- `plugin/skills/validate-git-safety-agent/SKILL.md`
- `plugin/skills/verify-implementation-agent/SKILL.md`
- `plugin/skills/work-prepare-agent/SKILL.md`
- `plugin/skills/write-and-commit-agent/SKILL.md`

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Fix consolidate-doc-agent argument-hint (remove unused `<path-to-document-or-directory>`)
- Add `argument-hint: "<catAgentId>"` to all 39 skills listed above

## Post-conditions
- [ ] Every SKILL.md that uses `$0`, `$N`, or `$ARGUMENTS` in its preprocessor command has a matching `argument-hint`
- [ ] consolidate-doc-agent argument-hint no longer references unused path parameter
- [ ] No SKILL.md has an argument-hint that doesn't match the actual arguments consumed
