# Plan: refactor-status-scripts-to-python

## Current State
Status display pre-computation uses shell script (precompute-status-display.sh) that invokes
build-box-lines.py as subprocess multiple times. Data collection is in status.sh.

## Target State
- Rename status.sh to get-status-data.sh for clarity
- Convert precompute-status-display.sh to Python
- Import build-box-lines.py functions directly instead of subprocess calls
- Single Python module handles both data collection parsing and box rendering

## Satisfies
None - infrastructure/refactoring task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Hook filename change requires hooks.json update
- **Mitigation:** Test /cat:status after changes; verify hook registration

## Files to Modify
- plugin/scripts/status.sh - Rename to get-status-data.sh
- plugin/hooks/precompute-status-display.sh - Convert to precompute-status-display.py
- plugin/scripts/build-box-lines.py - Extract functions for import
- plugin/hooks/hooks.json - Update hook registration

## Acceptance Criteria
- [ ] /cat:status works correctly after refactor
- [ ] All existing tests pass
- [ ] No subprocess calls to build-box-lines.py from the new Python hook

## Execution Steps
1. **Step 1:** Rename status.sh to get-status-data.sh
   - Files: plugin/scripts/status.sh
   - Verify: Script still callable with new name

2. **Step 2:** Refactor build-box-lines.py for importability
   - Files: plugin/scripts/build-box-lines.py
   - Verify: Can import display_width, build_line, build_border as functions

3. **Step 3:** Create precompute-status-display.py
   - Files: plugin/hooks/precompute-status-display.py
   - Verify: Imports build-box-lines functions, calls get-status-data.sh for JSON

4. **Step 4:** Update hooks.json registration
   - Files: plugin/hooks/hooks.json
   - Verify: New Python hook registered correctly

5. **Step 5:** Remove old shell hook
   - Files: plugin/hooks/precompute-status-display.sh
   - Verify: /cat:status still works end-to-end
