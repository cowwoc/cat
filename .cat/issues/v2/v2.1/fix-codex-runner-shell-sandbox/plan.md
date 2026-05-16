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
- [ ] Java conventions document the preferred `Objects.equals(value, literal)` operand order for null-safe equality checks.
- [ ] `mvn -f client/pom.xml verify -e` passes.

## Jobs

### Job 1: Add nested Codex sandbox regression coverage and fix command construction

- Inspect the Codex runner command construction in `client/cli/src/main/java/io/github/cowwoc/cat/codex/hook/skills/CodexRunner.java` and the existing coverage in `client/cli/src/test/java/io/github/cowwoc/cat/client/test/codex/CodexRunnerTest.java`.
- RED: update or add a `CodexRunnerTest` assertion that fails until `buildCommand()` explicitly configures nested Codex shell execution to avoid the current `bwrap` user-namespace sandbox startup failure.
- Run the targeted test and confirm the new/updated assertion fails for the expected command-construction reason.
- GREEN: update `CodexRunner.buildCommand()` so nested `codex exec` uses the Codex CLI sandbox option/configuration needed for shell commands to run in this externally sandboxed runtime.
- Ensure the command still includes the existing JSONL, last-message output, working-directory, model, effort, and stdin prompt behavior.
- Update the Java conventions to prefer `Objects.equals(value, literal)` over flipped string-literal equality checks, and apply that style in the Codex runner change.
- Run the targeted `CodexRunnerTest` test class and fix any regressions.
- Run the original-use-case check that the generated command shape is suitable for nested Codex executions from `codex-runner` and `empirical-test-runner --runtime codex`; if a live nested Codex run is too expensive or unavailable, document the concrete limitation in the implementation result.
- Update this issue's `index.json` to `closed` in the same commit as the implementation after tests pass.
- Run `mvn -f client/pom.xml verify -e` before reporting completion.
