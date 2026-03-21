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
- Move `writeLockFile(JvmScope, String, String)` and `createWorktreeDir(JvmScope, String)` into `TestUtils` as
  public static methods; remove the duplicate copies from all 5 test classes that currently define them
  (`EnforceWorktreePathIsolationTest`, `WarnBaseBranchEditTest`, `PreReadHookTest`, `BlockMainRebaseTest`,
  `BlockWorktreeIsolationViolationTest`) and update each caller to use `TestUtils.writeLockFile(...)` and
  `TestUtils.createWorktreeDir(...)`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnBaseBranchEditTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/PreReadHookTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockMainRebaseTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockWorktreeIsolationViolationTest.java`
- Replace `StringBuilder output` with `StringJoiner output = new StringJoiner("\n")` in
  `TestUtils.runGitCommandWithOutput`, replacing `output.append(line)` with `output.add(line)` and removing the
  conditional `if (output.length() > 0) output.append('\n')` delimiter guard
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java`
- Add assertion to `catSubdirectoryShowsCorrectedWorktreePath` (and any other write-blocked test) verifying that
  the blocked reason contains the full isolation explanation text: "The worktree exists to isolate changes from the
  main workspace until merge" — this closes post-condition #2 which requires the isolation rationale, not just the
  corrected path
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`
- Add `assertThat(reason).contains("Use the corrected worktree path")` to `readBlockedMessageShowsCorrectedPath()`
  (lines 564–588) to match the assertion already present in the write-blocked counterpart test, ensuring consistent
  validation of the redirect hint across both read and write block scenarios
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`
- Add a test `gitOutputPreservesLineBreaks` (or extend an existing git-output test) that invokes
  `TestUtils.runGitCommandWithOutput` on a multi-line git command (e.g., `git log --oneline -2`) and asserts that
  the returned string contains a newline between lines — confirming that `StringJoiner("\n")` produces correct
  line-separated output and that git command parsing is unaffected by the StringBuilder-to-StringJoiner refactor
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java` (or a dedicated test class)

## Post-conditions
- [ ] The error message for a blocked `.cat/` subdirectory write includes the corrected worktree-equivalent
  path (e.g., `{worktree}/.cat/retrospectives/index.json`)
- [ ] The error message explains the isolation rationale ("The worktree exists to isolate changes from the
  main workspace until merge")
- [ ] New test `catSubdirectoryShowsCorrectedWorktreePath` passes and verifies the exact message content
- [ ] All existing tests continue to pass (no regressions)
- [ ] E2E: invoke `/cat:learn` mid-worktree; the hook block message includes the redirected `.cat/`
  subdirectory path inside the worktree
