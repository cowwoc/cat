# Plan: fix-blocked-tasks-diagnostics

## Problem
The work-prepare skill's diagnostic gathering (SKILL.md lines 146-175) reports all issues with
non-empty Dependencies as blocked, without checking whether those dependencies are actually
satisfied (closed). This causes the NO_TASKS response to show misleading blocked_tasks entries
where the listed blockers are already complete.

## Satisfies
None

## Reproduction Code
```bash
# When all compression tasks are filtered out via "skip compression",
# the NO_TASKS diagnostics report tasks like compress-concepts-batch-1
# as blocked by prevent-plan-md-priming, even though prevent-plan-md-priming
# is already closed.
```

## Expected vs Actual
- **Expected:** Only report dependencies as blockers when their STATE.md status is NOT "closed"
- **Actual:** All non-empty dependencies reported as blockers regardless of their status

## Root Cause
The diagnostic bash snippet in work-prepare SKILL.md extracts dependencies via grep but never
checks each dependency's STATE.md status before adding to BLOCKED_TASKS. The comment says
"check if they're resolved" but the code skips that check entirely.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - only affects diagnostic reporting, not task discovery logic
- **Mitigation:** Existing tests for get-available-issues.sh cover the core discovery logic;
  add a test for the diagnostic gathering in work-prepare

## Files to Modify
- `plugin/skills/work-prepare/SKILL.md` - Fix diagnostic gathering snippet to check dependency status

## Test Cases
- [ ] Original bug scenario - closed dependencies not reported as blockers
- [ ] Open dependencies still correctly reported as blockers
- [ ] Mixed dependencies (some closed, some open) correctly filtered

## Execution Steps
1. **Step 1:** Edit `plugin/skills/work-prepare/SKILL.md` lines 146-175
   - In the diagnostic gathering bash snippet, after extracting DEPS for an open/in-progress issue,
     iterate over each dependency and check its STATE.md status
   - Only add to BLOCKED_TASKS if at least one dependency has a status other than "closed"
   - Filter the blocked_by array to include only unsatisfied (non-closed) dependencies
   - Files: `plugin/skills/work-prepare/SKILL.md`

2. **Step 2:** Run existing tests to verify no regressions
   - Files: `run_tests.py`

## Success Criteria
- [ ] Diagnostic snippet checks each dependency's STATE.md status before reporting as blocker
- [ ] All existing tests pass
- [ ] No regressions in task discovery or lock acquisition
