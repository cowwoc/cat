# Plan: add-build-hooks-skill

## Current State
No dedicated skill exists for building the Java hooks JAR. Building requires manual mvn commands.

## Target State
A `/build-hooks` skill in `.claude/skills/build-hooks/SKILL.md` that automates building the hooks JAR, running tests, and installing the artifact.

## Satisfies
None - developer tooling improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - additive only
- **Mitigation:** Skill is self-contained

## Files to Modify
- `.claude/skills/build-hooks/SKILL.md` - Create new skill definition

## Execution Steps
1. **Step 1:** Create `.claude/skills/build-hooks/SKILL.md` with build, test, and install steps
   - Files: `.claude/skills/build-hooks/SKILL.md`

## Success Criteria
- [ ] Skill file exists at `.claude/skills/build-hooks/SKILL.md`
- [ ] Skill defines clear build, test, and install workflow
- [ ] No regressions in existing skills