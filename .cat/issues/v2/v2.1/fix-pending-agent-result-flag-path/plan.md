# Plan: fix-pending-agent-result-flag-path

## Problem

`EnforceStatusOutput.check()` looks for the `pending-agent-result` flag at:

```
~/.claude/projects/{encodedProject}/{sessionId}/pending-agent-result
```

(via `sessionBasePath.resolve(sessionId).resolve("pending-agent-result")`)

But `SetPendingAgentResult` writes the flag at and `EnforceCollectAfterAgent` reads it at:

```
{projectPath}/.cat/work/sessions/{sessionId}/pending-agent-result
```

(via `scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result")`)

These are completely different directory trees. The stop-hook enforcement of `pending-agent-result`
in `EnforceStatusOutput` has never worked — it always returns false regardless of whether the flag
is set.

## Root Cause

`check()` receives `sessionBasePath` (from `scope.getClaudeSessionsPath()`) and uses it for both:
1. Transcript JSONL lookup — correct, this IS in `~/.claude/projects/...`
2. `pending-agent-result` flag lookup — wrong, the flag is in `.cat/work/sessions/...`

The parameter should not have been used for the flag lookup.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Fixing this activates enforcement that was silently disabled; sessions with
  an unconsumed agent result will now be blocked at stop-hook time
- **Mitigation:** Existing tests for `SetPendingAgentResult` and `EnforceCollectAfterAgent` verify
  the flag path; add a test to `EnforceStatusOutputTest` for the flag-triggered block

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java` — change flag lookup
  from `sessionBasePath.resolve(sessionId).resolve(...)` to
  `scope.getCatSessionPath(sessionId).resolve("pending-agent-result")`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java` — add test
  that the stop hook blocks when `pending-agent-result` flag exists

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- In `EnforceStatusOutput.check()`, replace:
  ```java
  Path flagPath = sessionBasePath.resolve(sessionId).resolve("pending-agent-result");
  ```
  with:
  ```java
  Path flagPath = scope.getCatSessionPath(sessionId).resolve("pending-agent-result");
  ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- Remove `sessionBasePath` parameter from `check()` entirely if it is no longer needed after this
  and the transcript path fix (verify usages)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- Add test: place `pending-agent-result` flag at correct path, call `check()`, verify it returns
  a block decision
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java`
- Run all tests: `python3 /workspace/run_tests.py`

## Post-conditions

- [ ] `EnforceStatusOutput` reads `pending-agent-result` from the same path that `SetPendingAgentResult` writes it
- [ ] Stop hook blocks correctly when a `pending-agent-result` flag is present
- [ ] All existing tests pass
- [ ] New test covers the flag-triggered block path
