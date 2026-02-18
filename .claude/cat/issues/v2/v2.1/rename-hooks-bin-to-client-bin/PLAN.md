# Plan: rename-hooks-bin-to-client-bin

## Goal
Replace all `hooks/bin` path references with `client/bin` across the entire codebase (plugin AND Java source).

## Satisfies
None - infrastructure rename

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Mechanical text replacement; no logic changes
- **Mitigation:** Grep before and after to confirm zero remaining references in source code

## Files to Modify
- plugin/hooks/hooks.json - 14 occurrences
- plugin/skills/work-with-issue-first-use/SKILL.md - 5 occurrences
- plugin/skills/skill-builder-first-use/SKILL.md - 2 occurrences
- plugin/skills/work-first-use/SKILL.md - 1 occurrence
- plugin/skills/stakeholder-review-box/SKILL.md - 1 occurrence
- plugin/skills/stakeholder-concern-box/SKILL.md - 1 occurrence
- plugin/skills/stakeholder-selection-box/SKILL.md - 1 occurrence
- plugin/skills/status-first-use/SKILL.md - 1 occurrence
- plugin/skills/statusline-first-use/SKILL.md - 1 occurrence
- plugin/skills/work-complete/SKILL.md - 1 occurrence
- plugin/skills/work-complete-first-use/SKILL.md - 1 occurrence
- plugin/skills/run-retrospective-first-use/SKILL.md - 1 occurrence
- plugin/skills/optimize-execution-first-use/SKILL.md - 1 occurrence
- plugin/concepts/silent-execution.md - 1 occurrence
- plugin/scripts/get-render-diff.sh - 1 occurrence
- client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java - 3 occurrences (lines 67, 473, 521)
- client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java - 17 occurrences

## Acceptance Criteria
- [ ] All `hooks/bin` references in Java source replaced with `client/bin`
- [ ] All `hooks/bin` references in Java tests replaced with `client/bin`
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] Zero remaining `hooks/bin` references in `client/src/`

## Execution Steps
1. **Replace in SkillLoader.java:** Update 3 occurrences (Javadoc lines 67, 473 and path resolution line 521)
2. **Replace in SkillLoaderTest.java:** Update 17 occurrences (test data and directory creation)
3. **Verify:** Grep `client/src/` for remaining `hooks/bin` references - expect zero
4. **Test:** Run `mvn -f client/pom.xml test` - all tests must pass
5. **Commit:** Commit changes
