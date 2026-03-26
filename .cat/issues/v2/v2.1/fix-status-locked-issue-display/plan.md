# Plan

## Goal

Fix the status display to show open issues that are locked by another session as in-progress (🔄)
instead of open (🔳). Currently, when another Claude session holds a lock on an issue, the status
box still shows the issue as open, leading to incorrect suggestions that it is available to work on.

Scope: status box only. All locks (active or stale) are treated as in-progress. The 🔄 indicator
is used (same as current session's in-progress issues).

## Research Findings

**Root cause confirmed:** `GetStatusOutput.getIssueStatus()` at line 350 in
`client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
checks for lock files at:
```java
catDir.resolve("locks")   // .cat/locks/ — this directory does NOT exist
```

But `IssueLock.java` stores lock files at:
```java
scope.getCatWorkPath().resolve("locks")  // .cat/work/locks/ — actual location
```

Since the wrong directory is used, `Files.exists(lockFile)` always returns `false`, and issues locked
by another session are displayed as open (🔳) instead of in-progress (🔄).

**Secondary bug in `getActiveAgents`:** The method uses the same wrong path (`catDir.resolve("locks")`)
AND parses lock files as `key=value` format, but lock files are JSON
(`{"session_id":"uuid","created_at":epochSeconds,...}`). This means the Active Agents section also
never shows other sessions' agents.

## Pre-conditions

(none)

## Post-conditions

- [ ] Issues locked by another session are displayed as 🔄 (in-progress) in the status box instead of 🔳 (open)
- [ ] Regression test added verifying locked issues appear as in-progress in the status display
- [ ] No new issues introduced
- [ ] E2E verification: acquire a lock on an issue from one session context, run status from another session context, confirm the locked issue shows as 🔄

## Sub-Agent Waves

### Wave 1

- Fix `GetStatusOutput.getIssueStatus()` in
  `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`:
  - Line 350: Change `catDir.resolve("locks").resolve(lockFileName)` to
    `scope.getCatWorkPath().resolve("locks").resolve(lockFileName)`
  - The `scope` field (type `ClaudeTool`) is already available in the class at line 61;
    no parameter changes are needed
  - Remove `catDir` parameter from `getIssueStatus` if it is no longer needed after this fix;
    keep it if still used for other purposes (check line 356: `catDir.getParent().getParent()`)
    — `catDir` is still used at lines 356-358 for the PRO branch status check, so keep the param

- Fix `GetStatusOutput.getActiveAgents()` in the same file:
  - Line 1019: Change `catDir.resolve("locks")` to `scope.getCatWorkPath().resolve("locks")`
  - Fix lock file JSON parsing (currently parses as `key=value`, but files are JSON format):
    - Replace the line-split `key=value` loop (lines 1042-1058) with proper JSON parsing
      using `scope.getJsonMapper()`. Parse `"session_id"` as a String field and
      `"created_at"` as a long field from the JSON object.
    - For `worktree`: the JSON has a `"worktrees"` map `{"/path": "sessionId"}`;
      extract the first key from this map as the worktree path (or empty string if the map is empty,
      absent, or not an object node — use `""` as the fallback worktree in all error cases)
  - Update the method signature to remove the `catDir` parameter if it's no longer used;
    otherwise keep it. Check if `catDir` is still referenced inside the method after the fix.
    If `catDir` is no longer used after changing `catDir.resolve("locks")` to use
    `scope.getCatWorkPath()`, remove the parameter AND update the call site at line 578.

- Add a regression test in
  `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetStatusOutputTest.java`:
  - Test name: `lockedIssueShowsInProgressStatus`
  - Setup: create a temp dir with `.cat/issues/v2/v2.0/my-task/index.json` containing
    `{"status":"open"}` and a lock file at `.cat/work/locks/2.0-my-task.lock` containing the
    following exact JSON (matching the format written by `IssueLock.writeLockToTempFile`):
    `{"session_id":"other-session-id","worktrees":{"/some/worktree":"other-session-id"},"created_at":1700000000,"created_iso":"2023-11-14T22:13:20Z"}`
    The `session_id` value must differ from the test scope's session ID (use `"other-session-id"`).
    The TestClaudeTool scope session ID can be obtained via `scope.getSessionId()` but since
    `TestClaudeTool` uses a fixed constant `SESSION_ID`, any value that doesn't equal that constant works.
    All four fields (`session_id`, `worktrees`, `created_at`, `created_iso`) must be present so the
    new JSON parsing code finds the fields it needs without errors.
  - Expected: `GetStatusOutput.getOutput(new String[0])` returns a string containing `"🔄 my-task"`
  - Follow the test conventions (no class fields, TestClaudeTool, try-with-resources, etc.)

- Run `mvn -f client/pom.xml verify -e` to confirm all tests pass (including the new test).
  Fix any compilation or lint errors before returning.

- Update `index.json` in `.cat/issues/v2/v2.1/fix-status-locked-issue-display/` to set
  `status: "closed"` and `progress: 100` in the SAME commit as the implementation.

## Commit Type

`bugfix:`
