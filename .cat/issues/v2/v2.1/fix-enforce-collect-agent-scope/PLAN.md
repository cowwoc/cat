# Plan: fix-enforce-collect-agent-scope

## Problem

`EnforceCollectAfterAgent` blocks ALL Task and Skill calls in any session where the `pending-agent-result`
flag file exists, even when that session is NOT inside a `/cat:work` workflow. If the flag persists (due to
a crash, premature session end, or any scenario where the flag is not cleaned up), the user's entire session
is blocked from using Task or Skill tools until they manually delete the flag file.

## Parent Requirements

None

## Reproduction Code

```
# 1. A /cat:work workflow runs and a subagent completes (creating the pending-agent-result flag)
# 2. Session ends WITHOUT collect-results-agent being called (flag is not cleared)
# 3. A NEW invocation of any skill (unrelated to /cat:work) is attempted in the same session
# 4. EnforceCollectAfterAgent blocks the call even though we are NOT in work-with-issue context
```

**Trigger path:**
- Session has `{sessionBasePath}/{sessionId}/pending-agent-result` flag file present
- User invokes any Skill or Task tool (e.g., `/cat:status`, `/cat:help`)
- `EnforceCollectAfterAgent.check()` reads the flag, finds it present, and blocks the call

## Expected vs Actual

- **Expected:** `EnforceCollectAfterAgent` only blocks when the session has an active worktree lock (the same
  condition that `SetPendingAgentResult` requires before creating the flag).
- **Actual:** `EnforceCollectAfterAgent` blocks any Task/Skill call in any session where the
  `pending-agent-result` flag exists, regardless of whether the session currently has an active worktree lock.

## Root Cause

`SetPendingAgentResult` correctly gates flag creation on `WorktreeContext.forSession()` returning non-null.
However, `EnforceCollectAfterAgent.check()` only checks whether `pending-agent-result` exists — it does NOT
re-verify that the session still has an active worktree lock. If the flag is stale (the worktree lock was
released or never existed), the hook still blocks all Task/Skill calls.

## Approaches

### A: Add WorktreeContext check to EnforceCollectAfterAgent (Chosen)

- **Risk:** LOW
- **Scope:** 2 files (implementation + test)
- **Description:** `EnforceCollectAfterAgent.check()` calls `WorktreeContext.forSession()` before blocking.
  If the worktree context is null (no active worktree lock for this session), the flag is stale — delete it
  and return `Result.allow()`. This mirrors the same guard used in `SetPendingAgentResult`.

**Why chosen:** Reuses existing `WorktreeContext` infrastructure, makes the invariant explicit ("flag only
matters when worktree lock is active"), handles stale flags automatically, requires changes in only one
class.

### B: work-context-active flag (Rejected)

- **Risk:** MEDIUM
- **Scope:** 3+ files (work-with-issue-agent skill, EnforceCollectAfterAgent, tests)
- **Description:** `work-with-issue-agent` sets a `work-context-active` flag at start and clears it at end.
  `EnforceCollectAfterAgent` only blocks if BOTH flags are present.

**Why rejected:** Requires coordinating two flag files across skill code (Markdown) and Java. The
`WorktreeContext` approach achieves the same result with less surface area.

### C: Only set pending-agent-result when work-context-active is present (Rejected)

- **Risk:** MEDIUM
- **Description:** Change `SetPendingAgentResult` to require a work-context-active flag before setting
  `pending-agent-result`.

**Why rejected:** `SetPendingAgentResult` already has equivalent guards (`isWorkExecute` + `WorktreeContext`
check). The bug is in the enforcement side (flag is read without re-validating context), not the setting
side. Approach A fixes the root cause directly.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** The added `WorktreeContext.forSession()` check will cause ALL EXISTING tests in
  `EnforceCollectAfterAgentTest` that test blocking behavior to fail — because existing tests create a flag
  file but do NOT create a lock file in `{projectCatDir}/locks/`. After the fix, `WorktreeContext.forSession()`
  returns null in all those tests and the flag is cleaned up. The tests must be updated to also create a
  worktree lock file when testing the blocking path.
- **Mitigation:** Update all existing tests that test blocking behavior to also create a matching lock file
  in `{projectCatDir}/locks/{issue_id}.lock` with the `session_id` field matching the test session ID. Also
  create the worktree directory at `{projectCatDir}/worktrees/{issue_id}/` (required by
  `WorktreeContext.forSession()` — it checks `Files.isDirectory(worktreePath)`).

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java` — add
  `WorktreeContext.forSession()` check after the flag-exists guard; if context is null, delete stale flag
  and return `Result.allow()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceCollectAfterAgentTest.java` — update ALL
  tests that test blocking behavior to create a lock file + worktree directory; add new regression test for
  stale-flag (no active worktree) scenario

## Test Cases

- [ ] Flag exists + active worktree lock → block (existing blocking behavior preserved)
- [ ] Flag exists + NO active worktree lock (stale flag) → allow, delete stale flag
- [ ] No flag → allow (existing behavior preserved)
- [ ] Flag + active lock + collect-results-agent skill → delete flag, allow (existing behavior preserved)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Modify `EnforceCollectAfterAgent.check()` to call `WorktreeContext.forSession()` after confirming the
  flag exists. If `WorktreeContext.forSession()` returns null, delete the flag file (to clean up stale
  state) and return `Result.allow()`. Place this check immediately after the existing
  `if (!Files.exists(flagPath)) return Result.allow();` early-return guard.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java`
  - **Exact insertion point:** after `if (!Files.exists(flagPath)) return Result.allow();` and before the
    `JsonNode skillNode = toolInput.get("skill");` line
  - **Required import to add:** `io.github.cowwoc.cat.hooks.WorktreeContext` (add to import block)
  - **Code to insert:**
    ```java
    WorktreeContext context = WorktreeContext.forSession(
      scope.getProjectCatDir(), scope.getClaudeProjectDir(), scope.getJsonMapper(), sessionId);
    if (context == null)
    {
      // No active worktree lock — flag is stale; clean up and allow
      try
      {
        Files.deleteIfExists(flagPath);
      }
      catch (IOException e)
      {
        log.warn("EnforceCollectAfterAgent: failed to delete stale flag file {}: {}", flagPath,
          e.getMessage());
      }
      return Result.allow();
    }
    ```

- Update ALL existing blocking tests in `EnforceCollectAfterAgentTest` to set up a valid worktree context.
  The tests that need updating are those that set up a flag file AND test blocking behavior:
  `flagExistsAndWorkMergeAgentIsBlocked`, `flagExistsAndTaskIsBlocked`, `blockReasonContainsRequiredElements`,
  `blockReasonContainsCompositeIdFormula`.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceCollectAfterAgentTest.java`
  - **What to add to each blocking test:**
    1. Create a unique `issueId` string (e.g., `"v2.1-test-issue"`)
    2. Create lock file directory: `Files.createDirectories(tempDir.resolve(".cat/work/locks"))`
    3. Write lock JSON to `tempDir.resolve(".cat/work/locks/" + issueId + ".lock")`:
       ```json
       {"session_id": "<sessionId>"}
       ```
       Where `<sessionId>` is the test's `sessionId` variable value.
    4. Create worktree directory: `Files.createDirectories(tempDir.resolve(".cat/work/worktrees/" + issueId))`
  - The `TestJvmScope(tempDir, tempDir)` constructor means `scope.getProjectCatDir()` returns
    `tempDir/.cat/work/` (verify by reading `TestJvmScope` — look for where `getProjectCatDir()` is
    computed). If `TestJvmScope` does not append `.cat/work`, adjust the lock file path accordingly.
  - **Verify the TestJvmScope path:** read
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java` to determine how
    `getProjectCatDir()` and `getClaudeProjectDir()` map to the constructor arguments before writing
    the lock setup code.

- Add new regression test `flagExistsButNoWorktreeLockAllows` to `EnforceCollectAfterAgentTest`:
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceCollectAfterAgentTest.java`
  - Test structure:
    1. Create temp dir, sessionId
    2. Create `TestJvmScope(tempDir, tempDir)`
    3. Create flag file (`createFlagFile(scope, sessionId)`)
    4. Do NOT create any lock file or worktree directory
    5. Invoke `handler.check(toolInput, sessionId, "")` with any skill that would normally be blocked
       (e.g., `cat:work-merge-agent`)
    6. Assert `result.blocked()` is `false`
    7. Assert flag file was deleted: `Files.notExists(scope.getSessionBasePath().resolve(sessionId).resolve("pending-agent-result"))`

- Tests that test ALLOW behavior with flag present (`flagExistsAndCollectResultsAgentDeletesFlagAndAllows`,
  `flagExistsAndMergeSubagentAgentDeletesFlagAndAllows`) do NOT need lock file setup — those tests return
  `allow` before the `WorktreeContext` check (because the skill is `collect-results-agent` or
  `merge-subagent-agent`, which triggers the early-return path that deletes the flag and allows). These
  tests remain unchanged.

### Wave 2

- Run all tests and verify they pass:
  ```bash
  mvn -f client/pom.xml test
  ```
  Fix any test failures before proceeding.
- Update the issue STATE.md: `Status: open → closed`, `Progress: 0% → 100%`
  - Files: `.cat/issues/v2/v2.1/fix-enforce-collect-agent-scope/STATE.md`

## Post-conditions

- [ ] Bug fixed: `EnforceCollectAfterAgent` only blocks Task/Skill calls when `WorktreeContext.forSession()`
  returns non-null for the current session (active worktree lock present)
- [ ] Stale-flag regression test added: `flagExistsButNoWorktreeLockAllows` passes and verifies that the
  hook returns `Result.allow()` and deletes the stale flag when no worktree lock is active
- [ ] Existing blocking tests updated to create lock file + worktree directory (so they still pass)
- [ ] No new issues introduced
- [ ] `mvn -f client/pom.xml test` exits 0
- [ ] E2E: Invoke a Skill tool in a session where `pending-agent-result` flag exists but no worktree lock
  file exists — confirm `EnforceCollectAfterAgent` returns allow and the flag is deleted
