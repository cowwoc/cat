# Plan: fix-enforce-status-worktree-path

## Problem

When Claude Code runs in a git worktree, the `enforce-status` stop hook is permanently blocked with:

```
❌ Hook error: ~/.claude/projects/-workspace--claude-worktrees-NAME/{session_id}.jsonl
```

The file at that path does not exist.

## Root Cause

`EnforceStatusOutput.run()` reads `transcript_path` from the hook payload (line 105) and passes it to
`checkTranscriptForStatusSkill()`. Claude Code constructs `transcript_path` from the **current working
directory** — but in a worktree session, the cwd is the worktree path
(`/workspace/.claude/worktrees/NAME`), which encodes to `-workspace--claude-worktrees-NAME`.

The actual session JSONL is stored under **`CLAUDE_PROJECT_DIR`** (`/workspace` → `-workspace`).

The hook already has everything needed to derive the correct path:
- `sessionId = scope.getSessionId()` — correct session ID
- `sessionBasePath = scope.getClaudeSessionsPath()` — computed from `CLAUDE_PROJECT_DIR`, points to
  `~/.claude/projects/-workspace/` ← **correct**

But it ignores these and trusts `transcript_path` from the payload, which points to the wrong
(worktree-derived) directory.

## Parent Requirements

None

## Expected vs Actual

- **Expected:** `checkTranscriptForStatusSkill` reads the session JSONL from the `CLAUDE_PROJECT_DIR`-based
  path (`~/.claude/projects/-workspace/{session_id}.jsonl`)
- **Actual:** It attempts to read from the cwd-based path
  (`~/.claude/projects/-workspace--claude-worktrees-NAME/{session_id}.jsonl`), which does not exist,
  throwing `NoSuchFileException` and permanently blocking the session

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Slight — replacing `transcript_path` with a constructed path means hooks no longer
  defer to Claude Code's provided path. If Claude Code ever legitimately stores session files somewhere
  other than `sessionBasePath`, the hook would miss them.
- **Mitigation:** Keep `transcript_path` as a fallback: try `sessionBasePath + sessionId + ".jsonl"` first;
  if it doesn't exist, fall back to `transcript_path`; if that also doesn't exist, skip enforcement.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java` — change
  `checkTranscriptForStatusSkill` to accept `sessionBasePath` + `sessionId` and construct the path
  itself rather than using the `transcript_path` payload field directly
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java` — add test
  case for worktree scenario where `transcript_path` points to wrong directory

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- In `EnforceStatusOutput.run()`, construct the preferred transcript path as
  `sessionBasePath.resolve(sessionId + ".jsonl")` and pass it to `check()` instead of
  (or alongside) `transcriptPath`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- In `check()` / `checkTranscriptForStatusSkill()`, resolve the transcript path in priority order:
  1. `Paths.get(transcriptPath)` if non-blank and exists → use it (correct in normal sessions)
  2. `sessionBasePath.resolve(sessionId + ".jsonl")` if it exists → use it (workaround for worktrees)
  3. Neither found → throw `IOException` (fail fast — missing transcript is an error, not a skip)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- Add a test that simulates a worktree scenario: `transcript_path` points to a non-existent
  worktree-derived path, but the session file exists at the `sessionBasePath`-derived path → hook
  reads it correctly and applies normal enforcement
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java`
- Run all tests: `python3 /workspace/run_tests.py`

## Post-conditions

- [ ] `EnforceStatusOutput` reads the session JSONL from the `CLAUDE_PROJECT_DIR`-based path even when
  running in a git worktree
- [ ] Hook no longer throws when `transcript_path` points to the wrong (worktree-derived) directory
- [ ] All existing tests pass with no regressions
- [ ] New test case covers the worktree scenario
