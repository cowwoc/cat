# Plan

## Goal

Fix two related bugs in `work-prepare` that together allow silent cross-session worktree resumption without user
confirmation and without proper lock handoff:

1. **Bug 1 — Silent resume without permission:** When an existing worktree is found and its lock is held by a
   foreign session (a different session ID than the calling session), `WorkPrepare.handleNonFoundResult()` returns
   `LOCKED` status. The `work-agent` skill interprets `LOCKED` as "pick a different issue" and stops silently.
   However, the foreign session may be a previous context-exhausted session that the user expects to continue.
   The user must be shown a confirmation dialog with lock-holder info (session ID, age) before work proceeds.

2. **Bug 2 — Missing lock handoff on resume:** Even if work were resumed, `resumeWithExistingWorktree()` sets
   `lock_acquired: true` in its output without actually acquiring a new lock for the calling session. The lock
   remains owned by the foreign session. When the issue is later merged, `issue-lock release` fails silently
   because the releasing session doesn't own the lock, leaving a stale lock file until `/cat:cleanup` runs.

## Research Findings

The following source locations are relevant:

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` (lines 406-429):
  `handleNonFoundResult()` — the broken ExistingWorktree + foreign-session-lock branch that returns `LOCKED`
  instead of `ERROR`. The `IssueLock.CheckLocked` record (returned by `issueLock.check()`) already carries
  `sessionId` and `ageSeconds` — both needed for the user-facing error message.

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` (lines 629-700+):
  `resumeWithExistingWorktree()` — sets `lock_acquired: true` but does not call `IssueLock.acquire()`.
  The lock handoff must be done before this method is called in the foreign-session resume path.

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`:
  `CheckLocked` record already has `sessionId` and `ageSeconds` fields. `forceRelease()` deletes the lock
  regardless of owning session. `acquire()` atomically writes a new lock file for the given session.

- `plugin/skills/work-prepare-agent/first-use.md` (lines 319-324): The skill currently says "Lock file
  present: Treat as locked by another session. Return LOCKED status. Do NOT investigate... Pick a different
  issue." This instruction must be revised to show a confirmation dialog instead.

## Pre-conditions

(none)

## Post-conditions

- [ ] When `work-prepare` detects an existing worktree locked by a foreign session, it returns `ERROR` status
  (not `LOCKED`) with fields: `issue_id`, `message`, `locked_by` (foreign session ID), `lock_age_seconds`
  (seconds since lock was created), and `worktree_path`
- [ ] The `work-prepare-agent` skill recognizes the `ERROR` + `locked_by` case and presents a confirmation
  dialog showing the foreign session ID and lock age before allowing resume
- [ ] On "Resume", the skill calls `issue-lock force-release` on the foreign session's lock and then
  `issue-lock acquire` for the current session before returning READY with the existing worktree
- [ ] After a cross-session resume and subsequent merge, `issue-lock release` succeeds because the current
  session now owns the lock
- [ ] Unit test added in `WorkPrepareTest` covering the new `ERROR` response format when ExistingWorktree is
  locked by a foreign session ID
- [ ] Existing behavior for same-session resume (locked by current session → READY) is unchanged
- [ ] Existing behavior for unlocked existing worktree (ERROR with existing worktree message, no `locked_by`
  field) is unchanged — the skill continues to handle this via the Orphaned Worktree Recovery Protocol

## Execution Steps

### Step 1: Modify WorkPrepare.handleNonFoundResult() to return ERROR for foreign-session locks

In `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`, find the block at line 419
that returns `LOCKED` when `!locked.sessionId().equals(input.sessionId())`. Replace it with:

```java
Map<String, Object> errorResult = new LinkedHashMap<>();
errorResult.put("status", "ERROR");
errorResult.put("message", "Issue " + existingWorktree.issueId() +
  " has an existing worktree locked by another session");
errorResult.put("issue_id", existingWorktree.issueId());
errorResult.put("locked_by", locked.sessionId());
errorResult.put("lock_age_seconds", locked.ageSeconds());
errorResult.put("worktree_path", existingWorktree.worktreePath());
return mapper.writeValueAsString(errorResult);
```

This replaces the `LOCKED` response with an `ERROR` response that includes enough info for the skill to
show a meaningful dialog.

### Step 2: Add a unit test for the new ERROR response format

In `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`, add a test that:
- Creates an issue worktree directory at the expected worktree path
- Writes a lock file owned by a foreign session ID (not the test session ID)
- Calls `WorkPrepare.run()` with the test session ID
- Asserts the returned JSON has `status: ERROR`, `locked_by` matching the foreign session, and
  `lock_age_seconds >= 0`
- Asserts the returned JSON does NOT have `status: LOCKED`

Run all tests via `mvn -f client/pom.xml verify` and confirm they pass.

### Step 3: Update work-prepare-agent skill to handle foreign-session lock dialog

In `plugin/skills/work-prepare-agent/first-use.md`, update the `existing_worktree` handling section
(lines 319-324). Change:

```
  - **Lock file present:** Treat as locked by another session. Return LOCKED status. Do NOT investigate,
    inspect, or remove the worktree. Pick a different issue.
```

To:

```
  - **Lock file present:** Check whether the `ERROR` response includes a `locked_by` field:
    - **`locked_by` present (foreign-session lock):** Present a confirmation dialog to the user:

      ```
      Issue {issue_id} has an existing worktree locked by a previous session.
      Lock holder: {locked_by}  (age: {lock_age_seconds}s)
      Worktree: {worktree_path}
      ```

      Options:
      1. **Resume** — force-release the stale lock and re-acquire for the current session, then
         return READY with the existing worktree path
      2. **Skip** — pick a different issue; do NOT remove the worktree or release the lock
      3. **Abort** — stop without working on any issue

      **If user selects Resume:**
      ```bash
      "${CLAUDE_PLUGIN_DATA}/client/bin/issue-lock" force-release "${ISSUE_ID}"
      "${CLAUDE_PLUGIN_DATA}/client/bin/issue-lock" acquire "${ISSUE_ID}" "${CLAUDE_SESSION_ID}"
      ```
      Then return READY using the existing worktree path (same as Orphaned Worktree Recovery step 4,
      with `has_existing_work: true`).

    - **No `locked_by` field (orphaned worktree — no lock):** Apply the Orphaned Worktree Recovery
      Protocol below.
```

### Step 4: Update index.json and commit

Update `client/src/main/java/.../WorkPrepare.java` and commit with type `bugfix:`.
Update `plugin/skills/work-prepare-agent/first-use.md` in the same commit (mixed plugin + client = plugin
commit type per CLAUDE.md, but these are separate issues; use `bugfix:` for the Java change and a separate
`bugfix:` for the plugin change, or combine if they are the same logical fix — use judgment).

Actually per CLAUDE.md: "Mixed commits: if a commit touches plugin files, the type follows the plugin work."
So one combined `bugfix:` commit touching both Java and plugin is correct.
