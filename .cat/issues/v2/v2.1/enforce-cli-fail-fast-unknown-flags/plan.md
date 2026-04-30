# Plan: enforce-cli-fail-fast-unknown-flags

## Problem

All CLI launchers that accept named flags (e.g., `--issue-id`, `--exclude-pattern`) silently ignore unknown flags
instead of failing fast. The root cause was observed via M580: calling `work-prepare --include-pattern foo` silently
ignored the unknown `--include-pattern` flag, so `work-prepare` ran with no filter, selecting the wrong issue.
Any typo or stale flag in a skill invocation is silently swallowed, causing incorrect behavior with no diagnostic.

## Parent Requirements

None

## Reproduction Code

```bash
# work-prepare silently ignores the unknown --include-pattern flag and proceeds,
# selecting whatever issue it finds rather than failing with an error.
"${CLAUDE_PLUGIN_DATA}/client/bin/work-prepare" --include-pattern "v2.1-*"
# Expected: ERROR: Unknown flag '--include-pattern'. Valid flags: --session-id, ...
# Actual: {"status":"READY","issueId":"2.1-some-unintended-issue",...}
```

## Expected vs Actual

- **Expected:** Each launcher exits immediately with a descriptive error that names the unknown flag and lists all
  valid flags when an unrecognized flag is passed.
- **Actual:** Unknown flags are silently skipped. The launcher continues with default values for the missing input,
  producing incorrect results.

## Root Cause

Five `run()` methods and one `main()` method contain a named-flag switch statement with a `default -> { }` (empty)
or `default -> { // ignore unknown flags }` branch. When an unrecognized flag is passed, the switch falls through to
the default branch silently.

Affected files:
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` —
  flags: `--session-id`, `--exclude-pattern`, `--issue-id`, `--trust-level`, `--arguments`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/BatchReader.java` —
  flags: `--pattern`, `--max-files`, `--context-lines`, `--file-type`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/HookRegistrar.java` —
  flag: `--can-block` (handled in default); all other flags handled normally, but any unrecognized flag is silently
  skipped
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java` —
  flags: `--type`, `--issue-name`, `--tokens`, `--percent`, `--branch`, `--iteration`, `--total`, `--project-dir`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` —
  flags: `--issue-name`, `--target-branch`, `--scope-complete`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java` —
  flags: `--completed-issue`, `--target-branch`, `--session-id`, `--project-dir`, `--exclude-pattern`

## Research Findings

The fix differs slightly by launcher:

**`WorkPrepare`** uses `toErrorJson(scope, message)` for error output (skill CLI tool, business-format JSON). It also
has a `args.length - 1` loop bound that must be changed to `args.length` with per-case bounds checks.

**`BatchReader`** uses `hookOutput.block(message)` written to `out` (HookOutput format). It also has a
`args.length - 1` loop bound on the inner loop starting at index 1 (after the positional pattern arg) — same fix
needed: change to `args.length` with per-case bounds check (`if (i + 1 >= args.length)`).

**`HookRegistrar`** currently handles `--can-block` inside the default case. The fix must restructure the default
case so `--can-block` is its own `case` arm, and the new `default` arm rejects unknown flags using `hookOutput.block()`.

**`GetCheckpointOutput`** uses `System.err.println(message)` + `System.exit(1)` for validation errors. The inner
parse loop increments by `i += 2` (reads flag+value pairs), so no loop bound off-by-one issue.

**`GetIssueCompleteOutput`** same `System.err` + `System.exit(1)` pattern. Inner loop uses `i += 2`.

**`GetNextIssueOutput`** (private `parseArgs()` helper) uses `i += 2` loop. Throw
`IllegalArgumentException` from the private helper; the caller must convert it to a block response.

The error message must include:
1. The unknown flag name
2. The full list of valid flags (copied from the Javadoc `<ul>` in each launcher)

The error message must include:
1. The unknown flag name
2. The full list of valid flags (copied from the Javadoc `<ul>` in each launcher)

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Any skill that passes an incorrect or stale flag to a launcher will now fail visibly. This is
  the desired behavior; no correct invocation will break.
- **Mitigation:** Regression tests added for each launcher; existing tests continue to pass.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — replace `default -> { // ignore }` with
  fail-fast error; also fix the loop bound (currently `args.length - 1`) which silently skips the last flag if it
  stands alone — change to `args.length` and add bounds checking inside each case before accessing `args[i+1]`.
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/BatchReader.java` — same replacement
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/HookRegistrar.java` — promote `--can-block` to its own
  case arm; new default rejects unknown flags
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java` — same replacement
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` — same replacement
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java` — same replacement
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add test verifying unknown flag
  causes error
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BatchReaderTest.java` — add test verifying unknown flag
  causes error (or create if does not yet exist)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetNextIssueOutputTest.java` — add test (or create if
  needed)

## Test Cases

- [ ] `WorkPrepare.run()` with `--include-pattern foo` → ERROR output containing "Unknown flag" and listing valid flags
- [ ] `BatchReader.run()` with `--unknown-flag foo` → same
- [ ] `GetNextIssueOutput.run()` (or `getOutput()`) with `--unknown-flag foo` → same
- [ ] Existing tests continue to pass (valid flag usage unchanged)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Fix `WorkPrepare.java`: replace `default -> { // ignore unknown flags }` with fail-fast block. The error must list
  valid flags: `--session-id`, `--exclude-pattern`, `--issue-id`, `--trust-level`, `--arguments`. Also fix the loop
  bound: change `args.length - 1` to `args.length` and add a bounds check (`if (i + 1 >= args.length)`) before each
  `++i; args[i]` access in each case arm. The error format for this skill CLI tool:
  `out.println(toErrorJson(scope, "Unknown flag '" + args[i] + "'. Valid flags: --session-id, --exclude-pattern, --issue-id, --trust-level, --arguments")); return;`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

- Fix `BatchReader.java`: replace `default -> { // ignore unknown flags }` with fail-fast error using
  `out.println(hookOutput.block("Unknown flag '" + args[i] + "'. Valid flags: --max-files, --context-lines, --file-type")); return;`.
  Also fix the loop bound: the inner loop at line ~199 uses `i < args.length - 1`; change to `i < args.length` and
  add a bounds check (`if (i + 1 >= args.length)`) before each `++i; args[i]` access in each case arm.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/BatchReader.java`

- Fix `HookRegistrar.java`: move `--can-block` out of the default arm into its own `case "--can-block" -> canBlock = true;`
  arm. Then change `default -> { }` to emit an error listing valid flags:
  `--name`, `--trigger`, `--matcher`, `--script-content`, `--claude-dir`, `--can-block`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/HookRegistrar.java`

- Fix `GetCheckpointOutput.java`: replace `default -> { }` with fail-fast error. Valid flags:
  `--type`, `--issue-name`, `--tokens`, `--percent`, `--branch`, `--iteration`, `--total`, `--project-dir`.
  The inner parse loop uses `i += 2` increments (not `++i`); the error goes to `System.err` then `System.exit(1)`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java`

- Fix `GetIssueCompleteOutput.java`: replace `default -> { }` with fail-fast error. Valid flags:
  `--issue-name`, `--target-branch`, `--scope-complete`. Same `System.err` + `System.exit(1)` pattern as the
  surrounding code in that file.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java`

- Fix `GetNextIssueOutput.java`: replace `default -> { }` in `parseArgs()` with fail-fast. Valid flags:
  `--completed-issue`, `--target-branch`, `--session-id`, `--project-dir`, `--exclude-pattern`.
  Throw `IllegalArgumentException("Unknown flag '" + args[i] + "'. Valid flags: --completed-issue, --target-branch, --session-id, --project-dir, --exclude-pattern")`
  since `parseArgs()` is a private helper whose caller already wraps in try/catch for `IllegalArgumentException`.
  If no such wrapping exists, add the throw and ensure the caller converts it to a block response.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java`

### Wave 2

- Add regression tests:
  - In `WorkPrepareTest.java`: add `unknownFlag_causesError()` — calls `WorkPrepare.run()` with
    `["--include-pattern", "foo"]` and asserts the output JSON contains `"status"` equal to `"ERROR"` and the message
    contains `"Unknown flag"` and `"--include-pattern"`.
  - In `BatchReaderTest.java` (create if absent): add `unknownFlag_causesError()` — calls `BatchReader.run()` with
    an unknown flag and asserts error output.
  - In `GetNextIssueOutputTest.java` (create if absent): add `unknownFlag_causesError()` — calls
    `GetNextIssueOutput.run()` with an unknown flag and asserts block response.
  - Run `mvn -f client/pom.xml test` and confirm all tests pass.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/BatchReaderTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetNextIssueOutputTest.java`

## Post-conditions

- [ ] All CLI launchers with named-flag switch statements reject unknown flags with an error message naming the
  unknown flag and listing all valid flags
- [ ] Regression tests added for `WorkPrepare`, `BatchReader`, and at least one other launcher (`GetNextIssueOutput`)
  verifying unknown flag rejection
- [ ] No regressions: `mvn -f client/pom.xml test` exits 0
- [ ] E2E verification: calling
  `"${CLAUDE_PLUGIN_DATA}/client/bin/work-prepare" --include-pattern "v2.1-*"` produces a JSON response containing
  `"status": "ERROR"` with a message that names `--include-pattern` and lists valid flags
