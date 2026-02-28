# Plan: restore-cwd-after-compaction

## Goal

After context compaction, the agent should automatically resume in the same working directory it was in before
compaction, preventing the silent drift back to `/workspace` that causes accidental edits in the main worktree.

## Satisfies

None (infrastructure improvement)

## Research Findings

- `SessionStart` fires after compaction with `"source": "compact"` in the hook input JSON
- `PostToolUse:Bash` hook input contains `workingDirectory` (the shell CWD at the time of the tool call)
- `RestoreWorktreeOnResume` (closed) already handles `source: "resume"` via lock file lookup, but does not handle
  `source: "compact"`, and the lock file only stores the worktree root, not the exact CWD sub-path
- No `PreCompact` hook is registered — CWD must be tracked continuously via `PostToolUse:Bash`
- Session CWD file pattern: `.claude/cat/sessions/{session_id}.cwd` (written after each Bash tool call)
- `additionalContext` in `SessionStartHandler` result is injected as `<system-reminder>` into agent context

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Writing to a session file on every Bash call adds minor I/O; file must be cleaned up on session end
- **Mitigation:** Only write when `workingDirectory` is non-empty and differs from the project root; clean up in
  `SessionEnd` hook

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/TrackWorkingDirectory.java` (NEW) — PostToolUse Bash
  handler: writes `workingDirectory` from hook input to `.claude/cat/sessions/{session_id}.cwd` when it is a
  non-default (non-project-root) directory
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreCwdAfterCompaction.java` (NEW) — SessionStart
  handler: activates when `source` is `compact`; reads `.claude/cat/sessions/{session_id}.cwd`; if path exists,
  injects `additionalContext` with `cd <path>` instruction
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java` — register `TrackWorkingDirectory`
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java` — register `RestoreCwdAfterCompaction`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java` (or equivalent) — clean up
  `.claude/cat/sessions/{session_id}.cwd` on session end
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TrackWorkingDirectoryTest.java` (NEW)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreCwdAfterCompactionTest.java` (NEW)

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. Create `TrackWorkingDirectory` PostToolUse Bash handler that writes `workingDirectory` to
   `.claude/cat/sessions/{session_id}.cwd` when non-empty and not equal to the project root
2. Register `TrackWorkingDirectory` in `PostToolUseHook`
3. Create `RestoreCwdAfterCompaction` SessionStart handler that reads the `.cwd` file and injects
   `additionalContext` when `source` is `compact` and the saved path exists
4. Register `RestoreCwdAfterCompaction` in `SessionStartHook`
5. Add cleanup of `.cwd` file in the session-end flow
6. Write tests for `TrackWorkingDirectory`: write on non-default dir, skip on project root, skip on empty
7. Write tests for `RestoreCwdAfterCompaction`: compact + file exists → inject; compact + no file → no inject;
   startup → no inject; resume → no inject (handled separately)
8. Run `mvn -f client/pom.xml verify`

## Post-conditions

- [ ] After every Bash tool use with a non-project-root `workingDirectory`, `.claude/cat/sessions/{session_id}.cwd`
      is updated with the current path
- [ ] On `SessionStart` with `source: "compact"`, if `.cwd` file exists and the path is a live directory, the agent
      receives `additionalContext` instructing it to `cd` back into that path
- [ ] On fresh startup or resume, `RestoreCwdAfterCompaction` does NOT inject (those cases handled by their own
      handlers or left unhandled)
- [ ] `.cwd` session file is cleaned up on session end
- [ ] All existing tests pass (`mvn -f client/pom.xml verify` exit code 0)
- [ ] E2E: Manually trigger compaction (or simulate with `source: "compact"` hook input) and confirm the agent
      receives a `cd` instruction for the previously active directory
