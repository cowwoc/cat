# Plan: rename-precomputed-output

## Current State
References to "pre-computed output" exist across MD and script files in the plugin.

## Target State
All references renamed to "output template" for consistent terminology.

## Satisfies
None - terminology consistency task

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - terminology only, no API changes
- **Mitigation:** Search-and-replace with verification

## Files to Modify
- MD files containing "pre-computed output"
- Script files containing "pre-computed output"

## Acceptance Criteria
- [ ] Behavior unchanged
- [ ] All tests still pass
- [ ] No remaining references to "pre-computed output"

## Execution Steps
1. **Step 1:** Search all MD and script files for "pre-computed output"
   - Verify: grep returns all instances
2. **Step 2:** Replace all occurrences with "output template"
   - Verify: grep confirms no remaining instances
3. **Step 3:** Run tests
   - Verify: All tests pass
