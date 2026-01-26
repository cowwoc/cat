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

## Scripts to Rename
- `plugin/hooks/posttool_handlers/skill_precompute.py` → `skill_output_template.py`
- `plugin/scripts/precompute-config-display.sh` → `generate-config-display.sh`

## References to Update
- Any imports or references to renamed scripts
- Handler registrations in `__init__.py` files
- hooks.json if scripts are referenced there

## Acceptance Criteria
- [ ] Behavior unchanged
- [ ] All tests still pass
- [ ] No remaining references to "pre-computed output"
- [ ] No remaining files with "precompute" in name
- [ ] All imports and references updated to new names

## Execution Steps
1. **Step 1:** Search all MD and script files for "pre-computed output"
   - Verify: grep returns all instances
2. **Step 2:** Replace all occurrences with "output template"
   - Verify: grep confirms no remaining instances
3. **Step 3:** Rename `skill_precompute.py` to `skill_output_template.py`
   - Verify: File exists at new path
4. **Step 4:** Rename `precompute-config-display.sh` to `generate-config-display.sh`
   - Verify: File exists at new path
5. **Step 5:** Update all imports and references to renamed files
   - Verify: grep for old names returns no results
6. **Step 6:** Run tests
   - Verify: All tests pass
