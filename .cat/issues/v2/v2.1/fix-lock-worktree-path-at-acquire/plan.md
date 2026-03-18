# Plan: fix-lock-worktree-path-at-acquire

## Problem

Lock acquisition happens in `IssueDiscovery.findNextIssue()` with an empty worktree path (`""`), requiring a
separate `issueLock.update()` call in `WorkPrepare.executeWithLock()` after the worktree is created. This
two-step pattern leaves lock files with an empty `worktrees` map between acquisition and the update, which
triggers `"empty worktrees map"` validation errors on any subsequent re-entry.

## Parent Requirements

None

## Root Cause

`IssueDiscovery.findNextIssue()` cannot know the worktree path at discovery time because worktree path
computation (`buildIssueBranch` + `scope.getProjectCatDir().resolve("worktrees").resolve(issueBranch)`) lives
in `WorkPrepare`. The design forces a placeholder `""` to be passed to `acquire()`, producing a lock file
whose `worktrees` map is empty. A second step (`updateLockWorktree`) fills the path post-creation, but any
failure between acquire and update leaves an inconsistent lock on disk.

## Approaches

### Option A (Rejected): Pass pre-computed path from `WorkPrepare` into `IssueDiscovery`

`WorkPrepare` computes the worktree path before calling `discovery.findNextIssue()` and passes it via
`SearchOptions`. `IssueDiscovery` passes the path through to `issueLock.acquire()`.

**Why rejected:** `SearchOptions` currently holds discovery configuration, not output data. Injecting a
pre-computed worktree path blurs the responsibility boundary — `IssueDiscovery` would be receiving
`WorkPrepare`-specific infrastructure data, tightening coupling. It also requires changing `SearchOptions`
(a public record) and every call site.

### Option B (Chosen): Move lock acquisition from `IssueDiscovery` to `WorkPrepare.executeWithLock()`

`IssueDiscovery.findNextIssue()` stops acquiring locks entirely. `WorkPrepare` computes the worktree path
from `issueBranch` before calling `executeWithLock()`, and calls `issueLock.acquire(issueId, sessionId,
worktreePath)` inside `executeWithLock()` — before creating the worktree. The `update()` method and
`updateLockWorktree()` helper are removed as dead code.

**Why chosen:** Keeps lock lifecycle entirely in `WorkPrepare` (the only class that creates worktrees),
eliminates the two-step acquire-then-update, and ensures the lock file is correct the moment it is written.
`IssueDiscovery` becomes a pure scanner with no lock side-effects.

## Risk Assessment

**Medium.** The lock acquisition site changes, so `IssueDiscovery` tests that verify locked-issue skipping
must be updated. The existing `WorkPrepareTest` regression tests (`executeReleasesLockOnOversizedReturn`,
`executeReleasesLockOnCorruptReturn`) must still pass — they rely on `WorkPrepare` releasing the lock on
early returns, which is unchanged. The `IssueLock.update()` method and `handleUpdate` CLI handler are
deleted; removing them from `IssueLockCliTest` and `IssueLockTest` is needed. The `LockResult.Updated`
record is left in place (it is part of the sealed interface and is referenced in tests), but the `update()`
method that produces it is removed.

## Files to Modify

### `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`

1. **`findSpecificIssue()`** (around line 779): Remove the `issueLock.acquire()` call and the
   `LockResult.Locked` check that follows. Also remove the `issueLock.update()` call in the
   `ExistingWorktree` branch (~line 794). The method returns `DiscoveryResult.Found` or non-Found results
   without touching the lock.

2. **`searchForIssue()` inner loop** (around line 1132): Remove the `issueLock.acquire()` call and the
   `!(lockResult instanceof Acquired)` continue. Remove the `issueLock.release()` call in the
   existing-worktree early-continue block (~line 1141).

3. **Class-level field `issueLock`** (line 116): Remove the field declaration
   `private final IssueLock issueLock;` and remove its initialization in the constructor (`this.issueLock =
   new IssueLock(scope);`, line 154).

4. **Javadoc on the class** (line 37): Remove the sentence "and integrates with {@link IssueLock} for lock
   acquisition." Update the `@see` or `@link` reference if present.

5. **Import cleanup**: If `IssueLock` is no longer referenced in `IssueDiscovery`, remove its import.

### `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

1. **`execute()` method** (around line 246): Remove the comment `// Step 3 (lock acquisition) is handled
   implicitly by IssueDiscovery.findNextIssue()`.

2. **`executeWithLock()` signature and body** (lines 436–578): Add lock acquisition as the very first step,
   before worktree creation. Compute the worktree path deterministically from `issueBranch` before calling
   `createWorktree`:

   ```java
   Path worktreePath = scope.getProjectCatDir().resolve("worktrees").resolve(issueBranch);
   // Acquire lock with the pre-computed worktree path
   IssueLock issueLock = new IssueLock(scope);
   IssueLock.LockResult lockResult = issueLock.acquire(issueId, input.sessionId(), worktreePath.toString());
   if (lockResult instanceof IssueLock.LockResult.Locked locked)
   {
     return mapper.writeValueAsString(Map.of(
       "status", "LOCKED",
       "message", "Issue locked by another session: " + locked.owner(),
       "issue_id", issueId,
       "locked_by", locked.owner()));
   }
   ```

   Remove the `// Update lock with worktree path` block (lines 478–490) that calls
   `updateLockWorktree(issueId, input.sessionId(), worktreePath.toString())`.

   The `createWorktree(projectDir, issueBranch)` call at step 5 no longer needs to re-declare
   `worktreePath` — it is already declared above. Keep `createWorktree` with its existing signature
   (returning `Path`) and replace the existing `Path worktreePath = createWorktree(...)` assignment with
   `createWorktree(projectDir, issueBranch)` (discard the return value, since `worktreePath` is already
   bound). Do NOT add any assertion comparing the returned path with the pre-computed one.

   **Concrete sequence after refactor:**
   ```
   a. Compute worktreePath = scope.getProjectCatDir().resolve("worktrees").resolve(issueBranch)
   b. Acquire lock with worktreePath (fail fast if LOCKED)
   c. createWorktree(projectDir, issueBranch)  → returns same path
   d. Verify branch
   e. Check existing work
   f. Create/update STATE.md
   g. Build READY JSON
   ```

3. **`updateLockWorktree()` private helper** (lines 1609–1629): Delete the entire method.

4. **Javadoc on `executeWithLock()`**: Update to reflect that lock acquisition happens inside this method.

5. **Import cleanup**: Ensure `IssueLock` import is present (already needed for `releaseLock`).

### `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`

1. **`acquire()` parameter validation** (line 435): Change:
   ```java
   requireThat(worktree, "worktree").isNotNull();
   ```
   to:
   ```java
   requireThat(worktree, "worktree").isNotBlank();
   ```

2. **`acquire()` Javadoc** (line 426): Update `@param worktree` from "the worktree path (may be empty)" to
   "the worktree path (must not be blank)".

3. **`update()` method** (lines 616–655): Delete the entire `public LockResult update(...)` method.

4. **`LockResult.Updated` record**: Keep the record — it is part of the sealed interface permits clause and
   referenced in `IssueLockTest`. Just remove the method that produces it.

5. **`handleUpdate()` private static method** (lines 1055–1065): Delete the entire `handleUpdate` method.

6. **`run()` switch statement** (lines 997–1013): Remove the `case "update" -> handleUpdate(...)` arm.
   Update the usage error message to remove "update" from the command list:
   ```java
   "Usage: issue-lock <command> [args]. Commands: acquire, release, force-release, check, list"
   ```

7. **`main()` Javadoc** (lines 930–946): Remove the `{@code update <issue-id> <session-id> <worktree>}`
   line from the commands list.

8. **`writeLockToTempFile()` method** (lines 585–602): The conditional `if (!worktree.isBlank())` guard now
   always evaluates to true (because `acquire` now requires a non-blank worktree). The guard can remain for
   safety or be removed; leave it in place to avoid a behavior change — the lock file will always have one
   worktree entry.

## Test Cases

### `IssueLockTest.java` — changes

1. **`acquireSucceedsWhenNoLockExists()`**: Keep as-is (already passes a non-blank worktree path).

2. **`updateSucceedsWhenLockOwned()`**: Delete this test — `update()` is removed.

3. **`updateFailsWhenLockOwnedByAnotherSession()`**: Delete this test.

4. **`updateFailsWhenNoLockExists()`**: Delete this test.

5. **Add `acquirePopulatesWorktreesMapImmediately()`**: New test that verifies the lock file's `worktrees`
   map is populated right after `acquire()` returns without any separate `update()` call:
   ```
   - Call lock.acquire(issueId, sessionId, "/workspace/.cat/worktrees/2.1-my-issue")
   - Assert result is LockResult.Acquired
   - Read the lock file from disk and parse JSON
   - Assert worktrees map contains exactly one entry: {"/workspace/.cat/worktrees/2.1-my-issue": sessionId}
   ```

6. **`acquireRejectsBlankWorktreePath()`**: New test that verifies `acquire()` throws
   `IllegalArgumentException` when `worktree` is blank:
   ```java
   @Test(expectedExceptions = IllegalArgumentException.class,
     expectedExceptionsMessageRegExp = ".*worktree.*")
   public void acquireRejectsBlankWorktreePath() throws IOException
   ```

### `IssueLockCliTest.java` — changes

1. **`updateWithValidArgsWritesUpdatedToStdout()`** (line 160): Delete this test.

2. **Add `acquireWithWorktreeArg_populatesWorktreesMap()`**: Tests that `IssueLock.run(["acquire",
   issueId, sessionId, "/some/path"], scope, out)` produces JSON with `status=acquired` and the lock file
   on disk has the worktree path in the `worktrees` map.

### `WorkPrepareTest.java` — changes

1. **`executeReleasesLockOnOversizedReturn()`**: Keep without modification. Lock acquisition now happens
   inside `executeWithLock()` before worktree creation. The test verifies the lock file is absent after
   OVERSIZED return — this still holds because `executeWithLock()` calls `releaseLock()` before returning.
   **Note:** The OVERSIZED check happens before `executeWithLock()` is called. Verify whether the lock is
   acquired before or after the OVERSIZED check in the refactored flow. In the new design, lock acquisition
   moves to `executeWithLock()`, which is called after the OVERSIZED check. This means the lock is never
   acquired for OVERSIZED issues — `releaseLock()` is a no-op but the lock file is absent, so the test
   assertion (`lockFileAbsent`) still passes. Confirm the test logic remains correct.

2. **`executeReleasesLockOnCorruptReturn()`**: Similar analysis. CORRUPT check also happens before
   `executeWithLock()`. Lock is never acquired, file is absent. Test assertion still holds.

3. **Add `executeReadyLockContainsWorktreePath()`**: New test verifying that after a READY return, the lock
   file's `worktrees` map contains the worktree path that matches the `worktree_path` field in the JSON
   output:
   ```
   - Create a git+CAT project with one open issue
   - Call prepare.execute(input)
   - Assert status == READY
   - Read lock file from disk
   - Parse JSON from lock file
   - Assert worktrees map contains exactly one entry matching the worktree_path from the READY JSON
   ```

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Core refactor — `IssueLock.java`

- Remove `update()` method (lines 616–655)
- Remove `handleUpdate()` private static method (lines 1055–1065)
- Remove `case "update"` from `run()` switch (line 1000)
- Update usage error message string to omit "update"
- Update `main()` Javadoc to remove `update` command entry
- Change `acquire()` validation for `worktree` from `isNotNull()` to `isNotBlank()` (line 435)
- Update `acquire()` `@param worktree` Javadoc

### Wave 2: Core refactor — `IssueDiscovery.java`

- Remove `issueLock` field declaration and constructor initialization
- Remove `IssueLock` import if no longer referenced
- In `findSpecificIssue()`: remove the lock acquire block (~lines 777–785) and the `issueLock.update()`
  call in the `ExistingWorktree` branch (~line 793–794)
- In `searchForIssue()` inner loop: remove the lock acquire block (~lines 1130–1135) and the
  `issueLock.release()` call in the existing-worktree `continue` block (~line 1141)
- Update class Javadoc to remove lock acquisition reference

### Wave 3: Core refactor — `WorkPrepare.java`

File: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

- In `executeWithLock()` (lines 436–578): insert the following block as the very first statement of the
  method body, before any `createWorktree` call:
  ```java
  Path worktreePath = scope.getProjectCatDir().resolve("worktrees").resolve(issueBranch);
  // Acquire lock with the pre-computed worktree path
  IssueLock issueLock = new IssueLock(scope);
  IssueLock.LockResult lockResult = issueLock.acquire(issueId, input.sessionId(), worktreePath.toString());
  if (lockResult instanceof IssueLock.LockResult.Locked locked)
  {
    return mapper.writeValueAsString(Map.of(
      "status", "LOCKED",
      "message", "Issue locked by another session: " + locked.owner(),
      "issue_id", issueId,
      "locked_by", locked.owner()));
  }
  ```
- In `executeWithLock()`: replace the existing `Path worktreePath = createWorktree(projectDir, issueBranch)`
  assignment (which re-declares `worktreePath`) with a bare call `createWorktree(projectDir, issueBranch)`
  that discards the return value, since `worktreePath` is already declared above.
- Remove the `// Update lock with worktree path` try/catch block (~lines 478–490) that calls
  `updateLockWorktree(issueId, input.sessionId(), worktreePath.toString())`
- Remove the `updateLockWorktree()` private helper method (lines 1609–1629)
- In `execute()` (~line 246): remove the comment `// Step 3 (lock acquisition) is handled implicitly by IssueDiscovery.findNextIssue()`
- Update `executeWithLock()` Javadoc to state that lock acquisition happens as the first step inside this method

### Wave 4: Test updates

- `client/src/test/java/io/github/cowwoc/cat/hooks/util/IssueLockTest.java`:
  - Delete test methods `updateSucceedsWhenLockOwned`, `updateFailsWhenLockOwnedByAnotherSession`,
    and `updateFailsWhenNoLockExists`
  - Add test method `acquirePopulatesWorktreesMapImmediately` exactly as specified in "Test Cases §
    IssueLockTest.java item 5" above
  - Add test method `acquireRejectsBlankWorktreePath` exactly as specified in "Test Cases §
    IssueLockTest.java item 6" above (use `@Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*worktree.*")`)
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/IssueLockCliTest.java`:
  - Delete test method `updateWithValidArgsWritesUpdatedToStdout` (line 160)
  - Add test method `acquireWithWorktreeArg_populatesWorktreesMap` exactly as specified in "Test Cases §
    IssueLockCliTest.java item 2" above
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/WorkPrepareTest.java`:
  - Add test method `executeReadyLockContainsWorktreePath` exactly as specified in "Test Cases §
    WorkPrepareTest.java item 3" above
  - Leave `executeReleasesLockOnOversizedReturn` and `executeReleasesLockOnCorruptReturn` unmodified

### Wave 5: Build verification

- Run `mvn -f client/pom.xml test` and confirm exit code 0
- Fix any compilation errors or test failures

## Post-conditions

1. - [ ] Lock acquisition moved from `IssueDiscovery.findNextIssue()` to `WorkPrepare.executeWithLock()` —
         `IssueDiscovery` no longer calls `issueLock.acquire()`
2. - [ ] `IssueLock.acquire()` is called with the pre-computed worktree path (not empty string), so the
         lock file's `worktrees` map is populated immediately on lock creation
3. - [ ] `IssueLock.acquire()` validation changed from `isNotNull()` to `isNotBlank()` for the `worktree`
         parameter
4. - [ ] The `issueLock.update()` call (and `updateLockWorktree()` helper) after worktree creation is
         removed from `WorkPrepare.java`
5. - [ ] `IssueLock.update()` method and CLI `update` subcommand removed (dead code)
6. - [ ] Regression test `acquirePopulatesWorktreesMapImmediately` verifies the lock file contains the
         worktree path immediately after `acquire()` returns
7. - [ ] Existing tests `executeReleasesLockOnOversizedReturn` and `executeReleasesLockOnCorruptReturn`
         continue to pass
8. - [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
9. - [ ] E2E: run work-prepare and confirm the resulting lock file's `worktrees` map contains the planned
         path before any worktree exists on disk
