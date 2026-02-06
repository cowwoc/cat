# Plan: fail-fast-missing-banners

## Goal
Enforce fail-fast when pre-rendered banners are unavailable due to context compaction. Addresses A019/PATTERN-008. When banners from SCRIPT OUTPUT sections are lost, skills must fail immediately with an error message and the script path to re-run, rather than constructing banners manually or silently degrading.

## Satisfies
None - infrastructure/retrospective action item

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Skills that currently degrade gracefully will now fail
- **Mitigation:** Each fail-fast includes the exact script command to re-run

## Files to Modify
- plugin/skills/ - Skills that reference SCRIPT OUTPUT banner sections
- Plugin skills with banner references need fail-fast + script path

## Acceptance Criteria
- [ ] All skills with SCRIPT OUTPUT references include fail-fast on missing banners
- [ ] Fail-fast message includes exact script path and arguments to re-run
- [ ] No manual banner construction anywhere in skill instructions
- [ ] No regressions

## Execution Steps
1. **Step 1:** Grep all skills for SCRIPT OUTPUT references to identify affected files
2. **Step 2:** For each affected skill, add fail-fast instruction with script re-invocation command
   - Pattern: "If SCRIPT OUTPUT section not found: FAIL. Run: [script path] [args]"
3. **Step 3:** Verify no skills contain manual box construction instructions
4. **Step 4:** Run all tests
   - Command: python3 /workspace/run_tests.py

## Success Criteria
- [ ] All banner-dependent skills have fail-fast with re-run commands
- [ ] No manual box/banner construction instructions remain
- [ ] All tests pass
