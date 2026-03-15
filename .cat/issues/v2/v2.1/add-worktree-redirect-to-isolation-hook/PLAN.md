# Plan: add-worktree-redirect-to-isolation-hook

## Goal

Enhance the `EnforceWorktreePathIsolation` hook error message to provide actionable guidance when blocking
an edit outside the active worktree: include the redirected path within the worktree where the agent should
make the edit instead, and explain why the restriction exists.

## Parent Requirements

None

## Approaches

### A: Verify and test existing corrected-path message (chosen)
- **Risk:** LOW
- **Scope:** 2 files (targeted)
- **Description:** Inspect the existing error message to confirm it already includes the corrected worktree
  path and the isolation explanation. Add a missing test for `.cat/` subdirectory redirect scenarios to
  prevent regression.

### B: Rewrite error message to add redirect guidance
- **Risk:** LOW
- **Scope:** 2 files (targeted)
- **Description:** Update `checkAgainstContext` to include the corrected worktree path and an explicit
  explanation in the block message. Add unit tests to verify message content.

**Chosen approach: A.** Inspection showed the corrected path is already included in the error message via
`context.correctedPath()`. The existing message structure covers both the redirect path and the isolation
rationale. The gap is that there is no test explicitly verifying the `.cat/` subdirectory redirect scenario
(the specific case reported in the issue description). Approach A adds this test to prevent regression.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** The `.cat/` redirect path is technically valid (points to a writable location inside the
  worktree), but writing shared-state files (like `retrospectives/index.json`) inside the worktree instead
  of the main workspace has different semantics. This issue only addresses the error message content;
  whether those writes are semantically correct is a separate concern.
- **Mitigation:** Add tests that verify the error message content for `.cat/` paths.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java` — verify or
  update `checkAgainstContext` so the block message contains: (1) the corrected worktree path, (2) the
  isolation explanation
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java` — add test
  `catSubdirectoryShowsCorrectedWorktreePath` verifying that `.cat/` subdirectory paths are redirected
  correctly in the error message

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Inspect current `checkAgainstContext` error message in `EnforceWorktreePathIsolation.java`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- If corrected path and explanation are absent, update the block message so it contains:
  1. The corrected worktree path computed via `context.correctedPath(absoluteFilePath)`
  2. An explanation: "The worktree exists to isolate changes from the main workspace until merge."
  3. A "do not bypass via Bash" warning
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- Add test `catSubdirectoryShowsCorrectedWorktreePath` to `EnforceWorktreePathIsolationTest`:
  - Setup: lock file + worktree for `ISSUE_ID`, target path = `projectDir.resolve(".cat/retrospectives/index.json")`
  - Assert: result is blocked, reason contains "Use the corrected worktree path", reason contains the
    absolute normalized path of `worktreeDir.resolve(".cat/retrospectives/index.json")`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`
- Run `mvn -f client/pom.xml test` and verify exit code 0 (all tests pass)
- Update STATE.md to Status: closed, Progress: 100%
  - Files: `.cat/issues/2.1/add-worktree-redirect-to-isolation-hook/STATE.md`

## Post-conditions
- [ ] The error message for a blocked `.cat/` subdirectory write includes the corrected worktree-equivalent
  path (e.g., `{worktree}/.cat/retrospectives/index.json`)
- [ ] The error message explains the isolation rationale ("The worktree exists to isolate changes from the
  main workspace until merge")
- [ ] New test `catSubdirectoryShowsCorrectedWorktreePath` passes and verifies the exact message content
- [ ] All existing tests continue to pass (no regressions)
- [ ] E2E: invoke `/cat:learn` mid-worktree; the hook block message includes the redirected `.cat/`
  subdirectory path inside the worktree
