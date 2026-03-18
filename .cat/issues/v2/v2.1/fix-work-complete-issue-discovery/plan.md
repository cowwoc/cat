# Plan: fix-work-complete-issue-discovery

## Problem

The `work-complete` handler (`get-output work-complete`) returns the issue that was just merged as the "next" issue
instead of finding a different open issue or reporting "Scope Complete". This causes the `/cat:work` orchestrator to
suggest re-working an already-closed issue.

## Satisfies

None — bugfix for internal tooling

## Reproduction Code

```bash
# After merging 2.1-improve-cleanup-stale-artifact-ux:
"/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/get-output" work-complete 2.1-improve-cleanup-stale-artifact-ux v2.1
# Output contains: **Next:** 2.1-improve-cleanup-stale-artifact-ux (same issue!)
```

## Expected vs Actual

- **Expected:** Output shows a different open issue as next, or "Scope Complete" if none remain
- **Actual:** Output shows the just-merged issue as the next issue

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The issue discovery logic in WorkCompleteHandler does not filter out the completed issue
- **Mitigation:** Fix the filtering logic and add a test case

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/work_complete/WorkCompleteHandler.java` — Fix issue discovery
  logic to exclude the completed issue ID from the next-issue search

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Investigate WorkCompleteHandler issue discovery logic**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/work_complete/WorkCompleteHandler.java`
   - Find where the next issue is selected and identify why the completed issue is not excluded

2. **Fix the filtering to exclude the completed issue**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/work_complete/WorkCompleteHandler.java`
   - Ensure the completed issue ID is excluded from the candidate list
   - When no candidates remain, output "Scope Complete" instead of a next issue

3. **Add test coverage**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/work_complete/WorkCompleteHandlerTest.java`
   - Test that the completed issue is excluded from next-issue discovery
   - Test that "Scope Complete" is returned when no other issues exist

## Post-conditions

- [ ] work-complete never returns the just-completed issue as the next issue
- [ ] work-complete returns "Scope Complete" when no other open issues exist in the version
- [ ] All existing tests pass
- [ ] New test cases cover the fix
