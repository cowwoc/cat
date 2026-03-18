# Plan: fix-cwd-file-storage-location

## Problem

`.cwd` files (working-directory snapshots written by `PreCompactHook` before context compaction) are
stored under `projectCatDir/sessions/{sessionId}.cwd`. Because these files are session-scoped — each
session tracks only its own working directory — they should live under `sessionCatDir/session.cwd`
instead.

## Satisfies

None

## Expected vs Actual

- **Expected:** After a PreCompact event, the `.cwd` file is written to
  `{claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/cat/session.cwd`.
- **Actual:** The file is written to
  `{claudeConfigDir}/projects/{encodedProjectDir}/cat/sessions/{sessionId}.cwd`.

## Root Cause

`PreCompactHook` calls `scope.getProjectCatDir().resolve("sessions")` instead of
`scope.getSessionCatDir()`. The same wrong path is read back in `RestoreCwdAfterCompaction` and
deleted in `SessionEndHook`.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Existing `.cwd` files at the old path will not be cleaned up by migration. They are
  stale session data and can be left for the next run of the `2.1.sh` migration (which already
  deletes `sessions/`).
- **Mitigation:** No migration needed; old files under `sessions/` are already removed by Phase 10 of
  the 2.1 migration.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/PreCompactHook.java` — write to
  `scope.getSessionCatDir().resolve("session.cwd")`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreCwdAfterCompaction.java` — read
  from `scope.getSessionCatDir().resolve("session.cwd")`
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java` — remove explicit `.cwd`
  deletion (file is under `sessionCatDir`, which is session-scoped and cleaned up with the session)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/PreCompactHookTest.java` — update path
  assertions
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreCwdAfterCompactionTest.java` — update
  path assertions
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHookTest.java` — update or remove
  `.cwd` deletion assertions

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **Update `PreCompactHook.java`:** Replace `scope.getProjectCatDir().resolve("sessions")` and the
  `{sessionId}.cwd` filename with `scope.getSessionCatDir().resolve("session.cwd")`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/PreCompactHook.java`

- **Update `RestoreCwdAfterCompaction.java`:** Replace
  `scope.getProjectCatDir().resolve("sessions/" + sessionId + ".cwd")` with
  `scope.getSessionCatDir().resolve("session.cwd")`
  - Files:
    `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreCwdAfterCompaction.java`

- **Update `SessionEndHook.java`:** Remove the block that deletes the `.cwd` file from
  `projectCatDir/sessions/`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java`

- **Update tests:** Adjust path assertions in all three test files to match the new
  `sessionCatDir/session.cwd` location
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/PreCompactHookTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreCwdAfterCompactionTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHookTest.java`

- **Run tests:** `mvn -f client/pom.xml test`

## Post-conditions

- [ ] `PreCompactHook` writes `.cwd` to `scope.getSessionCatDir().resolve("session.cwd")`
- [ ] `RestoreCwdAfterCompaction` reads `.cwd` from `scope.getSessionCatDir().resolve("session.cwd")`
- [ ] `SessionEndHook` no longer explicitly deletes a `.cwd` file
- [ ] All tests pass (`mvn -f client/pom.xml test`)
