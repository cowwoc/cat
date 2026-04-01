# Plan: fix-session-end-lock-deletion

## Problem
`SessionEndHook` deletes task locks owned by the current session when the session ends. This prevents lock
preservation across session boundaries when using `--resume <sessionId>`: the issue lock is deleted by the
previous session's cleanup hook, so the resumed session can no longer find or release it.

## Root Cause
`SessionEndHook.cleanTaskLocks()` iterates all `*.lock` files in the locks directory and deletes any whose
`session_id` field matches the current session. This runs at every session end, including when the session
will be resumed with the same session ID.

## Expected vs Actual
- **Expected:** Issue locks survive session end so that `--resume <sessionId>` can resume work with the
  same lock still held.
- **Actual:** `cleanTaskLocks()` deletes the lock at session end, causing the resumed session to fail to
  find its lock.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Tests that verify current-session lock deletion will fail — they must be removed.
  Stale lock cleanup (24h threshold) is unaffected.
- **Mitigation:** Keep `cleanStaleLocks()` (24h stale lock removal). Add test to verify lock preservation.
  Run full build to catch regressions.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java` — remove methods, simplify
  `run()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHookTest.java` — remove obsolete
  tests, update remaining call sites, add lock-preservation test

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Jobs
- /cat:tdd-implementation-agent goal="SessionEndHook must not delete current-session task locks at session end"

## Jobs

### Job 1
- In `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java`:
  - Remove these methods: `removeProjectLock()`, `cleanTaskLocks()`, `isLockOwnedBySession()`,
    `cleanLocksInDirectory()`
  - Remove `runWithProjectDir(ClaudeHook scope, Path projectPath)` method (it was only used to pass a
    custom project path for testing `removeProjectLock()`, which is removed)
  - Rewrite `run(ClaudeHook scope)` to directly contain the cleanup logic (previously delegated to
    `runWithProjectDir`):
    - Get `sessionId` from `this.scope.getSessionId()`
    - Create `messages` list
    - Call `cleanStaleLocks(messages)`
    - Call `new SessionEndHandler(this.scope).clean(sessionId)`
    - Return `new HookResult(scope.empty(), messages)`
    - Wrap in try/catch for `Exception` returning error message in messages list
  - Update class-level Javadoc: the lock-cleanup bullet should only describe stale lock removal (older
    than 24 hours); remove mentions of project lock file and current-session task locks
  - Remove imports that are no longer used after deleting the removed methods. The following are still
    needed by `cleanStaleLocks()`: `DirectoryStream`, `Files`, `Path`, `BasicFileAttributes`, `Instant`,
    `ArrayList`, `List`. Verify that no other imports become unused.
- In `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHookTest.java`:
  - Remove these test methods (they test removed behavior):
    - `projectLockRemoved()` — tests `removeProjectLock()` which is removed
    - `taskLocksRemovedForSession()` — tests `cleanTaskLocks()` which is removed
    - `nonMatchingSessionIdSkipsLockCleaning()` — tests session-ID matching in `isLockOwnedBySession()`
    - `multipleLocksOnlyCorrectPreserved()` — tests that only the matching session's lock is deleted
    - `runUsesGetProjectPathForLockFileName()` — tests project lock deletion via `run()`
    - `nullProjectPathThrowsException()` — tests null `projectPath` validation in `runWithProjectDir()`
    - `ioExceptionReadingLockFileHandled()` — tests IOException in `isLockOwnedBySession()`
    - `projectLockDeletionErrorHandledGracefully()` — tests graceful handling of project lock deletion
  - Update these test methods that call `runWithProjectDir(scope, tempDir)` — change to `run(scope)`:
    - `staleLocksRemoved()`: `new SessionEndHook(scope).runWithProjectDir(scope, tempDir)` →
      `new SessionEndHook(scope).run(scope)`
    - `twentyFourHourBoundaryRespected()`: same replacement
    - `nonexistentLockDirectoryHandledGracefully()`: same replacement
    - `staleLockAttributeReadErrorHandledGracefully()`: same replacement
  - Add new test method `currentSessionLockPreservedAfterSessionEnd()`:
    - Javadoc: "Verifies that task locks owned by the current session are NOT removed at session end."
    - Construct `scope` using `new TestClaudeHook(sessionPayload("test-session"), tempDir)` (same
      pattern as the existing stale-lock tests that will remain after cleanup)
    - Creates a `locks/tasks/` subdirectory inside the temp dir, writes a lock file named
      `test-issue.lock` with content `{"session_id": "test-session"}`
    - Calls `new SessionEndHook(scope).run(scope)`
    - Asserts the lock file still exists after the hook runs (i.e., `Files.exists(lockFile)` is true)
  - Update class-level Javadoc to remove mention of task lock and project lock removal from the class
    description
  - Remove `sessionPayload()` helper method ONLY IF no remaining test methods use it. Check whether
    the constructor `new TestClaudeHook(sessionPayload("test-session"), ...)` is still used by any
    remaining tests — if yes, keep `sessionPayload()`; if no remaining test uses it, remove it.
- Update `index.json` in the same commit: set `"status": "closed"`, `"progress": 100`
- Run `mvn -f client/pom.xml verify -e` and fix all failures before committing

## Post-conditions
- [ ] `SessionEndHook.cleanTaskLocks()` removed
- [ ] `SessionEndHook.isLockOwnedBySession()` removed
- [ ] `SessionEndHook` only removes stale locks older than 24 hours (not current-session locks)
- [ ] Tests updated/removed as needed
- [ ] `--resume <sessionId>` preserves issue locks across session boundaries
- [ ] E2E verification: acquire lock, shut down, resume same session, lock still owned
