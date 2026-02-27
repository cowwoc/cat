# Plan: Fix Stale Lock Overwrite in IssueLock.acquire()

## Problem

`IssueLock.acquire()` returns `Locked` when a stale lock (>4 hours old) from a different (dead) session exists.
This causes a TOCTOU race condition: `BlockUnsafeRemoval` sees the stale lock, decides the worktree is unprotected,
and deletes it. When a new session then acquires a fresh lock, the worktree is already gone — causing implementation
subagents to commit directly to the base branch (v2.1) instead of an isolated issue branch.

## Satisfies

None

## Reproduction Code

```
// 1. Session A acquires lock for issue X, creates worktree, then dies (lock is now >4h old)
// 2. Session B runs /cat:work for issue X
//    - work-prepare: lock is stale (different session) → acquire() returns Locked, skips issue
//      OR acquire() overwrites stale lock, writes fresh lock with session B's ID
//    - BlockUnsafeRemoval: scans lock, sees stale lock from session A → deletes worktree
//    - Session B subagents: worktree path doesn't exist → commit to main workspace (v2.1)
```

## Expected vs Actual

- **Expected:** When a stale lock (>4h) from a dead session exists, `acquire()` overwrites it with a fresh lock
  for the current session. `BlockUnsafeRemoval` then sees a fresh lock and protects the worktree.
- **Actual:** `acquire()` returns `Locked` for stale locks from different sessions, or the new session acquires
  the lock but `BlockUnsafeRemoval` still deletes the worktree during the window before the lock is refreshed.

## Root Cause

`BlockUnsafeRemoval.getProtectedPaths()` correctly skips stale locks (>4h) to avoid protecting ghost worktrees
from truly dead sessions. However, `IssueLock.acquire()` does not overwrite stale locks atomically — there is a
window between when cleanup scans the lock (seeing the stale lock) and when the new session writes a fresh lock.
This TOCTOU gap allows cleanup to delete a worktree that the new session is about to use.

The fix: `IssueLock.acquire()` must overwrite stale locks (>4h, from a different session) with a fresh lock for
the current session. This ensures that any `BlockUnsafeRemoval` scan after acquisition will see a fresh (non-stale)
lock and protect the worktree.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Could incorrectly overwrite a non-stale lock from another active session (but the stale
  check prevents this — only locks >4h old are overwritten)
- **Mitigation:** Add tests for: stale-lock overwrite, non-stale lock protection, own-session re-acquire

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java` - Overwrite stale locks in acquire()
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockTest.java` - Add stale lock overwrite tests

## Test Cases

- [ ] Stale lock overwrite: acquire() with stale lock (>4h) from different session → returns OK with fresh lock
- [ ] Non-stale lock protection: acquire() with non-stale lock from different session → returns Locked
- [ ] Own-session re-acquire: acquire() with own session's lock → returns OK (idempotent)
- [ ] No existing lock: acquire() with no lock file → creates fresh lock, returns OK

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1 (TDD - write failing tests first):** Add test methods to `IssueLockTest.java`:

   **`acquireOverwritesStaleLockFromDifferentSession`:** Create a lock file with a different session ID and
   timestamp >4 hours ago. Call `IssueLock.acquire(issueId, currentSession)`. Assert result is `OK`. Assert
   lock file now contains `currentSession` as session_id and a recent timestamp.

   **`acquireRejectsNonStaleLockFromDifferentSession`:** Create a lock file with a different session ID and
   timestamp <4 hours ago. Call `IssueLock.acquire(issueId, currentSession)`. Assert result is `Locked`.

   Use a fixed `Clock` (injectable via constructor or `JvmScope`) for deterministic time control.

   Run: `mvn -f client/pom.xml test -Dtest=IssueLockTest` — both new tests should FAIL (red).

2. **Step 2 (Implement fix):** Modify `IssueLock.acquire()`:

   When an existing lock file is found and belongs to a different session:
   - If the lock is stale (created_at older than 4 hours): overwrite with fresh lock (current session,
     current timestamp), return OK.
   - If the lock is NOT stale: return Locked (existing behavior).

   Inject a `Clock` for testability (use `JvmScope.getClock()` or constructor injection matching existing
   patterns in the codebase).

3. **Step 3 (Run all tests):** Run the full test suite:
   ```bash
   mvn -f client/pom.xml test
   ```
   All tests must pass (exit code 0). Both new tests should now pass (green).

## Post-conditions

- [ ] `acquireOverwritesStaleLockFromDifferentSession` test passes: stale lock from dead session is overwritten
- [ ] `acquireRejectsNonStaleLockFromDifferentSession` test passes: non-stale lock from active session is respected
- [ ] All existing `IssueLockTest.java` tests pass
- [ ] No regressions: `mvn -f client/pom.xml test` exits with code 0
- [ ] E2E: Simulate the M433 scenario — stale lock exists, new session acquires, `BlockUnsafeRemoval` scan
  sees fresh lock and does NOT delete the worktree
