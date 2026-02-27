# Plan: Fix cleanup survey missing worktrees

## Goal

Fix the /cat:cleanup survey handler so it correctly enumerates existing worktrees. Currently, the survey
outputs "0 worktrees found" even when worktrees exist on disk, forcing a fallback git worktree list call
to recover the correct state.

## Satisfies

None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Survey output format change must remain backward-compatible with cleanup skill parsing
- **Mitigation:** Read existing survey handler before changing it

## Files to Modify
- plugin/ or client/ source for the cleanup survey handler (to be determined during investigation)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Reproduce the bug - run /cat:cleanup and observe the survey output shows "0 worktrees found"
   even when git worktree list returns worktrees
2. **Step 2:** Locate the source code for the cleanup survey handler that generates the worktree list section
3. **Step 3:** Write a failing test that verifies the handler correctly enumerates worktrees
4. **Step 4:** Fix the worktree enumeration logic
5. **Step 5:** Verify the test passes and the survey now shows correct worktree count

## Post-conditions
- [ ] /cat:cleanup survey correctly reports all worktrees registered with git worktree list
- [ ] No regression in the rest of the cleanup survey output (locks, branches, stale remotes)
- [ ] All tests pass
