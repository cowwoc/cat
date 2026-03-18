# Plan: Resume Work With Existing Worktree

## Goal

Update `/cat:work` (the `work-prepare` phase implemented in `WorkPrepare.java`) to detect when the
current session already holds the lock on an issue whose worktree directory already exists, and
resume seamlessly by returning a `READY` result with the existing worktree path — instead of
returning an `ERROR`.

## Parent Requirements

None

## Approaches

### A: Handle `ExistingWorktree` in `WorkPrepare.handleNonFoundResult`

- **Risk:** LOW
- **Scope:** 2 files (`WorkPrepare.java`, `WorkPrepareTest.java`)
- **Description:** When `handleNonFoundResult` encounters an `ExistingWorktree` result, check
  whether `input.sessionId()` owns the lock. If yes, bypass worktree creation and build the `READY`
  JSON directly from the existing worktree. If no, return ERROR as today.

### B: Handle `ExistingWorktree` in `IssueDiscovery` by returning `Found`

- **Risk:** MEDIUM
- **Scope:** 3+ files (changes `IssueDiscovery`'s sealed result hierarchy)
- **Description:** Promote `ExistingWorktree` to a `Found` variant inside `IssueDiscovery`.
  Requires modifying the sealed interface and all switch arms that handle it.

> **Selected: Approach A** — lowest risk, minimal surface area, no sealed-interface changes.
> The session ID is available in `WorkPrepare.execute` via `input.sessionId()`, and the lock can
> be checked with `issueLock.check(issueId)`.

## Research Findings

### Current Code Path

1. `WorkPrepare.execute` → `IssueDiscovery.findNextIssue`
2. `IssueDiscovery` calls `issueLock.acquire(issueId, sessionId, "")`:
   - Returns `LockResult.Acquired` when the lock is already held by the same session.
   - Returns `LockResult.Locked` when another session holds it (→ `NotExecutable`).
3. After a successful acquire, `IssueDiscovery` checks whether the worktree directory exists:
   - If it does → returns `DiscoveryResult.ExistingWorktree`.
   - If not → returns `DiscoveryResult.Found`.
4. `WorkPrepare.handleNonFoundResult` handles `ExistingWorktree` today with:
   ```java
   return mapper.writeValueAsString(Map.of(
     "status", "ERROR",
     "message", "Issue " + existingWorktree.issueId() + " has an existing worktree at: " +
       existingWorktree.worktreePath()));
   ```

### Key Types

- `IssueDiscovery.DiscoveryResult.ExistingWorktree` record fields:
  `issueId`, `major`, `minor`, `patch`, `issueName`, `issuePath`, `worktreePath`
- `IssueLock.check(issueId)` → `LockResult.CheckLocked` (when locked) or `CheckUnlocked`
- `IssueLock.CheckLocked` fields: `sessionId()`, `worktree()`, `ageSeconds()`
- Lock files live at `{projectCatDir}/locks/{issueId}.lock`

### READY JSON contract (from `executeWithLock`)

The `READY` response must include:
```
status, issue_id, major, minor, issue_name, issue_path (worktree-relative),
worktree_path, issue_branch, targetBranch, estimated_tokens, percent_of_threshold,
goal, preconditions, approach_selected, lock_acquired,
has_existing_work, existing_commits, commit_summary
```

### `buildIssueBranch` signature

```java
private String buildIssueBranch(String major, String minor, String patch, String issueName)
```

### Helper methods available in `WorkPrepare`

- `buildIssueBranch(major, minor, patch, issueName)` — builds the branch name string
- `estimateTokens(planPath)` — returns token count
- `IssueGoalReader.readGoalFromPlan(planPath)` — reads the `## Goal` section
- `readPreconditionsFromPlan(planPath)` — reads `## Pre-conditions` items
- `ExistingWorkChecker.check(worktreePath, targetBranch)` — checks for existing commits
- `checkTargetBranchCommits(projectDir, targetBranch, issueName, planPath)` — suspicious commit check
- `GitCommands.getCurrentBranch(projectDir.toString())` — reads current branch

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The `ExistingWorktree` result carries all fields needed to build the READY response
  except `estimatedTokens` and existing-work metadata — these can be recomputed.
- **Mitigation:** Recompute `estimatedTokens`, `goal`, `preconditions`, `existingWork`, and
  `suspiciousCommits` from the existing worktree path using the same helpers already used in
  `executeWithLock`.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  - Modify `handleNonFoundResult` to accept `PrepareInput input` as a parameter.
  - When result is `ExistingWorktree`: check the lock owner. If it matches `input.sessionId()`,
    call `resumeWithExistingWorktree(input, existingWorktree, projectDir, mapper)`.
  - Add private method `resumeWithExistingWorktree` that builds the READY JSON.
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
  - Add `executeReturnsReadyWhenSessionOwnsLockAndWorktreeExists` test.
  - Add `executeReturnsErrorWhenDifferentSessionOwnsLockAndWorktreeExists` test (confirm no
    regression: another session's existing worktree still returns LOCKED, not ERROR, because the
    lock check in `IssueDiscovery` fires first and returns `NotExecutable` before `ExistingWorktree`
    can be reached).

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Write failing tests `executeReturnsReadyWhenSessionOwnsLockAndWorktreeExists` in
  `WorkPrepareTest.java` (TDD — write test first, verify it fails, then implement).
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
- Implement the resume logic in `WorkPrepare.java`:
  1. Add `PrepareInput input` parameter to `handleNonFoundResult` (update the single call site in
     `execute`).
  2. In the `ExistingWorktree` branch of `handleNonFoundResult`:
     a. Read the lock: `IssueLock.LockResult check = issueLock.check(existingWorktree.issueId())`.
     b. If `check instanceof IssueLock.LockResult.CheckLocked cl && cl.sessionId().equals(input.sessionId())`:
        - Call `resumeWithExistingWorktree(input, existingWorktree, projectDir, mapper)`.
     c. Otherwise: keep the current ERROR response.
  3. Add `private String resumeWithExistingWorktree(PrepareInput input,
     IssueDiscovery.DiscoveryResult.ExistingWorktree existing, Path projectDir, JsonMapper mapper)`
     which:
     - Derives `issueBranch` via `buildIssueBranch(existing.major(), existing.minor(),
       existing.patch(), existing.issueName())`.
     - Derives `targetBranch` via `GitCommands.getCurrentBranch(projectDir.toString())`.
     - Reads `planPath` from `Path.of(existing.issuePath()).resolve("PLAN.md")` — note:
       `existing.issuePath()` is the **main workspace** issue directory (not inside the worktree),
       so the plan file must be read from the worktree path instead:
       `Path.of(existing.worktreePath()).resolve(projectDir.relativize(Path.of(existing.issuePath())).toString()).resolve("PLAN.md")`.
     - Calls `estimateTokens(planPath)`.
     - Returns OVERSIZED JSON if token limit exceeded.
     - Calls `ExistingWorkChecker.check(existing.worktreePath(), targetBranch)`.
     - Calls `checkTargetBranchCommits(projectDir, targetBranch, existing.issueName(), planPath)`.
     - Calls `IssueGoalReader.readGoalFromPlan(planPath)`.
     - Calls `readPreconditionsFromPlan(planPath)`.
     - Builds and returns the READY JSON with `worktree_path = existing.worktreePath()`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- Run `mvn -f client/pom.xml test` to verify all tests pass.
  - Files: (build only)

## Post-conditions

- [ ] `WorkPrepareTest.executeReturnsReadyWhenSessionOwnsLockAndWorktreeExists` passes: when the
  current session owns the lock and the worktree directory already exists, `execute` returns
  `status = READY` with the existing `worktree_path`.
- [ ] `WorkPrepareTest.executeReturnsLockedWhenIssueIsLockedAndWorktreeExists` still passes (no
  regression: another session locking an issue with an existing worktree still returns `LOCKED`).
- [ ] All existing `WorkPrepareTest` tests pass.
- [ ] `mvn -f client/pom.xml test` exits with code 0.
- [ ] E2E: Running `/cat:work resume <issue-id>` (or `/cat:work <issue-id>`) when the same session
  already holds the lock and a worktree exists proceeds directly to the implement/confirm/review/merge
  phases without prompting to clean up the worktree.
