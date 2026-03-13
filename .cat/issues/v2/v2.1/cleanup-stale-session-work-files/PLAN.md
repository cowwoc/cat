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
