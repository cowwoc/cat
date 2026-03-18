# Plan: Enforce Exclusive Issue Locks Per Session

## Problem

When a session calls `issue-lock acquire` for a second different issue while still holding a lock for a
first issue, the acquire currently succeeds. This is incorrect: a session holding two simultaneous locks
causes subagents that inherit the session context to be confused about which worktree is active, leading
to file edits in the wrong location.

The fix must be in the Java `IssueLock.acquire()` method: before writing a new lock file, scan all
existing `.lock` files in `LOCKS_DIR` to check whether this `session_id` already appears as the owner
of a different issue. If so, return a structured error naming the conflicting issue ID.

## Parent Requirements

None

## Reproduction Code

```java
// Session S acquires lock for issue-a:
lock.acquire("issue-a", sessionId, "/worktree-a");  // -> Acquired

// Same session S tries to acquire lock for issue-b (a DIFFERENT issue):
lock.acquire("issue-b", sessionId, "/worktree-b");  // -> currently returns Acquired (WRONG)
                                                     // -> must return Error (CORRECT)
```

## Expected vs Actual

- **Expected:** `acquire("issue-b", sessionId, "...")` returns `LockResult.Error` with a message
  identifying `issue-a` as the conflicting lock held by this session.
- **Actual:** `acquire("issue-b", sessionId, "...")` returns `LockResult.Acquired` and creates a second
  lock file, leaving the session holding two simultaneous locks.

## Root Cause

`IssueLock.acquire()` in
`client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java` only checks the target issue's
lock file (`getLockFile(issueId)`). It never scans other lock files for the calling session_id. There is
no guard preventing a session from acquiring locks on multiple issues simultaneously.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Single-issue workflows (the normal case) are unaffected — the scan finds no prior
  lock for the session and proceeds as before. Only multi-lock sessions (currently a bug) are newly
  rejected.
- **Mitigation:** Comprehensive TDD test coverage of all four scenarios before implementation; Maven
  `verify` run to confirm no regressions.

## Impact Notes

Single-issue sessions (the normal case) are completely unaffected. Multi-lock sessions currently succeed
incorrectly and will intentionally fail after this change.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java` — add
  `scanForConflictingLock()` private helper and call it at the top of `acquire()` before the existing
  lock-file check
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockTest.java` — add four new test methods
  covering the conflict detection scenarios
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockCliTest.java` — add CLI-level test for
  the conflict error path

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add four failing tests to `IssueLockTest` that cover conflict detection:
  - `acquireFailsWhenSessionAlreadyHoldsLockForDifferentIssue` — session holds `issue-a`, tries to
    acquire `issue-b`; verify result is `LockResult.Error` with message containing `issue-a`
  - `acquireSucceedsAfterReleasingPriorLock` — session holds `issue-a`, releases it, then acquires
    `issue-b`; verify result is `LockResult.Acquired`
  - `acquireIsIdempotentForSameIssueEvenWithOtherLocksPresentForSameSession` — edge case: same-session,
    same-issue re-acquire is still idempotent (returns `Acquired`/already-held)
  - `acquireAllowsDifferentSessionsToHoldSeparateIssues` — two distinct sessions each hold a different
    issue; neither should block the other
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockTest.java`

- Add one CLI-level test to `IssueLockCliTest` covering the conflict error JSON output format:
  - `acquireConflictErrorOutputsCorrectJson` — verifies the JSON output contains `"status": "error"` and
    the conflicting issue ID in the message
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockCliTest.java`

- Run `mvn -f client/pom.xml test` to confirm tests FAIL (red phase of TDD)
  - Files: none (validation only)

### Wave 2

- Implement `scanForConflictingLock(String issueId, String sessionId)` private method in `IssueLock`:

  ```java
  /**
   * Scans all lock files to detect whether this session already holds a lock for a different issue.
   * <p>
   * Returns the conflicting issue ID if found, or empty string if none.
   *
   * @param issueId   the issue being acquired (excluded from the scan)
   * @param sessionId the session performing the acquire
   * @return the conflicting issue ID, or "" if no conflict exists
   * @throws IOException if the lock directory cannot be listed
   */
  private String scanForConflictingLock(String issueId, String sessionId) throws IOException
  {
    if (Files.notExists(lockDir))
      return "";
    List<Path> lockFiles;
    try (Stream<Path> stream = Files.list(lockDir))
    {
      lockFiles = stream
        .filter(path -> path.toString().endsWith(".lock"))
        .toList();
    }
    for (Path lockFile : lockFiles)
    {
      String fileName = lockFile.getFileName().toString();
      String candidateIssueId = fileName.substring(0, fileName.length() - ".lock".length());
      // Skip the lock file for the issue being acquired — same-issue re-acquire is handled separately
      if (sanitizeIssueId(issueId).equals(candidateIssueId))
        continue;
      JsonNode node = parseLockFile(lockFile);
      if (node == null)
        continue;
      // Check top-level session_id field
      JsonNode sessionNode = node.get("session_id");
      if (sessionNode != null && sessionNode.isString() && sessionNode.asString().equals(sessionId))
        return candidateIssueId;
      // Also check worktrees map values in case session_id diverged from worktrees entries
      JsonNode worktreesNode = node.get("worktrees");
      if (worktreesNode != null && worktreesNode.isObject())
      {
        for (JsonNode value : worktreesNode)
        {
          if (value.isString() && value.asString().equals(sessionId))
            return candidateIssueId;
        }
      }
    }
    return "";
  }
  ```

- Call `scanForConflictingLock` at the top of `acquire()`, immediately after parameter validation and
  before `Files.createDirectories(lockDir)`:

  ```java
  // Check if this session already holds a lock for a different issue.
  // A session may only hold one lock at a time to prevent subagents from
  // inheriting an ambiguous active-worktree context.
  String conflictingIssueId = scanForConflictingLock(issueId, sessionId);
  if (!conflictingIssueId.isEmpty())
  {
    return new LockResult.Error("error",
      "Session already holds a lock for issue '" + conflictingIssueId + "'. " +
      "Release that lock first before acquiring a lock for '" + issueId + "'.");
  }
  ```

  The insertion point in `acquire()` is:
  - After `validateSessionId(sessionId);` (line ~437)
  - Before `Files.createDirectories(lockDir);` (line ~439)

  Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`

- Run `mvn -f client/pom.xml test` to confirm all tests PASS (green phase of TDD)
  - Files: none (validation only)

- Run `mvn -f client/pom.xml verify` to confirm checkstyle and PMD pass
  - Files: none (validation only)

- Update `STATE.md` to `100%` complete
  - Files: `.cat/issues/v2/v2.1/enforce-exclusive-issue-locks/STATE.md`

## Post-conditions

- [ ] `IssueLock.acquire()` returns `LockResult.Error` with a message containing the conflicting issue
  ID when the calling session already holds a lock for a different issue
- [ ] `IssueLock.acquire()` remains idempotent for same-issue re-acquires (returns `Acquired` with
  "already held" message)
- [ ] `IssueLock.acquire()` succeeds when the session holds no prior locks (unchanged behavior)
- [ ] `IssueLock.acquire()` succeeds after the conflicting lock is released (release-then-acquire works)
- [ ] Two different sessions can each hold a separate issue lock without blocking each other
- [ ] All new test methods pass
- [ ] No regressions: existing `IssueLockTest` and `IssueLockCliTest` tests continue to pass
- [ ] `mvn -f client/pom.xml verify` exits 0 (includes checkstyle and PMD)
