# Plan: fix-cli-error-json-to-stdout

## Objective
Add a convention section to `.claude/rules/hooks.md` requiring CLI tools invoked by skills to write ALL structured JSON
output to stdout, exit with code 0, and use the standard Claude Code hook JSON output format. Then fix all existing
violations across the codebase.

## Background
CLI tools like `work-prepare`, `git-squash`, `git-rebase`, etc. are invoked as subprocesses by skills. The calling skill
parses JSON from stdout. When error JSON is written to stderr instead, the skill receives empty stdout and treats it as
"no result" — losing the structured error message and potentially triggering incorrect recovery behavior (M542).

Additionally, exit code semantics matter for Claude Code hook integration:
- **Exit code 0** tells Claude Code to parse stdout as JSON.
- **Non-zero exit code** causes Claude Code to feed stderr directly to Claude as plain text, losing the structured error.

The project already has `HookOutput.java` which builds standard Claude Code hook JSON output format with fields like
`decision`, `reason`, `stopReason`, `continue`, `suppressOutput`, and `systemMessage`.

## Convention to Add

In `.claude/rules/hooks.md`, add a new section after the existing hook registration table:

### CLI Tool Output Convention

CLI tools invoked by skills (via `main()` methods) must:

1. Write ALL structured JSON output to `System.out` — including error responses
2. Exit with code 0 so Claude Code parses stdout as JSON
3. Use the standard Claude Code hook JSON output format via `HookOutput` utility

`System.err` is reserved for non-structured diagnostics (e.g., debug logging, stack traces during development) that are
not intended for skill consumption.

**Standard hook JSON output fields:**
- `decision` (string) — e.g., `"block"` to indicate a blocked operation
- `reason` (string) — human-readable explanation of the decision
- `continue` (bool) — whether processing should continue
- `stopReason` (string) — reason for stopping
- `suppressOutput` (bool) — whether to suppress output
- `systemMessage` (string) — message for the system context

**Why:** Skills parse JSON from stdout. Exit code 0 tells Claude Code to parse stdout as JSON. Non-zero exit codes
cause stderr to be fed to Claude as plain text, losing the structured error. Custom JSON formats like
`{"status":"ERROR","message":"..."}` are not recognized by Claude Code's hook output parsing.

**Pattern:**
```java
// Good — use HookOutput, write to stdout, exit 0
catch (IOException e)
{
  HookOutput hookOutput = new HookOutput(scope);
  System.out.println(hookOutput.block(e.getMessage()));
  System.exit(0);
}

// Avoid — custom JSON format to stderr with exit 1
catch (IOException e)
{
  System.err.println("""
    {
      "status": "ERROR",
      "message": "%s"
    }""".formatted(e.getMessage().replace("\"", "\\\"")));
  System.exit(1);
}
```

## Files to Modify

### Convention
- `.claude/rules/hooks.md` — Add "CLI Tool Output Convention" section after the existing hook registration table

### Java Source (violations to fix)

Each violation requires three changes:
1. Change `System.err.println` to `System.out.println`
2. Change `System.exit(1)` to `System.exit(0)`
3. Replace custom `{"status":"ERROR","message":"..."}` JSON with standard hook output format via `HookOutput`

Files:
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — Line ~1807: `System.err.println` with
  custom JSON in RuntimeException|AssertionError catch
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebase.java` — Lines ~710-760: `System.err.println` JSON
  outputs in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java` — Lines ~338-388: `System.err.println` JSON
  outputs in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitAmend.java` — Lines ~259-346: `System.err.println` JSON
  outputs in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java` — Lines ~282-350: `System.err.println`
  JSON outputs in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — Line ~2273: plain text error to
  stdout (needs standard hook JSON format)

### Java Tests
- Update any tests that assert stderr output for these CLI tools to assert stdout instead

## Sub-Agent Waves

### Wave 2 — Fix Remaining Violations

- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`, replace the inline JSON string in the
  `main()` RuntimeException/AssertionError catch block (around line 1805) with `HookOutput.block()` so it uses the
  standard hook output format via the `HookOutput` utility instead of a hand-constructed JSON string.
- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitAmend.java`, replace the `buildErrorJson()` method
  (which returns `{"status":"ERROR","message":"..."}`) and all its call sites with `HookOutput.block()` so that all
  error results flowing to stdout from `main()` use the standard Claude Code hook JSON output format
  (`decision`/`reason` fields).
- Run `mvn -f client/pom.xml test` and confirm all tests pass (exit code 0). Update any existing tests that asserted
  the old `{"status":"ERROR"}` format from `GitAmend` to assert the new `HookOutput` (`decision`/`reason`) format.
- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/BatchReader.java`, replace the custom
  `{"status":"ERROR","message":"..."}` JSON strings written to `System.err` at lines 159 and 230 with
  `HookOutput.block()` written to `System.out`, and change all `System.exit(1)` calls that follow structured JSON
  output (lines 164, 188, 201, 235) to `System.exit(0)`.
- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/WriteAndCommit.java`, replace the custom
  `{"status":"error",...}` JSON strings written to `System.err` at lines 183-187 and 206-210 with `HookOutput.block()`
  written to `System.out`, and change the corresponding `System.exit(1)` calls at lines 188 and 211 to
  `System.exit(0)`.
- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`, replace all custom
  `{"status":"error",...}` JSON strings written to `System.err` at lines 561, 587, 604, and 624 with
  `HookOutput.block()` written to `System.out`, and change all corresponding `System.exit(1)` calls in those error
  paths to `System.exit(0)`.
- Run `mvn -f client/pom.xml test` and confirm all tests pass (exit code 0). Update any existing tests for
  `BatchReader`, `WriteAndCommit`, and `RecordLearning` that asserted the old `{"status":"ERROR"}` or
  `{"status":"error"}` format or stderr output to assert the new `HookOutput` (`decision`/`reason`) format on stdout.

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] Convention section "CLI Tool Output Convention" exists in `.claude/rules/hooks.md`
- [ ] All `main()` methods in CLI tools write JSON to `System.out` using `HookOutput` utility
- [ ] All `main()` methods exit with code 0 (not code 1) when producing structured JSON output
- [ ] No `main()` method writes structured JSON to `System.err`
- [ ] No `main()` method uses custom `{"status":"ERROR","message":"..."}` JSON format
- [ ] All tests pass: `mvn -f client/pom.xml test`
- [ ] E2E: Invoke a CLI tool with invalid args and confirm standard hook JSON appears on stdout with exit code 0
