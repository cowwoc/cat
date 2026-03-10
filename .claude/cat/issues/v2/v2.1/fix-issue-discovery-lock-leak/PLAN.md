# Plan: fix-issue-discovery-lock-leak

## Problem

`IssueDiscovery` acquires a lock with an empty `worktrees` map (`worktrees: {}`) during issue
discovery, then proceeds to find an existing worktree — but leaves the lock in the empty-worktrees
state instead of releasing or updating it. On the next `work-prepare` invocation, `IssueLock.acquire`
throws `IOException: "has an empty worktrees map"`, blocking all future work on that issue.

Two code paths are affected:

**Bug 1 — `findIssueInDir` (ALL scope scan):**
`IssueDiscovery.java:1118` acquires the lock with `""` as the worktree path (`issueLock.acquire(issueId,
options.sessionId(), "")`), creating a lock with `worktrees: {}`. At line 1124 it detects an existing
worktree directory and does `continue` — skipping to the next issue — without releasing the lock.
The lock is now permanently stuck with `worktrees: {}`.

**Bug 2 — `findSpecificIssue` (specific issue scope):**
`IssueDiscovery.java:771` similarly acquires the lock with `""`. At line 781 it detects the worktree
exists and returns `ExistingWorktree` without updating the lock with the worktree path. The lock has
`worktrees: {}`, which causes the next `work-prepare` to fail with "empty worktrees map" even after
the user attempts to run another session on the same issue.

## Parent Requirements

None

## Reproduction Code

```java
// Bug 1 scenario (ALL scope)
// 1. Previous session created worktree for "2.1-fix-bug" and made commits
// 2. Lock was properly released
// 3. A new session ran work-prepare (ALL scope) while the worktree dir still existed:
//    - IssueDiscovery acquired lock with "" → worktrees: {}
//    - Detected worktree exists → continue (skipped issue, leaked lock)
// 4. Next work-prepare invocation:
IssueLock lock = new IssueLock(scope);
LockResult result = lock.acquire("2.1-fix-bug", newSessionId, "");
// Throws: IOException("Lock file... has an empty worktrees map")

// Bug 2 scenario (specific issue scope)
// 1. A stale lock from a dead session was overwritten by overwriteWithFreshLock → worktrees: {}
// 2. findSpecificIssue detected worktree exists → returned ExistingWorktree without updating lock
// 3. Cleanup force-released the lock, next invocation created fresh lock with ""
// 4. Again findSpecificIssue detected worktree → returned ExistingWorktree without updating
// 5. Next work-prepare: same "empty worktrees map" error
```

## Expected vs Actual

- **Expected (Bug 1):** When `findIssueInDir` skips an issue because its worktree exists, it releases
  the lock before continuing to the next issue. The lock file is not left in an invalid state.
- **Actual (Bug 1):** Lock is acquired with `worktrees: {}` and never released. Next work-prepare
  fails with `IOException: "has an empty worktrees map"`.

- **Expected (Bug 2):** When `findSpecificIssue` finds an existing worktree after acquiring the lock,
  it updates the lock with the actual worktree path before returning `ExistingWorktree`. The lock
  reflects reality even while cleanup is offered to the user.
- **Actual (Bug 2):** Lock is acquired with `worktrees: {}` and returned without update. The lock file
  remains invalid, causing "empty worktrees map" errors on subsequent work-prepare invocations.

## Root Cause

Both code paths in `IssueDiscovery` acquire the lock before checking for an existing worktree (this
ordering is intentional — it prevents a locked issue from being treated as an abandoned worktree).
However, neither code path updates or releases the lock when they subsequently discover the worktree
already exists. The lock file is left with `worktrees: {}`, which `IssueLock.acquire` treats as a
corrupt/invalid state on future calls.

## Impact Notes

`restore-worktree-on-resume` (closed) and `fix-work-prepare-lock-order` (closed) both addressed
related lock ordering concerns but did not fix these specific leak paths. This is a recurrence with a
distinct root cause: the lock-acquired-before-worktree-check design leaves no cleanup path when the
worktree check fails.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Changes to lock acquisition/release in IssueDiscovery. Could affect concurrent
  session behavior if release logic is incorrect.
- **Mitigation:** Add regression tests for both scenarios before fixing. TDD approach ensures the fix
  is targeted.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` — Fix Bug 1 in
  `findIssueInDir` (release lock before `continue`) and Bug 2 in `findSpecificIssue` (update lock
  with worktree path before returning `ExistingWorktree`)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java` — Add regression
  tests for both scenarios

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Write failing tests for Bug 1 and Bug 2, verify they fail, then fix both bugs in
  `IssueDiscovery.java`, verify tests pass
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java`
  - **Bug 1 fix:** In `findIssueInDir` (around line 1118-1125), after `issueLock.acquire(...)` succeeds
    and `Files.isDirectory(getWorktreePath(issueId))` is true, call
    `issueLock.release(issueId, options.sessionId())` before `continue`
  - **Bug 2 fix:** In `findSpecificIssue` (around line 771-784), after acquiring the lock and before
    returning `ExistingWorktree`, call `issueLock.update(issueId, options.sessionId(),
    worktreePath.toString())` to register the worktree path in the lock file. If `update` fails (e.g.,
    lock was overwritten by a concurrent session), proceed with returning `ExistingWorktree` anyway —
    the update is best-effort.
  - **Test for Bug 1:** Create a scenario where a worktree directory exists for an issue, run ALL-scope
    discovery, verify that after discovery completes (with a different issue found), the lock for the
    skipped issue does not exist (was released)
  - **Test for Bug 2:** Create a scenario where a specific issue has an existing worktree and the lock
    was acquired with empty worktrees map (simulating the `overwriteWithFreshLock` scenario). Run
    specific-issue discovery, verify the returned `ExistingWorktree` result AND verify the lock file
    now has the worktree path in its `worktrees` map (not `{}`)
  - Run `mvn -f client/pom.xml test` and verify all tests pass
  - Update `STATE.md` to closed

## Post-conditions

- [ ] Bug 1 fixed: running work-prepare (ALL scope) when a worktree exists for one issue does not
  leave a lock file with `worktrees: {}` for that issue
- [ ] Bug 2 fixed: running work-prepare for a specific issue that has an existing worktree correctly
  updates the lock file with the worktree path before returning the ExistingWorktree error result
- [ ] Regression tests pass for both scenarios
- [ ] `mvn -f client/pom.xml test` exits 0 (no regressions)
- [ ] E2E: run work-prepare after a previous session left a worktree — no "empty worktrees map"
  IOException is thrown
