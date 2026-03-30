# Plan: fix-add-agent-duplicate-detection-open-only

## Problem
The `add-agent` skill's duplicate-detection logic checks all issues in a version (both open and closed) to prevent duplicate issue creation. This causes false positives: attempting to create an issue with the same name as a previously closed issue is incorrectly rejected as a duplicate.

## Parent Requirements
None (bug fix)

## Expected vs Actual
- **Expected:** Duplicate detection should only check open issues. Creating an issue with the same name as a closed issue should be allowed.
- **Actual:** All issues (open and closed) are checked, blocking issue creation if a closed issue with the same name exists.

## Root Cause
The duplicate-detection logic does not filter by issue status and checks all issues in the version index.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Closed issues could have naming conflicts with new open issues, but this is acceptable (closed issues are historical records)
- **Mitigation:** Add regression test with a mix of open and closed issues to verify detection works correctly

## Files to Modify
- `plugin/skills/add-agent/first-use.md` - Update duplicate-detection logic to filter by open status only

## Test Cases
- [ ] Duplicate detection rejects open issues with same name
- [ ] Duplicate detection allows new issues with same name as closed issues
- [ ] E2E: Create issue, close it, then create new issue with same name succeeds

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Jobs
<!-- OPTIONAL: Skills requiring main-agent-level execution. Remove if not needed. -->

## Jobs

### Job 1
- Update `add-agent/first-use.md` duplicate-detection logic to only check issues with `"status": "open"` in their index.json
- Add test case verifying that duplicate detection ignores closed issues
  - Files: `plugin/skills/add-agent/first-use.md`

## Post-conditions
- [ ] Bug fixed - duplicate detection filters to open issues only
- [ ] Regression test added to prevent regression
- [ ] No new issues introduced
- [ ] E2E verification - test duplicate detection with mix of open/closed issues
