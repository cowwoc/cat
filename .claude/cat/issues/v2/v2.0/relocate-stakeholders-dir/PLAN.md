# Plan: relocate-stakeholders-dir

## Current State
Stakeholder definitions are nested under `plugin/concepts/stakeholders/`, creating unnecessary directory depth.

## Target State
Stakeholder definitions at `plugin/stakeholders/` - a flatter, more intuitive location alongside other plugin
components.

## Satisfies
None - infrastructure/cleanup task

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - internal reorganization only
- **Mitigation:** Update all references before removing old location

## Files to Modify
- `plugin/concepts/stakeholders/*` - move to `plugin/stakeholders/`
- Any files referencing `plugin/concepts/stakeholders` - update paths

## Acceptance Criteria
- [ ] Behavior unchanged
- [ ] All tests still pass
- [ ] References updated - all file references point to new location

## Execution Steps
1. **Step 1:** Create `plugin/stakeholders/` directory
   - Files: plugin/stakeholders/
   - Verify: `ls plugin/stakeholders/`
2. **Step 2:** Move all stakeholder files from concepts to new location
   - Files: All files in plugin/concepts/stakeholders/
   - Verify: `ls plugin/stakeholders/` shows all stakeholder files
3. **Step 3:** Find and update all references to old path
   - Files: Any files with `plugin/concepts/stakeholders` references
   - Verify: `grep -r "plugin/concepts/stakeholders" plugin/` returns nothing
4. **Step 4:** Remove empty `plugin/concepts/stakeholders/` directory
   - Verify: `ls plugin/concepts/` shows no stakeholders directory
5. **Step 5:** Run tests to verify no regressions
   - Verify: `python3 /workspace/run_tests.py` passes
