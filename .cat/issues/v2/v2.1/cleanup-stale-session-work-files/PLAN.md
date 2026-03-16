# Plan: Cleanup Stale Session Work Files

## Goal

Implement automatic cleanup of session-specific marker files stored under `.cat/work/sessions/{sessionId}/`
when the corresponding Claude session is deleted from its own project directory
(`~/.config/claude/projects/{encodedProjectDir}/{sessionId}/`). This prevents stale marker files from
accumulating indefinitely.

## Parent Requirements

None

## Approaches

### A: Register SessionEnd Hook to Clean Up Stale Sessions

- **Risk:** LOW
- **Scope:** 2 files (`SessionEndHandler.java`, `SessionEndHandlerTest.java`)
- **Description:** Register a handler for the `SessionEnd` hook (already defined in `plugin/hooks/hooks.json`).
  The handler:
  1. Scans `~/.cat/work/sessions/` for all session directories.
  2. For each session directory, checks whether the corresponding Claude session still exists in
     `~/.config/claude/projects/{encodedProjectDir}/{sessionId}/`.
  3. Deletes `.cat/work/sessions/{staleSessionId}/` if the Claude session directory no longer exists.
  The handler runs at session end, so it catches sessions that were just deleted by Claude before this
  session exits.

### B: Periodic Cleanup on SessionStart

- **Risk:** MEDIUM
- **Scope:** 3+ files (involves modifying `SessionStartHandler` or creating a new periodic task)
- **Description:** On each `SessionStart`, scan `.cat/work/sessions/` and delete any stale directories.
  This runs on every session, which could be inefficient for projects with many old sessions.

> **Selected: Approach A** — hooks into Claude's own session lifecycle (`SessionEnd`) and runs once per
> session cleanup event, eliminating stale files before the session exits. No performance impact on
> normal session starts.

## Research Findings

### Session Directory Locations

| Directory | Purpose | Notes |
|-----------|---------|-------|
| `~/.claude/` | Claude config directory; default `CLAUDE_CONFIG_DIR` | Env var, defaults to `~/.claude` |
| `~/.claude/projects/{encodedProjectDir}/{sessionId}/` | Claude session directory | Created by Claude, exists for the session's lifetime |
| `~/.cat/work/sessions/{sessionId}/` | CAT work files per session | Stores markers like `session.cwd`, lock status, etc. |

### Encoded Project Directory

Claude encodes project paths by replacing `/` and `.` with `-`:
- `/workspace` → `-workspace`
- `/home/user/proj.cat` → `-home-user-proj-cat`
- Method: `JvmScope.getEncodedProjectDir()` (already implemented)

### Existing SessionEnd Hook

The `SessionEnd` hook is registered in `plugin/hooks/hooks.json`:
```json
"SessionEnd": [
  {
    "hooks": [
      {
        "type": "command",
        "command": "${CLAUDE_PLUGIN_ROOT}/client/bin/session-end"
      }
    ]
  }
]
```

This calls the jlink-bundled `session-end` binary, which invokes the Java handler `SessionEndHandler`.

### Handler Input/Output Contract

Like all hook handlers, `SessionEndHandler` must:
- Extend `BashHandler`
- Implement `BashHandler.Result handle(BashHandler.Input input)`
- Return a result containing:
  - `output` (string; null if silent)
  - `continueProcessing` (boolean; false to stop, true to continue)
  - `exitCode` (integer; 0 for success, non-zero for error)

### Session Directory Lifecycle

- Claude **creates** `~/.claude/projects/{encodedProjectDir}/{sessionId}/` when a session starts
- Claude **deletes** `~/.claude/projects/{encodedProjectDir}/{sessionId}/` when the session ends
- CAT creates `~/.cat/work/sessions/{sessionId}/` when needed (e.g., first `/cat:work` invocation)
- CAT **does NOT clean up** `~/.cat/work/sessions/{sessionId}/` — this is the bug being fixed

### Multi-Session Safety

Multiple Claude instances may run concurrently, each with its own session ID. The cleanup logic must:
- Only delete directories for **non-existent Claude sessions** (the directory in
  `~/.claude/projects/` is gone)
- Never delete the **current session's** marker directory (even though the current session has not yet
  been deleted by Claude)
- Handle race conditions: if two concurrent sessions scan at the same time, both may try to delete
  the same stale directory

**Solution:** Use a simple file existence check: if
`~/.claude/projects/{encodedProjectDir}/{sessionId}/` does not exist, the session is stale.

### Existing Utilities

- `JvmScope.getClaudeConfigDir()` → `~/.claude/` (default or env var)
- `JvmScope.getClaudeProjectDir()` → project root (e.g., `/workspace`)
- `JvmScope.getEncodedProjectDir()` → encoded path name
- `JvmScope.getSessionDirectory()` → `~/.claude/projects/{encodedProjectDir}/{sessionId}/`

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Race conditions if two sessions delete the same stale directory simultaneously
- **Mitigation:** Use `Files.notExists()` to check before deletion, and wrap deletion in a try-catch
  to ignore failures due to concurrent deletion. Logging will record success and non-blocking failures.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`
  - Implement cleanup logic: scan `~/.cat/work/sessions/`, find stale directories, delete them
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
  - Add tests: verify stale sessions are cleaned up; verify current session is NOT deleted

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Write failing tests for `SessionEndHandler` in `SessionEndHandlerTest.java` (TDD):
  1. `sessionEndDeletesStaleSessionWorkFiles` — create a stale session directory, call handler, verify
     it was deleted
  2. `sessionEndSkipsCurrentSessionWorkFiles` — verify the current session's work directory is NOT
     deleted
  3. `sessionEndHandlesNonExistentWorkDirectory` — verify no error when `.cat/work/sessions/` doesn't
     exist yet
  4. `sessionEndContinuesOnConcurrentDeletion` — verify handler completes even if another session
     deletes the same directory during cleanup
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Implement `SessionEndHandler.java`:
  1. Read `sessionId` and other config from `input`
  2. Construct path to `.cat/work/sessions/` via `scope.getProjectCatDir().resolve("sessions")`
  3. If the directory doesn't exist, return silent success (no cleanup needed)
  4. List all subdirectories (session IDs)
  5. For each session ID found:
     a. Skip the current session (don't delete our own work files)
     b. Check if `~/.claude/projects/{encodedProjectDir}/{sessionId}/` exists
     c. If NOT → delete `.cat/work/sessions/{sessionId}/` (and log the action)
     d. On deletion error → log warning (non-blocking)
  6. Return success (continue to next hook)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`
- Run `mvn -f client/pom.xml test` to verify all tests pass
  - Files: (build only)

### Wave 2 (Fix: Test Coverage and Design Gaps)

- Add missing test `sessionEndContinuesOnConcurrentDeletion` to `SessionEndHandlerTest.java`:
  - Simulate a directory that is deleted by another thread/process between the listing step and the
    `deleteSessionWorkDirectory()` call
  - Verify the handler returns success (continues) rather than throwing or returning an error result
  - This exercises the IOException catch block in the concurrent-deletion path
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Add test for partial-deletion failure path in `SessionEndHandlerTest.java`:
  - Create two stale session directories; make one undeletable (e.g., set permissions so deletion fails)
  - Verify the handler logs a warning for the failed deletion and still processes the other directory
  - Verify `deletionFullySucceeded` is `false` when any delete fails, and success is NOT logged
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Add nested directory structure test in `SessionEndHandlerTest.java`:
  - Create a stale session directory with at least 3 levels of nested subdirectories and files
  - Verify `Files.walk().sorted(Comparator.reverseOrder())` deletes all contents before removing
    the root directory (no `DirectoryNotEmptyException`)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Fix path traversal risk in `SessionEndHandler.java`:
  - After reading subdirectory names from `.cat/work/sessions/` (line ~77), validate each name
    against `SESSION_ID_PATTERN` (UUID regex) before using it in `Path.resolve()`
  - Reject (log and skip) any directory name that does not match the expected session ID format
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`
- Fix `isLockOwnedBySession()` to use line-exact match instead of substring match:
  - Replace `content.contains(sessionId)` with a check that matches the sessionId as a complete
    line (e.g., split on newlines and compare trimmed tokens, or use a Pattern with `^sessionId$`)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`
- Fix `TestUtils.MAPPER` Jackson convention violation in `TestUtils.java`:
  - Remove the static `JsonMapper.builder().build()` field
  - Change `bashInput()` (and any other method using the static field) to accept a `JvmScope`
    parameter and call `scope.getJsonMapper()` instead
  - `dummyInput()` already follows this pattern; align `bashInput()` to match
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java`
- Run `mvn -f client/pom.xml test` to verify all tests pass after Wave 2 fixes
  - Files: (build only)

### Wave 3 (Fix: Missing Test Coverage for Post-conditions)

- Add test `sessionEndContinuesOnConcurrentDeletion` to `SessionEndHandlerTest.java`:
  - Create a stale session directory (no corresponding Claude session directory)
  - Use a separate thread that deletes the stale directory between when the handler lists it and when
    `deleteSessionWorkDirectory()` is called (simulate concurrent deletion by a second session)
  - Invoke the handler and verify it returns a success result (no exception thrown, `continueProcessing` is true)
  - This directly exercises the `IOException` catch block that silently ignores concurrent-deletion failures
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Add integration test `sessionEndCleansMultipleStaleSessionsLeavingOnlyActive` to `SessionEndHandlerTest.java`:
  - Create 5 fake session directories under `.cat/work/sessions/` (using temp directories)
  - Create Claude session directories for only 2 of the 5 sessions (simulating 2 active, 3 stale sessions)
  - Invoke the handler with the current session set to one of the 2 active sessions
  - Verify: the 3 stale session directories are deleted; the 2 active session directories remain; handler
    returns success
  - This is the E2E verification that `.cat/work/sessions/` retains only active session directories after cleanup
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Run `mvn -f client/pom.xml test` to verify all tests pass after Wave 3 additions
  - Files: (build only)

### Wave 4 (Fix: Stakeholder Review Concerns)

- Fix `clean()` to use `scope.getClaudeSessionId()` as the canonical source for the current session ID instead of
  reading it from `input.getSessionId()`:
  - In `SessionEndHandler.java`, locate the `clean()` method (~line 79) and replace `input.getSessionId()` with
    `scope.getClaudeSessionId()` to align with the pattern used by all other session-scoped operations in the class
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`
- Add test `sessionEndRejectsNonUuidSessionIds()` to `SessionEndHandlerTest.java`:
  - Create a directory under `.cat/work/sessions/` whose name is NOT a valid UUID (e.g., `../../../etc/passwd`,
    `not-a-uuid`, or `.`)
  - Invoke the handler and verify that the non-UUID directory is skipped (not deleted, no exception thrown)
  - This explicitly validates the security boundary enforced by `SESSION_ID_PATTERN`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Add test `sessionEndHandlesPermissionErrors()` to `SessionEndHandlerTest.java`:
  - Create a stale session directory (no corresponding Claude session directory)
  - Make the directory read-only so that `Files.delete()` will fail with a permission error
  - Invoke the handler and verify it completes gracefully (returns success, logs a warning, no exception thrown)
  - This exercises the `IOException` catch block for the non-concurrent-deletion failure path
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHandlerTest.java`
- Run `mvn -f client/pom.xml test` to verify all tests pass after Wave 4 fixes
  - Files: (build only)

## Post-conditions

- [ ] `SessionEndHandlerTest.sessionEndDeletesStaleSessionWorkFiles` passes: when a session directory
  exists in `.cat/work/sessions/` but the corresponding Claude session directory no longer exists,
  `SessionEndHandler.handle()` returns success and the stale directory is deleted
- [ ] `SessionEndHandlerTest.sessionEndSkipsCurrentSessionWorkFiles` passes: the current session's
  `.cat/work/sessions/{currentSessionId}/` is NOT deleted
- [ ] `SessionEndHandlerTest.sessionEndContinuesOnConcurrentDeletion` passes: if two sessions
  concurrently try to delete the same stale directory, both succeed gracefully
- [ ] All existing `SessionEndHandlerTest` tests pass
- [ ] `mvn -f client/pom.xml test` exits with code 0
- [ ] E2E: After many sessions run and end, `.cat/work/sessions/` contains only directories for
  currently open Claude sessions (verified manually or via integration test)
