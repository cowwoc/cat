# Plan

## Goal

Update the Codex empirical-test runner path so nested Codex executions avoid the shell/sandbox startup failure observed during `$cat:help` empirical tests. The runner should preserve or inject the execution settings needed for nested shell commands to run successfully, without requiring ad-hoc wrapper scripts.

## Pre-conditions

(none)

## Post-conditions

- [ ] Nested Codex runs launched by `codex-runner`/`empirical-test-runner --runtime codex` no longer fail shell tool execution with the `bwrap` user-namespace sandbox error in the current runtime.
- [ ] The fix is implemented in the Codex runner path rather than in one-off empirical-test prompts or temporary wrapper scripts.
- [ ] Regression coverage verifies the Codex command construction includes the required sandbox/environment handling for nested executions.
- [ ] Existing Codex runner behavior remains compatible with normal model, effort, working-directory, JSONL, and last-message output options.
- [ ] `mvn -f client/pom.xml verify -e` passes.
