# Plan: skip-decomposed-parents-in-discovery

## Problem
The `get-available-issues.sh` script returns decomposed parent tasks (like `compress-md-files`) instead of their
executable sub-issues. When a parent task has `## Decomposed Into` section and its sub-issues have dependencies that aren't
satisfied, the script still offers the parent task - which cannot be executed directly.

## Reproduction Code
```bash
# Parent task compress-md-files is in-progress with Decomposed: true
# Its sub-issues (compress-skills-md, etc.) depend on migrate-to-silent-preprocessing
# Script returns: compress-md-files (not executable)
plugin/scripts/get-available-issues.sh --session-id "test"
```

## Expected vs Actual
- **Expected:** Script should skip decomposed parent tasks and find executable sub-issues, or report "no executable tasks"
  if all sub-issues are blocked
- **Actual:** Script offers decomposed parent task which cannot be executed directly

## Root Cause
The `find_issue_in_minor()` function checks if a task is `pending` or `in-progress`, checks dependencies, but does NOT
check if the task has `## Decomposed Into` section. Decomposed parent tasks should be skipped - the work should be done
on sub-issues instead.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - only adds a skip condition
- **Mitigation:** Existing tests should continue to pass

## Files to Modify
- `plugin/scripts/get-available-issues.sh` - Add check to skip decomposed parent tasks

## Test Cases
- [ ] Decomposed parent tasks are skipped during discovery
- [ ] Sub-issues of decomposed parents are found when dependencies are met
- [ ] Non-decomposed tasks continue to work normally
- [ ] "No executable tasks" returned when all sub-issues are blocked

## Acceptance Criteria
- [ ] Script skips tasks with `## Decomposed Into` section in STATE.md
- [ ] Sub-issues are discovered when their dependencies are satisfied
- [ ] No regressions in existing task discovery behavior

## Execution Steps
1. **Add decomposed parent check in find_issue_in_minor():**
   - After status check, before dependency check
   - Check if STATE.md contains `## Decomposed Into`
   - If yes, skip this task (continue to next)
   - Verify: Decomposed parents are no longer offered
2. **Test the fix:**
   - Run discovery with current state (compress-md-files decomposed)
   - Verify script finds migrate-to-silent-preprocessing sub-issues or reports blocked
