# Plan: migrate-benchmark-runner-to-java

## Goal
Migrate plugin/skills/instruction-builder-agent/benchmark-runner.sh (~1300 lines of Bash business logic)
to a Java jlink tool, following project conventions that require Java for complex business logic.

## Parent Requirements
None

## Current State
benchmark-runner.sh contains a full SPRT state machine, numeric arithmetic via awk, multi-pass JSON
parsing, statistical boundary evaluation, and incremental change-detection algorithms — all in Bash.

## Target State
A Java class `BenchmarkRunner.java` dispatches the 10 subcommands (extract-units, detect-changes,
map-units, extract-model, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status,
merge-results) using Jackson JsonMapper for JSON and standard Java APIs for SHA-256 and file I/O.
The Bash script becomes a thin wrapper that delegates to the jlink binary.

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Internal tool interface; no user-visible changes
- **Mitigation:** TDD approach (write tests first), functional parity verification

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/BenchmarkRunner.java` (NEW)
- `plugin/skills/instruction-builder-agent/benchmark-runner.sh` (replace with thin wrapper)
- Build configuration to register new jlink entry point

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Create BenchmarkRunner.java with all 10 command handlers
- Replace benchmark-runner.sh with thin wrapper delegating to jlink binary
- Register in build configuration
- Run tests to verify functional parity

### Wave 2 (Round 3 stakeholder concern fixes)
- **[HIGH] Fix getGitOutput() correctness defect in BenchmarkRunnerTest.java:** Delete the private
  `getGitOutput()` method (lines ~952-974 of BenchmarkRunnerTest.java) which ignores the `process.waitFor()`
  return value. Replace all three call sites (`rev-parse HEAD` on lines ~601, ~654, ~717) with
  `TestUtils.runGitCommandWithOutput()`, which already checks the exit code and throws `IOException`
  on failure.
- **[MEDIUM] Fix persistArtifacts path construction in BenchmarkRunner.java:** Replace the string
  concatenation path construction at lines 511-518 (computing `relTestCasesPath` and `relBenchmarkJson`
  via `skillPathPrefix + "/benchmark/..."`) with proper `java.nio.file.Path` operations:
  use `Path.of(skillPathArg).getParent()` (defaulting to `Path.of(".")` when null) and
  `parentPath.resolve("benchmark").resolve("test-cases.json")`, then convert to string with
  forward-slash separators via `path.toString().replace('\\', '/')` for cross-platform correctness.
- **[MEDIUM] Add test initSprtUsePriorBoostWithAcceptPrior in BenchmarkRunnerTest.java:** Add a new
  test that sets up a prior benchmark JSON with a test case having `"decision": "ACCEPT"` and a
  non-zero `log_ratio`. Calls `initSprt` with `--prior-boost` flag. Verifies that the resulting
  SPRT state for the re-run test case has `log_ratio` equal to `PRIOR_BOOST` (1.112) rather than
  0.0, confirming that the prior boost logic fires when the prior decision is ACCEPT.
- **[MEDIUM] Add 3 missing error-path tests in BenchmarkRunnerTest.java (prioritized subset):**
  1. `persistArtifactsRejectsCorruptBenchmarkJson`: Arrange a skill directory with a
     corrupt/empty `artifacts/benchmark.json` (or missing required fields like `session_id`);
     call `persist-artifacts`; verify that an `IOException` or `IllegalArgumentException` is thrown
     with a message referencing the file or missing field.
  2. `initSprtWithEmptyPrior`: Call `initSprt` with `rerunIds` containing test case IDs but
     `priorPath` set to `"none"` (no prior data). Verify that all listed test case IDs appear
     in the output `sprt_state` with fresh SPRT values (`log_ratio=0.0`, `decision=INCONCLUSIVE`,
     `carried_forward=false`).
  3. `checkBoundaryWithZeroTestCases`: Call `check-boundary` with a SPRT state JSON containing
     zero test case entries. Verify the output JSON reports no ACCEPT/REJECT decisions and that
     the boundary check completes without error.
- **[LOW] Fix Javadoc at lines 54-55 in BenchmarkRunner.java:** The class-level Javadoc currently
  says errors are reported via `System.err` with a non-zero exit code. Update it to reflect actual
  behavior: all output (including errors) is written to `System.out` as JSON via `HookOutput`,
  and the process exits with code 0 so Claude Code can parse the JSON response.
- **[LOW] Replace System.err.println retry/warning messages with SLF4J logger in BenchmarkRunner.java:**
  At lines ~211-213 (`extract-model` fallback warning) and lines ~562-564 (`persist-artifacts` retry
  message), replace `System.err.println(...)` with `log.warn(...)` calls using the existing SLF4J
  `Logger log` field. This follows project conventions that reserve `System.err` for non-structured
  diagnostics not intended for skill consumption.
- **[LOW] Replace String.join+concat with Files.write in BenchmarkRunner.java:** At lines 288-291
  in `detectChanges`, replace the two `Files.writeString(path, String.join("\n", lines) + "\n", UTF_8)`
  calls with `Files.write(path, lines, StandardCharsets.UTF_8)`. The `Files.write(Path, Iterable, Charset)`
  overload appends the system line separator after each element; verify that `git diff --no-index`
  treats LF-terminated files the same on Linux (where system separator is `\n`) — if behavior is
  identical, apply the change; if not, keep the current approach and add a comment explaining why.
- **[LOW] Add math documentation comments to existing SPRT tests in BenchmarkRunnerTest.java:** For
  each existing SPRT test (covering `init-sprt`, `update-sprt`, `check-boundary`), add a brief inline
  comment before the test body explaining the alpha (0.05), beta (0.05), p0 (0.95), and p1 (0.85)
  parameters, the SPRT_ACCEPT (2.944 = ln(19)) and SPRT_REJECT (-2.944 = ln(0.0526)) thresholds,
  and what mathematical outcome the test is verifying (e.g., "after N passes, log_ratio should exceed
  SPRT_ACCEPT").
- **NOTE — Deferred (concern 5, 4 temp files in detectChanges):** Reducing the 4 temporary files in
  `detectChanges` would require refactoring `parseSkill()` to accept `String` content instead of a
  `Path`, and `diffBodies()` to use an in-memory diff strategy instead of `git diff --no-index`.
  This constitutes significant structural refactoring unrelated to correctness. Deferred.
- **NOTE — Skipped (concern 10, MessageDigest per call):** `MessageDigest.getInstance("SHA-256")` is
  called inside `sha256Bytes()`, which is invoked at most twice per command invocation (never in a
  loop). Creating the digest once would save negligible work. Skipped.

## Post-conditions
- [ ] All 10 benchmark-runner commands produce identical JSON output in Java and original Bash
- [ ] benchmark-runner.sh is a thin wrapper (<20 lines) delegating to jlink binary
- [ ] E2E: Run instruction-builder-agent benchmark flow and confirm it works end-to-end
