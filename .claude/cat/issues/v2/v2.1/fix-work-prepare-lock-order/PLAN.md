# Plan: fix-work-prepare-lock-order

## Problem
`IssueDiscovery` checks for an existing worktree **before** checking whether the issue is locked by
another session. When another Claude instance owns the lock and has a worktree, the code returns
`ExistingWorktree` instead of `NotExecutable(locked)`. The agent then shows the user a confusing
"existing worktree detected — clean up?" prompt, obscuring the real cause: another session is
actively working on the issue and cleanup would destroy live work.

## Root Cause
Two code paths in `IssueDiscovery.java` share the same wrong ordering:

1. **`evaluateIssue()` (single-issue lookup, ~line 748):**
   ```java
   // Check for existing worktree  ← happens first
   if (Files.isDirectory(worktreePath))
     return new DiscoveryResult.ExistingWorktree(...);
   // Try to acquire lock  ← happens after, never reached when worktree exists
   IssueLock.LockResult lockResult = issueLock.acquire(...);
   ```

2. **`searchForIssue()` (bulk scan, ~line 1093):**
   ```java
   // Check for existing worktree  ← happens first
   if (Files.isDirectory(getWorktreePath(issueId))) continue;
   // Try to acquire lock  ← same problem
   IssueLock.LockResult lockResult = issueLock.acquire(...);
   ```

## Expected vs Actual
- **Expected:** Agent returns `LOCKED` with the owning session ID when the issue is held by
  another Claude instance, even if that session's worktree directory already exists.
- **Actual:** Agent returns `ExistingWorktree` (or `ERROR: Failed to create worktree: branch already
  exists`), causing the orchestrator to prompt the user to clean up an actively-used worktree.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** The `ExistingWorktree` case still needs to work when no lock is held
  (abandoned worktrees from crashed sessions). Swap order; do not remove the worktree check.
- **Mitigation:** Existing `executeReturnsLockedWhenReasonContainsLocked` test verifies the
  LOCKED path. New regression test covers the combined lock+worktree scenario.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` — swap lock check
  before worktree check in both `evaluateIssue()` and `searchForIssue()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add regression
  test `executeReturnsLockedWhenIssueIsLockedAndWorktreeExists`

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In `IssueDiscovery.evaluateIssue()`: move the lock-acquisition block (lines ~754-763) to
  **above** the existing-worktree check (lines ~748-752). The new order is:
  1. Check dependencies (unchanged)
  2. **Try to acquire lock** — if `Locked`, return `NotExecutable(locked)`
  3. Check for existing worktree — if present, return `ExistingWorktree`
  4. Return `Found` (unchanged)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
- In `IssueDiscovery.searchForIssue()`: move the existing-worktree directory check to **after**
  the lock-acquisition block (lines ~1093-1102). The new order is:
  1. Check dependencies (unchanged)
  2. **Try to acquire lock** — if not Acquired, `continue`
  3. Check for existing worktree — if present, `continue`
  4. Return `Found` (unchanged)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
- Add test `executeReturnsLockedWhenIssueIsLockedAndWorktreeExists` to `WorkPrepareTest.java`:
  1. Create a temp git CAT project with issue `2.1-locked-with-wt`
  2. Write a fresh lock file for that issue owned by a different session UUID
  3. Add an actual git worktree for the issue branch (`git worktree add -b 2.1-locked-with-wt
     <path> HEAD`)
  4. Call `prepare.execute()` with a different session UUID
  5. Assert `status == "LOCKED"` and `message` contains "locked"
  6. Cleanup: remove the worktree (`git worktree remove --force`) and temp project
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
- Run `mvn -f client/pom.xml test` — all tests must pass
  - Files: (none — verification only)

## Post-conditions
- [ ] Lock acquisition happens before any worktree creation attempt (verified by code ordering in
  `evaluateIssue()` and `searchForIssue()`)
- [ ] Error message when issue is locked includes the locking session's ID (verified by
  `NotExecutable` message: "Issue locked by another session: {owner}")
- [ ] Error message clearly distinguishes 'locked by another session' from 'worktree cleanup
  needed' (`LOCKED` status vs `ERROR` with "existing worktree")
- [ ] Regression test `executeReturnsLockedWhenIssueIsLockedAndWorktreeExists` passes: when a
  lock + existing worktree both exist, `execute()` returns `LOCKED` (not `ERROR`)
- [ ] All existing `WorkPrepareTest` tests still pass
- [ ] E2E: Reproduce the bug scenario — run `work-prepare` targeting an issue that is already
  locked by another session UUID with its worktree directory present. Confirm the result is
  `{"status":"LOCKED",...}` instead of `{"status":"ERROR","message":"Failed to create worktree..."}`
