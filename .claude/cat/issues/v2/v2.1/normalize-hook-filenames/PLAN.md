# Plan: normalize-hook-filenames

## Current State
The hook file `plugin/hooks/session_start.sh` uses underscores, while all other hook scripts use hyphens
(e.g., `detect-giving-up.sh`, `inject-claudemd-section.sh`, `warn-base-branch-edit.sh`).

## Target State
Rename `session_start.sh` to `session-start.sh` and update all references for naming consistency.

## Satisfies
None - naming consistency cleanup

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None if all references updated
- **Mitigation:** Grep for all references before and after

## Files to Modify
- `plugin/hooks/session_start.sh` - Rename to `session-start.sh`
- `plugin/hooks/hooks.json` - Update SessionStart command reference
- `plugin/hooks/hook.sh` - Update error message referencing session_start.sh
- `plugin/hooks/README.md` - Update documentation references

## Execution Steps
1. **Step 1:** Rename the file using `git mv plugin/hooks/session_start.sh plugin/hooks/session-start.sh`
2. **Step 2:** Update `plugin/hooks/hooks.json` line 8: change `session_start.sh` to `session-start.sh`
3. **Step 3:** Update `plugin/hooks/hook.sh` line 44: change `session_start.sh` to `session-start.sh` in error message
4. **Step 4:** Update `plugin/hooks/README.md` lines 20 and 67: change `session_start.sh` to `session-start.sh`
5. **Step 5:** Grep the entire codebase for remaining `session_start.sh` references (excluding PLAN.md files)

## Success Criteria
- [ ] All tests pass after refactoring
- [ ] No functional references to `session_start.sh` remain (PLAN.md references in closed issues are acceptable)
- [ ] `session-start.sh` is executable and functions identically