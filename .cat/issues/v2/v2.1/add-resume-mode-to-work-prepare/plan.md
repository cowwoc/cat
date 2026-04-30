# Plan: add-resume-mode-to-work-prepare

## Goal

Add a `--resume` flag to the `work-prepare` CLI that enables cross-session issue resume. When
`--resume` is passed and an existing worktree is present, `work-prepare` force-acquires the lock
for the current session (regardless of who held it previously) and returns READY using the
existing worktree. Without `--resume`, orphaned worktrees continue to return ERROR unchanged.

## Parent Requirements

None

## Research Findings

- `WorkPrepare.java` lines 400–423: `ExistingWorktree` is detected when the worktree directory
  exists. The lock check is read-only (`issueLock.check()`). READY is only returned when the
  current session already owns the lock. Without `--resume` there is no code path that acquires
  the lock for a new session when a worktree exists.
- `IssueLock.acquire()` handles stale locks (4+ hours) automatically via `overwriteWithFreshLock()`
  but will not override a fresh lock. `IssueLock.forceRelease()` removes any lock unconditionally.
- Atomic lock replacement approach: `forceRelease(issueId)` then `acquire(issueId, sessionId,
  worktreePath)`. The window between the two calls is narrow; a concurrent session acquiring in
  that window would be immediately overridden by the subsequent `acquire`. For the resume use case
  (user-initiated, non-automated) this is acceptable.
- `PrepareInput` is the record holding parsed CLI arguments. The `resume`/`continue` prefix is
  currently stripped and discarded in `parseRawArguments()`. The fix preserves the prefix as a
  boolean field `resume` in `PrepareInput` instead of silently discarding it.
- `work-prepare` is invoked by the skill via:
  `"${CLAUDE_PLUGIN_DATA}/client/bin/work-prepare" --arguments "${ARGUMENTS}"`
  The `${ARGUMENTS}` string is the raw user input (e.g., `"resume 2.1-my-issue"`). The change
  makes the `resume`/`continue` prefix functional rather than cosmetic.

## Approaches

### A: Reuse prefix stripping as a functional flag (chosen)
- **Risk:** LOW
- **Scope:** 3 files (WorkPrepare.java, WorkPrepareTest.java, plugin/skills/work/first-use.md)
- **Description:** Capture the `resume`/`continue` prefix in `PrepareInput.resume` boolean instead
  of discarding it. In `handleNonFoundResult`, when `ExistingWorktree` is found and `resume=true`,
  force-release the stale lock and acquire a fresh one for the current session, then return READY.

### B: New `--resume` top-level CLI flag
- **Risk:** MEDIUM
- **Scope:** 5 files (argument parsing, PrepareInput, WorkPrepare, skill, tests)
- **Description:** Add a separate `--resume` flag parsed before `--arguments`. Requires updating
  the skill invocation to thread the flag separately. More mechanical complexity for no benefit
  over Approach A.

> Approach A chosen: it leverages the already-parsed prefix detection with minimal new code.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Race condition between `forceRelease` and `acquire` when another session tries to
  claim the same issue concurrently.
- **Mitigation:** This is a user-initiated operation (user explicitly says "resume"). Two users
  concurrently resuming the same issue is not a realistic scenario. Existing multi-instance lock
  safety documentation acknowledges this pattern.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  - Add `resume` boolean field to `PrepareInput` record
  - In `parseRawArguments()`: capture `resume`/`continue` prefix as `resume=true` instead of
    discarding it
  - In `handleNonFoundResult()`: add `issueId` field to the ERROR response for `ExistingWorktree`
    (currently lines ~419-422 produce `Map.of("status", "ERROR", "message", "...")` without an
    `issueId` field). Update to:
    `mapper.writeValueAsString(Map.of("status", "ERROR", "message", "Issue " + existingWorktree.issueId() + " has an existing worktree at: " + existingWorktree.worktreePath(), "issueId", existingWorktree.issueId()))`
    This change is required so that `first-use.md` can extract the `issueId` to construct the
    `resume <issueId>` argument.
  - In `handleNonFoundResult()`: when `ExistingWorktree` and `input.resume() == true`:
    - Call `IssueLock.LockResult forceResult = issueLock.forceRelease(existingWorktree.issueId())`
    - If `forceResult instanceof IssueLock.LockResult.Error forceError`: return
      `mapper.writeValueAsString(Map.of("status", "ERROR", "message", "Failed to release existing lock: " + forceError.message(), "issueId", existingWorktree.issueId()))`
    - Call `IssueLock.LockResult acquireResult = issueLock.acquire(existingWorktree.issueId(), input.sessionId(), existingWorktree.worktreePath().toString())`
    - If `acquireResult instanceof IssueLock.LockResult.Locked locked`: return
      `mapper.writeValueAsString(Map.of("status", "LOCKED", "message", "Issue " + existingWorktree.issueId() + " was locked by another session during resume", "issueId", existingWorktree.issueId(), "lockedBy", locked.sessionId()))`
      (mirrors the existing LOCKED pattern at lines ~413-417)
    - Otherwise (`Acquired`): return `resumeWithExistingWorktree(existingWorktree, projectPath, mapper)`
  - If `input.resume() == true` but discovery result is NOT `ExistingWorktree` (no worktree
    exists): do NOT intercept — let the normal flow continue, which either creates a new worktree
    for `Found` issues or returns appropriate errors for other cases. The no-worktree ERROR is
    naturally handled because `Found` issues create a fresh worktree normally.
  - Note: when `resume=true` and the issue returns `Found` (no existing worktree), treat as normal
    new-issue flow — the user simply wanted to pick up their issue but no prior worktree existed.
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
  - Add test: `executeReturnsReadyWhenResumeAndDifferentSessionOwnsLock` — verifies READY is
    returned and the lock owner is replaced with the current session.
  - Add test: `executeReturnsReadyWhenResumeAndNoLockExists` — existing worktree, no lock owner,
    `resume=true` → READY.
  - Add test: `executeReturnsReadyWhenResumeAndCurrentSessionOwnsLock` — resume when current
    session already owns lock → READY (unchanged behavior, no regression).
  - Add test: `executeReturnsReadyWhenResumeAndNoWorktreeExists` — `resume=true` but no worktree
    → normal new-issue READY (creates worktree fresh).
  - Add test: `executeReturnsErrorWhenNoResumeAndOrphanedWorktree` — without `resume`, existing
    orphaned worktree → ERROR (regression guard for existing behavior).
- `plugin/skills/work/first-use.md`
  - Section **ERROR: Existing Worktree Handling** (starts at the line `**ERROR: Existing Worktree Handling:**`) — make
    the following changes:
    1. Replace option `"Resume on existing worktree" (retry work-prepare immediately — see below)` with:
       `"Resume on existing worktree" (only offered when user explicitly said resume/continue — see below)`
    2. Replace step 3 body (currently: "IMMEDIATELY retry work-prepare using the same subprocess invocation ...
       `"${CLAUDE_PLUGIN_DATA}/client/bin/work-prepare" --arguments "${ARGUMENTS}"`") with:
       - Extract `issueId` from the `"issueId"` field of the ERROR JSON returned by the first
         work-prepare invocation (the field is now guaranteed to be present in ERROR responses for
         existing worktrees after the WorkPrepare.java change above).
       - Invoke: `"${CLAUDE_PLUGIN_DATA}/client/bin/work-prepare" --arguments "resume ${issueId}"`
       - Parse the result and resume Phase 1 error handling logic.
    3. Add a constraint block before step 3: **"Resume on existing worktree" must only be presented
       as an option if the user's original invocation explicitly contained a resume/continue keyword
       (e.g., `resume 2.1-my-issue`, `continue 2.1-my-issue`). If the user did NOT use resume/continue,
       omit this option entirely from the AskUserQuestion and only present "Clean up and retry" and "Abort".**

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Write failing tests for the new resume behavior in `WorkPrepareTest.java` (TDD):
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
- Implement `resume` field in `PrepareInput` and update `parseRawArguments()` to capture the
  prefix as `resume=true`:
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- Implement the `ExistingWorktree` + `resume=true` path in `handleNonFoundResult()`:
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- Run `mvn -f client/pom.xml test` to confirm all tests pass

### Wave 2
- Update `plugin/skills/work/first-use.md` — the ERROR: Existing Worktree Handling section:
  - Change "Resume on existing worktree" option to pass `resume <issueId>` in ARGUMENTS
  - Add constraint that this option is only presented when user explicitly said resume
  - Files: `plugin/skills/work/first-use.md`
- Update `index.json` to set status closed
  - Files: `.cat/issues/v2/v2.1/add-resume-mode-to-work-prepare/index.json`

## Post-conditions
- [ ] `work-prepare --arguments "resume <issueId>"` returns READY when the issue has an existing
      worktree (atomically replaces any stale lock with current session lock)
- [ ] `work-prepare --arguments "<issueId>"` (no resume prefix) continues to return ERROR for
      orphaned worktrees (existing behavior preserved)
- [ ] `work-prepare --arguments "resume <issueId>"` when no worktree exists returns READY by
      creating a fresh worktree (normal new-issue flow)
- [ ] `plugin/skills/work/first-use.md` documents that "Resume on existing worktree" is only
      offered when the user explicitly said resume/continue
- [ ] All new tests pass: resume with stale lock, resume with no lock, resume with current session
      lock, normal mode with orphaned worktree (ERROR preserved)
- [ ] All existing `WorkPrepareTest` tests pass (no regressions)
- [ ] `mvn -f client/pom.xml test` exits 0
- [ ] E2E: `/cat:work resume 2.1-my-issue` on an issue with an orphaned worktree returns READY
      and the agent can proceed with implementation using the existing worktree and branch
