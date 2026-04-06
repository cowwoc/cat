# Plan: rewrite-cleanup-step4-tests

## Goal
Rewrite Turn 1 sections of the 5 cleanup step4 test files to use organic multi-turn format where the user
prompt does not name or hint at which skill to invoke.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None
- **Mitigation:** Files are test scenarios only; no functional code changes

## Files to Modify
- plugin/tests/skills/cleanup/first-use/step4-confirm.md - rewrite Turn 1 to organic multi-turn format
- plugin/tests/skills/cleanup/first-use/step4-no-dir-delete.md - rewrite Turn 1 to organic multi-turn format
- plugin/tests/skills/cleanup/first-use/step4-sequence.md - rewrite Turn 1 to organic multi-turn format
- plugin/tests/skills/cleanup/first-use/step4-validate-dir.md - rewrite Turn 1 to organic multi-turn format
- plugin/tests/skills/cleanup/first-use/step4-validate-name.md - rewrite Turn 1 to organic multi-turn format

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Rewrite all 5 step4 test files with organic Turn 1 + Turn 2 multi-turn format
  - Files: plugin/tests/skills/cleanup/first-use/step4-confirm.md,
           plugin/tests/skills/cleanup/first-use/step4-no-dir-delete.md,
           plugin/tests/skills/cleanup/first-use/step4-sequence.md,
           plugin/tests/skills/cleanup/first-use/step4-validate-dir.md,
           plugin/tests/skills/cleanup/first-use/step4-validate-name.md

## Post-conditions
- [ ] All 5 files have Turn 1 that does not name or hint at the cleanup skill
- [ ] All 5 files have Turn 2 that selects option 3 (delete corrupt index.json files)
- [ ] Frontmatter and Assertions sections are unchanged
