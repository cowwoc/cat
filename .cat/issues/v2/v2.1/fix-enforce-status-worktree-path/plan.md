# Plan: fix-enforce-status-worktree-path

## Problem

When Claude Code runs in a git worktree (e.g., `/workspace/.claude/worktrees/NAME`), it computes
the project directory hash from the worktree path. The `enforce-status` stop hook receives a
`transcript_path` pointing to the worktree-specific project directory (e.g.,
`~/.claude/projects/-workspace--claude-worktrees-NAME/{session_id}.jsonl`), but the actual session
JSONL file lives in the main workspace's project directory
(`~/.claude/projects/-workspace/{session_id}.jsonl`). The hook throws `NoSuchFileException` and
permanently blocks the session with:
`❌ Hook error: /home/node/.config/claude/projects/-workspace--claude-worktrees-NAME/{session_id}.jsonl`

## Parent Requirements

None

## Reproduction Code

```
# Start Claude Code in a git worktree (e.g., /workspace/.claude/worktrees/my-branch)
# End any session turn → stop hook fires → error appears, blocking all output
```

## Expected vs Actual

- **Expected:** Hook finds the session JSONL file (or skips enforcement if unavailable) and allows the session to proceed
- **Actual:** Hook throws `NoSuchFileException` on the worktree-specific path and permanently blocks the session

## Root Cause

`EnforceStatusOutput.checkTranscriptForStatusSkill()` calls `Files.newBufferedReader(path)` directly
without checking if the file exists first. When Claude Code is invoked in a worktree, the
`transcript_path` it provides points to the wrong project directory. The fix has two layers:

1. **Worktree fallback:** If the transcript file doesn't exist at the given path AND the path contains
   `--claude-worktrees-`, strip the worktree suffix from the encoded project path and look for the
   session file in the parent workspace's project directory.
2. **Graceful skip:** If the file still can't be found, return `CheckResult(false, true)` to skip
   enforcement rather than throwing.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Enforcement could be silently skipped if transcript_path is wrong for unrelated reasons
- **Mitigation:** Worktree detection is specific (pattern `--claude-worktrees-`); fallback only activates for known worktree paths

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java` — add `resolveTranscriptPath()` helper and use it in `checkTranscriptForStatusSkill()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java` — add test cases for missing file and worktree fallback

## Test Cases

- [ ] Transcript path points to non-existent file → returns `CheckResult(false, true)` (no block)
- [ ] Transcript path contains `--claude-worktrees-NAME` but file exists in parent workspace path → resolves and reads correctly
- [ ] Transcript path file exists normally → existing behavior unchanged

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- Add `resolveTranscriptPath(String transcriptPath)` private static method to `EnforceStatusOutput`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
  - If `transcriptPath` is blank: return `null`
  - If file exists at given path: return `Paths.get(transcriptPath)`
  - If path contains `--claude-worktrees-`: extract the prefix before `--claude-worktrees-`, reconstruct the parent workspace path, return that path if it exists
  - Otherwise: return `null` (skip enforcement)
- Replace `Path path = Paths.get(transcriptPath)` in `checkTranscriptForStatusSkill()` with `Path path = resolveTranscriptPath(transcriptPath); if (path == null) return new CheckResult(false, true);`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- Add unit tests covering the three cases above
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java`
- Run all tests: `python3 /workspace/run_tests.py`

## Post-conditions

- [ ] `EnforceStatusOutput` no longer throws when `transcript_path` points to a non-existent worktree-specific path
- [ ] Worktree sessions resolve transcript from parent workspace project directory when available
- [ ] All existing tests pass with no regressions
- [ ] New test cases cover missing-file and worktree-fallback scenarios
