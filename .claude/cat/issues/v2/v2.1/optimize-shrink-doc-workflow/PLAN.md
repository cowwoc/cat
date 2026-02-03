# Plan: optimize-shrink-doc-workflow

## Current State
The shrink-doc skill runs sequential Bash commands for baseline/version checks and guesses file paths instead of using Glob results.

## Target State
1. Baseline check and version check run as parallel Bash calls
2. File discovery uses Glob results directly instead of guessing paths

## Satisfies
None - internal optimization

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - internal refactor only
- **Mitigation:** Tests verify behavior unchanged

## Files to Modify
- plugin/skills/shrink-doc/SKILL.md - Update workflow steps

## Acceptance Criteria
- [ ] All tests still pass
- [ ] Code quality improved (fewer round-trips)

## Execution Steps
1. **Step 1:** Modify Step 2 to combine baseline + version check into single parallel Bash
   - Files: plugin/skills/shrink-doc/SKILL.md
   - Verify: Skill still works correctly

2. **Step 2:** Update file discovery to use Glob output directly
   - Files: plugin/skills/shrink-doc/SKILL.md  
   - Verify: No path guessing after Glob
