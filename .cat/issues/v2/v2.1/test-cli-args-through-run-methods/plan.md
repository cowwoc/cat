# Plan

## Goal

Ensure every command-line option in every non-hook CLI class has test coverage through `run()`, and refactor
existing tests that call internal methods directly to go through `run()` instead.

This follows the "Test Through run(), Not Internal Methods" convention added to `.claude/rules/java.md` in
issue `2.1-thin-main-methods-and-add-run-tests`.

## Background

Issue `2.1-thin-main-methods-and-add-run-tests` added `run()` methods and `*MainTest.java` files for all
non-hook CLI classes. However:

1. Most `*MainTest.java` files only have null-check tests and 1-2 basic error-path tests
2. Many command-line options (flags, subcommands, validation paths) lack test coverage through `run()`
3. Existing tests in some files call internal methods (e.g., `getOutput()`, `analyzeSession()`, `getConcernBox()`)
   directly instead of going through `run()`

## Scope

### Part 1: Add missing argument-parsing tests through run()

For each CLI class with `run()`, ensure every command-line option and error path has a test:

- **Valid flag combinations** — verify `run()` returns 0 and produces expected output
- **Missing required flags** — verify `run()` returns 1
- **Invalid flag values** — verify `run()` returns 1
- **Unknown flags** — verify `run()` returns 1 (where applicable)
- **Help/usage output** — verify `run()` returns 0 and output contains "Usage"

Priority files (have substantial arg parsing with gaps):
- `EmpiricalTestRunner` — `--config`, `--trials`, `--model`, `--cwd`, `--output`, `--baseline`
- `GetAddOutput` — `--type`, `--name`, `--version`, `--issue-type`, `--dependencies`, `--parent`, `--path`
- `GetCheckpointOutput` — `--type`, `--issue-name`, `--tokens`, `--percent`, `--branch`, `--iteration`, `--total`
- `GetCleanupOutput` — `--project-dir`, `--phase`
- `GetIssueCompleteOutput` — `--issue-name`, `--target-branch`, `--scope-complete`
- `GetNextIssueOutput` — `--completed-issue`, `--target-branch`, `--session-id`, `--project-dir`, `--exclude-pattern`
- `SessionAnalyzer` — subcommands: `analyze`, `search`, `errors`, `file-history`
- `MarkdownWrapper` — `--width`, positional file arg, stdin mode

### Part 2: Refactor existing tests to use run()

Find tests that call internal methods directly (e.g., `getOutput()`, `analyzeSession()`, `getConcernBox()`)
and refactor them to go through `run()` with appropriate command-line arguments instead.

## Research Findings

- All 8 priority CLI classes exist in `client/src/main/java/io/github/cowwoc/cat/hooks/skills/` or
  `client/src/main/java/io/github/cowwoc/cat/hooks/util/`.
- All `*MainTest.java` files currently have 3–6 tests covering only null checks and unknown-flag errors.
- No `*MainTest.java` file calls internal methods directly — Part 2 requires no changes.
- `GetCleanupOutput.run()` with `--phase plan` and `--phase verify` reads from `System.in` (not injectable in
  unit tests). Only `--phase bogus` (invalid-phase validation) can be practically tested through `run()`.
- `SessionAnalyzer.run()` uses `resolveSessionPath(sessionId)` which builds the path as
  `scope.getClaudeSessionsPath().resolve(sessionId + ".jsonl")`. Sessions path =
  `claudeConfigPath.resolve("projects").resolve(encodeProjectPath(projectPath.toString()))`, where
  `encodeProjectPath` replaces `/`, `.`, and ` ` with `-`. For happy-path subcommand tests, create a minimal
  JSONL session file at `scope.getClaudeSessionsPath().resolve("test-session.jsonl")`.
- `GetIssueCompleteOutput.run()` with `--issue-name` calls `discoverAndRender()`, which gracefully falls
  back to scope-complete box when the project has no issues (IssueDiscovery returns NotFound).
- `GetNextIssueOutput.run()` gracefully handles missing lock files (releaseLock catches exceptions) and
  empty projects (findNextIssue returns scope-complete box) — happy-path test works with a temp dir.
- `MarkdownWrapper.run()` takes 4 parameters: `(JvmScope scope, String[] args, InputStream in, PrintStream out)`.
  `MarkdownWrapperMainTest` already injects `ByteArrayInputStream` — use the same pattern.
- `GetAddOutput.run()` with `--type issue|version --name X --version Y` is pure formatting (no IO), always
  succeeds.
- `GetCheckpointOutput.run()` with `--type issue-complete` requires non-blank `--tokens`, `--percent`,
  `--issue-name`, `--branch`; with `--type feedback-applied` requires non-blank `--iteration`, `--tokens`,
  `--total`, `--issue-name`, `--branch`.

## Test Pattern

All test files follow this pattern (do NOT deviate from it):

```java
@Test
public void someTest() throws IOException
{
  Path tempDir = Files.createTempDirectory("some-prefix-");
  try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
  {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    SomeClass.run(scope, new String[]{"--flag", "value"}, out);
    String output = buffer.toString(StandardCharsets.UTF_8).strip();
    requireThat(output, "output").isNotBlank();
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

For exception tests:
```java
@Test(expectedExceptions = IllegalArgumentException.class,
  expectedExceptionsMessageRegExp = ".*some.*pattern.*")
public void someErrorTest() throws IOException
{
  Path tempDir = Files.createTempDirectory("some-prefix-");
  try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
  {
    SomeClass.run(scope, new String[]{"--bad", "value"}, System.out);
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

For `MarkdownWrapper`, replace `ClaudeTool scope` with `JvmScope scope` and add `InputStream in` parameter
(4-arg `run()`).

For `SessionAnalyzer` happy-path tests, create the session file before calling `run()`:
```java
Path sessionsPath = scope.getClaudeSessionsPath();
Files.createDirectories(sessionsPath);
String minimalJsonl = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg1\",\"content\":[]}}\n";
Files.writeString(sessionsPath.resolve("test-session.jsonl"), minimalJsonl);
// Then call run() with args {"analyze", "test-session"} etc.
```

## Post-conditions

- [ ] Every `*MainTest.java` has tests for each command-line flag/option accepted by its `run()` method
- [ ] No test calls an internal method when the same functionality can be exercised through `run()`
- [ ] All tests pass (`mvn -f client/pom.xml verify`)
- [ ] Error paths return non-zero exit codes (verified via `requireThat(result, "result").isEqualTo(1)`)
- [ ] Happy paths return zero exit codes (verified via `requireThat(result, "result").isEqualTo(0)`)

## Jobs

### Job 1

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerMainTest.java`.

Add this test method:

```
trialsNonIntegerThrowsException():
  args: ["--trials", "not-a-number"]
  expectedExceptions: NumberFormatException
  (no expectedExceptionsMessageRegExp needed — just NumberFormatException)
```

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetAddOutputMainTest.java`.

Add these test methods:

```
issueTypeHappyPath():
  args: ["--type", "issue", "--name", "my-issue", "--version", "2.1"]
  assert: run() returns 0, output is non-blank

versionTypeHappyPath():
  args: ["--type", "version", "--name", "1.0", "--version", "2.1"]
  assert: run() returns 0, output is non-blank

invalidTypeThrowsException():
  args: ["--type", "bogus", "--name", "test", "--version", "2.1"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*bogus.*" (matches "...got: bogus")

missingNameThrowsException():
  args: ["--type", "issue", "--version", "2.1"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*--name.*"

missingVersionThrowsException():
  args: ["--type", "issue", "--name", "test"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*--version.*"

bugfixIssueTypeHappyPath():
  args: ["--issue-type", "bugfix", "--type", "issue", "--name", "my-bug", "--version", "2.1"]
  assert: run() returns 0, output is non-blank
```

After adding all tests, run `mvn -f client/pom.xml verify -e` in the worktree root. All tests must pass.

### Job 2

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetCheckpointOutputMainTest.java`.

Add these test methods:

```
issueCompleteTypeHappyPath():
  args: ["--type", "issue-complete", "--tokens", "100", "--percent", "50",
         "--issue-name", "my-issue", "--branch", "main"]
  assert: output is non-blank

feedbackAppliedTypeHappyPath():
  args: ["--type", "feedback-applied", "--iteration", "1", "--tokens", "100",
         "--total", "200", "--issue-name", "my-issue", "--branch", "main"]
  assert: output is non-blank

issueCompleteMissingTokensThrowsException():
  args: ["--type", "issue-complete", "--percent", "50"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*--tokens.*--percent.*"

feedbackAppliedMissingIterationThrowsException():
  args: ["--type", "feedback-applied", "--tokens", "100"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*--iteration.*"

invalidTypeThrowsException():
  args: ["--type", "bogus"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*Invalid type.*bogus.*"
```

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetCleanupOutputMainTest.java`.

Add this test method:

```
invalidPhaseThrowsException():
  args: ["--phase", "bogus"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*Unknown.*phase.*bogus.*"
  (Error message is: "Unknown --phase value 'bogus'. Expected: survey, plan, or verify.")
```

After adding all tests, run `mvn -f client/pom.xml verify -e` in the worktree root. All tests must pass.

### Job 3

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetIssueCompleteOutputMainTest.java`.

Add these test methods:

```
scopeCompleteHappyPath():
  args: ["--scope-complete", "v2.1"]
  assert: output is non-blank (getScopeCompleteBox("v2.1") returns formatted box)

issueNameHappyPath():
  args: ["--issue-name", "2.1-my-issue", "--target-branch", "main"]
  assert: output is non-blank
  (no next issue in empty project → falls back to getScopeCompleteBox("v2.1"))

missingIssueNameAndScopeCompleteThrowsException():
  args: [] (empty)
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*--issue-name.*"
```

Note: `noArgsThrowsException()` is already in the file. Verify the existing test covers the empty-args case;
if it does, the third test above is redundant — skip it and only add the first two.

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetNextIssueOutputMainTest.java`.

Add these test methods:

```
missingCompletedIssueThrowsException():
  args: ["--target-branch", "main", "--session-id", "test-session", "--project-dir", tempDir.toString()]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*Usage.*"

allRequiredFlagsHappyPath():
  args: ["--completed-issue", "2.1-my-issue", "--target-branch", "main",
         "--session-id", "test-session", "--project-dir", tempDir.toString()]
  assert: output is non-blank
  (no lock file → graceful; no issues in tempDir → falls back to scope-complete box)

excludePatternFlagIsRecognized():
  args: ["--completed-issue", "2.1-my-issue", "--target-branch", "main",
         "--session-id", "test-session", "--project-dir", tempDir.toString(),
         "--exclude-pattern", "skip-*"]
  assert: output is non-blank
```

After adding all tests, run `mvn -f client/pom.xml verify -e` in the worktree root. All tests must pass.

### Job 4

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerMainTest.java`.

Add these test methods. For error-path tests (subcommand with too few args), no session file is needed:

```
analyzeMissingSessionIdThrowsException():
  args: ["analyze"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*Usage.*analyze.*"

searchMissingPatternThrowsException():
  args: ["search", "some-session-id"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*Usage.*search.*"

errorsMissingSessionIdThrowsException():
  args: ["errors"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*Usage.*errors.*"

fileHistoryMissingPathPatternThrowsException():
  args: ["file-history", "some-session-id"]
  expectedExceptions: IllegalArgumentException
  expectedExceptionsMessageRegExp: ".*Usage.*file-history.*"
```

For happy-path tests (subcommands with a real session file), create the session file first:

```
analyzeSubcommandProducesOutput():
  setup:
    sessionsPath = scope.getClaudeSessionsPath()
    Files.createDirectories(sessionsPath)
    Files.writeString(sessionsPath.resolve("test-session.jsonl"),
      "{\"type\":\"assistant\",\"message\":{\"id\":\"msg1\",\"content\":[]}}\n")
  args: ["analyze", "test-session"]
  assert: output is non-blank (valid JSON string)

errorsSubcommandProducesOutput():
  setup: same session file as analyzeSubcommandProducesOutput
  args: ["errors", "test-session"]
  assert: output is non-blank

searchSubcommandProducesOutput():
  setup: same session file as analyzeSubcommandProducesOutput
  args: ["search", "test-session", "msg1"]
  assert: output is non-blank
```

Also add this missing null-parameter test:

```java
@Test(expectedExceptions = NullPointerException.class,
  expectedExceptionsMessageRegExp = ".*scope.*")
public void nullScopeThrowsException() throws IOException
{
  ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
  SessionAnalyzer.run(null, new String[]{"dummy"}, out);
}
```

Note: No tempDir or scope is needed here — the method throws before any IO, so there is no resource to open or close.

Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/MarkdownWrapperMainTest.java`.

Add these test methods:

```
nullInThrowsException():
  MarkdownWrapper.run(scope, new String[]{}, null, out)
  expectedExceptions: NullPointerException
  expectedExceptionsMessageRegExp: ".*in.*"

customWidthIsApplied():
  in: "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12 word13" (long line)
  args: ["--width", "20"]
  assert: every line in output has length <= 20 characters
  (Wrap content from stdin using --width 20, verify no line exceeds 20 chars)

filePathArgWrapsFileInPlace():
  setup:
    Path tempFile = Files.createTempFile("markdown-wrapper-test-", ".md")
    String longContent = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10"
    Files.writeString(tempFile, longContent)
  args: [tempFile.toString(), "--width", "20"]
  in: empty ByteArrayInputStream
  assert: the file was modified (content is different from the original)
  cleanup: Files.deleteIfExists(tempFile)
```

Update `.cat/issues/v2/v2.1/test-cli-args-through-run-methods/index.json` (relative to the worktree root):
- Set `"status": "closed"`

Commit message type: `test:`

After adding all tests, run `mvn -f client/pom.xml verify -e` in the worktree root. All tests must pass.

The `index.json` update must be in the SAME commit as the test changes for this job.
