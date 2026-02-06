# Plan: replace-precomputed-with-script-output

## Goal
Replace all instances of "pre-computed" output terminology with "script output" across the codebase. This is a
terminology standardization to make documentation clearer - the output comes from scripts, not pre-computation.

## Satisfies
None - terminology cleanup

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** May affect references in retrospective data or learnings
- **Mitigation:** Search broadly, replace carefully, preserve meaning

## Files to Modify
- plugin/skills/**/*.md - Skill files referencing "pre-computed"
- plugin/concepts/**/*.md - Concept files
- plugin/hooks/**/*.py - Hook handlers
- .claude/cat/retrospectives/ - Retrospective entries (if applicable)

## Acceptance Criteria
- [ ] No instances of "pre-computed" remain in active skill/concept files
- [ ] All replacements preserve original meaning
- [ ] Tests pass
- [ ] No regressions

## Execution Steps
1. **Step 1:** Search all files for "pre-computed", "precomputed", "Pre-computed", "Precomputed" variations
2. **Step 2:** Replace with "script output" or appropriate "script" terminology
   - Files: all matching files from Step 1
3. **Step 3:** Verify replacements read naturally in context
4. **Step 4:** Run all tests
   - Command: python3 /workspace/run_tests.py

## Success Criteria
- [ ] Zero instances of "pre-computed"/"precomputed" in active plugin files
- [ ] All replacements are contextually appropriate
- [ ] All tests pass
