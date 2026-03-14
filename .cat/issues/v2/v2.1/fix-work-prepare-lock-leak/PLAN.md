# Plan: fix-work-prepare-lock-leak

## Problem

`WorkPrepare.execute()` acquires a lock via `IssueDiscovery.findNextIssue()` (which calls
`issueLock.acquire(issueId, sessionId, "")` with an empty worktree path). When the found issue is
rejected as `OVERSIZED` (lines 279-287) or `CORRUPT` (lines 260-268), those early-return paths return
directly without releasing the lock. The persisted lock file contains an empty `worktrees` map `{}`.
On any subsequent `work-prepare` call for the same issue, `IssueLock.acquire()` detects the empty
`worktrees` map, treats it as corruption, and throws `IOException`, permanently blocking all future
`work-prepare` calls until force-released via `/cat:cleanup`.

## Parent Requirements

- None

## Reproduction Code

```java
// 1. Create an issue whose PLAN.md exceeds TOKEN_LIMIT tokens
// 2. Call WorkPrepare.execute() — returns OVERSIZED, but lock file is left on disk
// 3. Call WorkPrepare.execute() again — throws IOException from IssueLock.acquire()
//    "Lock file for issue 'X' has an empty worktrees map"

PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);
String result1 = prepare.execute(input);  // returns OVERSIZED, lock leaked
String result2 = prepare.execute(input);  // throws IOException — unexpected
```

## Expected vs Actual

- **Expected:** After an OVERSIZED or CORRUPT early return, the lock file is removed and subsequent
  `work-prepare` calls succeed.
- **Actual:** The lock file persists with an empty `worktrees` map. The next call throws `IOException`:
  `"Lock file for issue '<id>' has an empty worktrees map: <path>. Delete the lock file or run /cat:cleanup."`

## Root Cause

`WorkPrepare.execute()` has two early-return paths (CORRUPT at lines 260-268, OVERSIZED at lines
279-287) that execute after the lock is acquired by `IssueDiscovery.findNextIssue()` but before
reaching `executeWithLock()` where all other error paths call `releaseLock()`. The helper method
`releaseLock(issueId, sessionId)` already exists in `WorkPrepare` (around line 1626) for exactly this
purpose — it just isn't called in these two paths.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal — the fix adds two `releaseLock()` calls in paths that currently leave
  a lock file on disk. No existing code relies on the lock persisting after an OVERSIZED/CORRUPT return.
- **Mitigation:** New regression tests verify lock cleanup after both early returns. Existing tests
  continue to pass.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — add `releaseLock()` call
  before the CORRUPT return (around line 268) and before the OVERSIZED return (around line 287)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add two regression
  tests: one for OVERSIZED lock cleanup, one for CORRUPT lock cleanup

## Test Cases

- [ ] OVERSIZED return releases the lock (no lock file remains after the call returns)
- [ ] CORRUPT return releases the lock (no lock file remains after the call returns)
- [ ] After OVERSIZED return, a second `work-prepare` call on the same issue does not throw
- [ ] After CORRUPT return, a second `work-prepare` call on the same issue does not throw

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add `releaseLock(issueId, input.sessionId())` before the CORRUPT return in
  `WorkPrepare.execute()`.
  - File: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  - Location: In the `if (found.isCorrupt())` block (around line 260). The lock was acquired only
    when `sessionId` is non-empty (see `IssueDiscovery.findNextIssue()` line 777:
    `if (!options.sessionId().isEmpty())`), so guard the release call with the same condition. Add
    the guarded call immediately before `return mapper.writeValueAsString(corruptResult)`. Exact
    replacement — replace the current `if (found.isCorrupt())` block:

    ```java
    if (found.isCorrupt())
    {
      if (!input.sessionId().isEmpty())
        releaseLock(issueId, input.sessionId());
      Map<String, Object> corruptResult = new LinkedHashMap<>();
      corruptResult.put("status", "CORRUPT");
      corruptResult.put("issue_id", issueId);
      corruptResult.put("issue_path", issuePath.toString());
      corruptResult.put("message", "Issue directory is corrupt: STATE.md exists but PLAN.md is " +
        "missing at " + issuePath);
      return mapper.writeValueAsString(corruptResult);
    }
    ```

- Add `releaseLock(issueId, input.sessionId())` before the OVERSIZED return in
  `WorkPrepare.execute()`.
  - File: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  - Location: In the `if (estimatedTokens > TOKEN_LIMIT)` block (around line 279). Guard with
    `!input.sessionId().isEmpty()`. Exact replacement — replace the current
    `if (estimatedTokens > TOKEN_LIMIT)` block:

    ```java
    if (estimatedTokens > TOKEN_LIMIT)
    {
      if (!input.sessionId().isEmpty())
        releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "OVERSIZED",
        "message", "Issue estimated at " + estimatedTokens + " tokens (limit: " + TOKEN_LIMIT + ")",
        "suggestion", "Use /cat:decompose-issue to break into smaller issues",
        "issue_id", issueId,
        "estimated_tokens", estimatedTokens));
    }
    ```

- Add two regression tests to `WorkPrepareTest`:
  - File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
  - Insert immediately after the existing `executeReturnsCorruptWhenStateMdExistsButNoPlanMd` test
    (after its closing brace, around line 176).

  **Test 1 — OVERSIZED releases the lock:**

  ```java
  /**
   * Verifies that execute releases the lock when returning OVERSIZED.
   * <p>
   * After an OVERSIZED return the lock file must not remain on disk, so that a
   * subsequent work-prepare call does not fail with "empty worktrees map".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReleasesLockOnOversizedReturn() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "huge-feature", "open");
      createOversizedPlan(projectDir, "2", "1", "huge-feature");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add oversized issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("OVERSIZED");

      // The lock file must not remain after an OVERSIZED early return
      Path lockFile = scope.getProjectCatDir().resolve("locks").resolve("2.1-huge-feature.lock");
      requireThat(Files.exists(lockFile), "lockFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }
  ```

  **Test 2 — CORRUPT releases the lock:**

  ```java
  /**
   * Verifies that execute releases the lock when returning CORRUPT.
   * <p>
   * After a CORRUPT return the lock file must not remain on disk, so that a
   * subsequent work-prepare call does not fail with "empty worktrees map".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReleasesLockOnCorruptReturn() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create issue directory with only STATE.md (no PLAN.md) — simulates a corrupt directory
      Path issueDir = projectDir.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("corrupt-issue");
      Files.createDirectories(issueDir);

      String stateContent = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;
      Files.writeString(issueDir.resolve("STATE.md"), stateContent);
      // Deliberately no PLAN.md — this is the corrupt condition

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add corrupt issue directory");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("CORRUPT");

      // The lock file must not remain after a CORRUPT early return
      Path lockFile = scope.getProjectCatDir().resolve("locks").resolve("2.1-corrupt-issue.lock");
      requireThat(Files.exists(lockFile), "lockFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }
  ```

- Run all tests to verify no regressions.
  - Command: `mvn -f client/pom.xml test`

## Post-conditions

- [ ] Bug fixed: Lock is released on OVERSIZED early return (no lock file remains after early return)
- [ ] Bug fixed: Lock is released on CORRUPT early return (no lock file remains after early return)
- [ ] Regression test added: `executeReleasesLockOnOversizedReturn` passes
- [ ] Regression test added: `executeReleasesLockOnCorruptReturn` passes
- [ ] No new issues: Multiple `work-prepare` calls on an oversized issue do not accumulate locks
- [ ] E2E: After OVERSIZED rejection, a second `work-prepare` call on the same issue returns OVERSIZED
  (not IOException)
- [ ] E2E: After CORRUPT rejection, a second `work-prepare` call on the same issue returns CORRUPT
  (not IOException)
- [ ] All tests pass: `mvn -f client/pom.xml test` exits 0
