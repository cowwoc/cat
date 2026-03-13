# Plan: Move CAT Session Files to CAT Work Path

## Goal

Several CAT-specific session files are currently stored under Claude's config directory
(`~/.claude/projects/{encoded}/{sessionId}/`) instead of under the project-local CAT work path
(`{projectRoot}/.cat/work/sessions/{sessionId}/`). Move them to consolidate CAT state in one location.

Files to move:

1. **Skill loaded markers** (`loaded/` directory) — created by `GetSkill` and `GetFile`, tracked in
   `ClearAgentMarkers`. Currently at `{sessionBasePath}/{sessionId}/loaded/` and
   `{sessionBasePath}/{sessionId}/subagents/{agentId}/loaded/`
2. **Pending agent result flag** — created by `SetPendingAgentResult`. Currently at
   `{sessionBasePath}/{sessionId}/pending-agent-result`
3. **Failure tracking files** — created by `DetectRepeatedFailures`. Currently at
   `{sessionBasePath}/{sessionId}/cat-failure-tracking-*.count`

All should move to subdirectories of `scope.getCatWorkPath()` (returning `{projectRoot}/.cat/work/`),
specifically under `{projectRoot}/.cat/work/sessions/{sessionId}/`.

## Parent Requirements

None

## Approaches

### A: Move All Three File Groups to getCatSessionPath()

- **Risk:** LOW
- **Scope:** ~8 files
- **Description:** Update each of the three Java classes (`GetSkill`/`GetFile`/`ClearAgentMarkers`,
  `SetPendingAgentResult`, `DetectRepeatedFailures`) and their callers
  (`PostToolUseHook`, `PostToolUseFailureHook`) to use `scope.getCatSessionPath()` instead of
  `scope.getClaudeSessionsPath().resolve(sessionId)`. Also update `ResetFailureCounter` and test files.

> **Selected: Approach A** — straightforward relocation; all three file groups serve the same purpose
> (per-session CAT state) and belong together under the project-local `.cat/work/sessions/` hierarchy.

## Research Findings

### Current Path Construction

| File/Class | Current Path | New Path |
|---|---|---|
| `GetFile.java` (skill loaded markers) | `{sessionBasePath}/{catAgentId}/loaded/` | `{catSessionPath}/loaded/` or `{catWorkPath}/sessions/{catAgentId}/loaded/` |
| `ClearAgentMarkers.java` | `{sessionBasePath}/{sessionId}/loaded/`, `{sessionBasePath}/{sessionId}/subagents/{agentId}/loaded/` | `{catSessionPath}/loaded/`, `{catSessionPath}/subagents/{agentId}/loaded/` |
| `SetPendingAgentResult.java` | `{sessionBasePath}/{sessionId}/pending-agent-result` | `{catSessionPath}/pending-agent-result` |
| `DetectRepeatedFailures.java` | caller-injected `sessionDirectory` = `{sessionBasePath}/{sessionId}` | caller passes `{catSessionPath}` |

**Note on agent ID vs session ID:** `GetFile.java` uses `catAgentId` (a composite ID like
`{sessionId}/subagents/{agentId}`) as the directory under `sessionBasePath`. After the move, the
`loaded/` directory for the main agent would be at `{catWorkPath}/sessions/{sessionId}/loaded/` and
for subagents at `{catWorkPath}/sessions/{sessionId}/subagents/{agentId}/loaded/`. This matches what
`ClearAgentMarkers` already expects (it constructs these paths explicitly).

### Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetFile.java` — update `baseDir` to use
  `scope.getCatWorkPath()` + `sessions` instead of `scope.getClaudeSessionsPath()`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/ClearAgentMarkers.java` — update `baseDir`
  to use `scope.getCatWorkPath()` + `sessions` instead of `scope.getClaudeSessionsPath()`
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java` — change
  `flagPath` construction from `scope.getClaudeSessionsPath().resolve(sessionId).resolve(...)` to
  `scope.getCatSessionPath().resolve(...)`
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java` — change `sessionDirectory`
  construction to use `scope.getCatSessionPath()` for `ResetFailureCounter`
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseFailureHook.java` — change
  `sessionDirectory` construction to use `scope.getCatSessionPath()` for `DetectRepeatedFailures`
- `client/src/main/java/io/github/cowwoc/cat/hooks/failure/DetectRepeatedFailures.java` — update
  Javadoc to reference new path
- `client/src/main/java/io/github/cowwoc/cat/hooks/failure/ResetFailureCounter.java` — update Javadoc
  to reference new path
- Test files for each of the above classes — update path constructions to match new locations

### Dependency Note

This issue depends on `refactor-jvmscope-path-api`, which renames `getProjectCatDir()` →
`getCatWorkPath()` and `getSessionCatDir()` → `getCatSessionPath()`. All code in this issue uses the
post-refactor names.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None significant. The old path location (`~/.claude/projects/...`) is Claude-owned space
  and these files are transient session state. Moving them does not require a migration because they are
  recreated each session.
- **Mitigation:** Tests verify the new paths. No migration is needed since the files are transient.

## Pre-conditions

- [ ] `refactor-jvmscope-path-api` is closed (provides `getCatWorkPath()` and `getCatSessionPath()`)

## Sub-Agent Waves

### Wave 1

- Write failing tests for the three file groups (TDD):
  - `GetFileTest` — verify `loaded/` marker is written under `{catWorkPath}/sessions/{catAgentId}/loaded/`
  - `ClearAgentMarkersTest` — verify markers are cleared from `{catSessionPath}/loaded/` and
    `{catSessionPath}/subagents/{agentId}/loaded/`
  - `SetPendingAgentResultTest` — verify flag is written to `{catSessionPath}/pending-agent-result`
  - `DetectRepeatedFailuresTest` — verify tracking file uses caller-injected path (no change needed if
    already injectable; update caller path in `PostToolUseFailureHookTest`)
  - Files: relevant test files under `client/src/test/`
- Run `mvn -f client/pom.xml test` — tests should fail (TDD red phase)

### Wave 2

- Update production path construction in:
  - `GetFile.java`: change `scope.getClaudeSessionsPath()` → `scope.getCatWorkPath().resolve("sessions")`
  - `ClearAgentMarkers.java`: same change
  - `SetPendingAgentResult.java`: change to `scope.getCatSessionPath().resolve("pending-agent-result")`
  - `PostToolUseHook.java`: change `sessionDirectory` to `scope.getCatSessionPath()`
  - `PostToolUseFailureHook.java`: change `sessionDirectory` to `scope.getCatSessionPath()`
  - Files: all production files listed above
- Update Javadoc in `DetectRepeatedFailures.java` and `ResetFailureCounter.java` to reference new path
- Run `mvn -f client/pom.xml test` — all tests should pass

## Post-conditions

- [ ] `GetFile.java` writes `loaded/` markers under `{catWorkPath}/sessions/{catAgentId}/loaded/`,
  NOT under `{sessionBasePath}/{catAgentId}/loaded/`
- [ ] `ClearAgentMarkers.java` clears markers from `{catSessionPath}/loaded/` and
  `{catSessionPath}/subagents/{agentId}/loaded/`
- [ ] `SetPendingAgentResult.java` writes the flag to `{catSessionPath}/pending-agent-result`,
  NOT under `{sessionBasePath}/{sessionId}/`
- [ ] `PostToolUseFailureHook.java` passes `scope.getCatSessionPath()` to `DetectRepeatedFailures`
- [ ] `PostToolUseHook.java` passes `scope.getCatSessionPath()` to `ResetFailureCounter`
- [ ] No production code constructs paths under `scope.getClaudeSessionsPath()` for these three file
  groups
- [ ] `mvn -f client/pom.xml test` exits with code 0
