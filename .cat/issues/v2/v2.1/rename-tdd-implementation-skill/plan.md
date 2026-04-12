# Plan: rename-tdd-implementation-skill

## Current State
Several files reference the skill as `tdd-implementation` (without `-agent` suffix), but the actual directory is `plugin/skills/tdd-implementation-agent/`.

## Target State
All active code and documentation consistently reference `tdd-implementation-agent`.

## Parent Requirements
None - cleanup task

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None (skill invocations still work, just updating references for consistency)
- **Mitigation:** Grep verification before/after to confirm all stale references updated

## Files to Modify
- `CLAUDE.md` - Change `/cat:tdd-implementation` → `/cat:tdd-implementation-agent` (line 69)
- `AGENTS.md` - Same change (line 69)
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/prompt/UserIssues.java` - Change `Skill: tdd-implementation` → `Skill: tdd-implementation-agent` (line 91)
- `plugin/skills/instruction-builder-agent/skill-conventions.md` - Change skill reference (line 723)
- `.cat/issues/v2/v2.3/compress-skills-batch-4/plan.md` - Update path `plugin/skills/tdd-implementation/SKILL.md` → `plugin/skills/tdd-implementation-agent/SKILL.md` (lines 28, 39)

## Exclusions
- Closed v2.0 and v2.1 issue files (per CLAUDE.md: do not update closed issues)
- `.cat/retrospectives/mistakes-2026-02.json` (historical record)

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Update documentation files
- Update CLAUDE.md line 69: `/cat:tdd-implementation` → `/cat:tdd-implementation-agent`
  - Files: `CLAUDE.md`
- Update AGENTS.md line 69: same change
  - Files: `AGENTS.md`
- Update skill-conventions.md line 723: `tdd-implementation` → `tdd-implementation-agent`
  - Files: `plugin/skills/instruction-builder-agent/skill-conventions.md`

### Job 2: Update source code
- Update UserIssues.java line 91: `Skill: tdd-implementation` → `Skill: tdd-implementation-agent`
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/prompt/UserIssues.java`

### Job 3: Update open issue plan
- Update compress-skills-batch-4 plan.md line 28: `plugin/skills/tdd-implementation/` → `plugin/skills/tdd-implementation-agent/`
- Update same file line 39: commit message reference
  - Files: `.cat/issues/v2/v2.3/compress-skills-batch-4/plan.md`

### Job 4: Verify completeness
- Run grep to verify no remaining stale references in active files
- Confirm closed issues and retrospectives still contain old references (unchanged as expected)

## Post-conditions
- [ ] All 5 active files updated with correct skill name
- [ ] Grep finds no stale `tdd-implementation` references in CLAUDE.md, AGENTS.md, client/, or plugin/ (excluding closed issues)
- [ ] All tests pass
