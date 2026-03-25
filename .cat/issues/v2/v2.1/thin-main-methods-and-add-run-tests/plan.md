# Plan

## Goal

Ensure every non-hook CLI class with a `main()` method:
1. Delegates entirely to a `static void run(scope, args, PrintStream)` method with no
   other logic remaining in `main()`
2. Has unit tests that exercise `run()` directly, covering argument parsing, error paths,
   and representative happy-path inputs

This closes the coverage gap where bugs in `main()` argument handling and scope selection
go undetected because `main()` reads from `System.in` or `System.out` and cannot be
invoked safely from parallel unit tests.

## Background

There are 63 `main()` methods in the codebase split into three categories:

**Already correct — no work needed:**
- Hook handlers (14 classes: `PreToolUseHook`, `SessionStartHook`, etc.): already thin via
  `HookRunner.execute(ClassName::new, args)`
- CLI tools already following the pattern (9 classes with `*MainTest.java`):
  `BatchReader`, `GetDiffOutput`, `GitAmend`, `GitMergeLinear`, `GitRebase`, `GitSquash`,
  `RecordLearning`, `WorkPrepare`, `WriteAndCommit`

**Needs work — CLI tools with `run()` but no `*MainTest.java`** (5 classes):
`AotTraining`, `HookRegistrar`, `IssueLock`, `VerifyDeferPlanGeneration`, `WriteSessionMarker`

**Needs work — CLI tools with logic in `main()` and no `run()`** (34 classes):

*skills/ package (19 classes):*
`EmpiricalTestRunner`, `GetAddOutput`, `GetCheckpointOutput`, `GetCleanupOutput`, `GetConfigOutput`,
`GetIssueCompleteOutput`, `GetNextIssueOutput`, `GetOutput`, `GetRetrospectiveOutput`,
`GetStakeholderConcernBox`, `GetStakeholderReviewBox`, `GetStakeholderSelectionBox`,
`GetStatusOutput`, `GetStatuslineOutput`, `GetSubagentStatusOutput`, `GetTokenReportOutput`,
`ProgressBanner`, `SkillTestRunner`, `VerifyAudit`

*util/ package (13 classes):*
`ExistingWorkChecker`, `Feedback`, `GetFile`, `InvestigationContextExtractor`, `IssueCreator`,
`MarkdownWrapper`, `MergeAndCleanup`, `RetrospectiveMigrator`, `RootCauseAnalyzer`,
`SessionAnalyzer`, `StatusAlignmentValidator`, `StatuslineCommand`, `StatuslineInstall`

*hooks/ package (2 classes):*
`EnforceStatusOutput`, `TokenCounter`

**Excluded from this issue:**
- `GetSkill` — covered by `2.1-fix-get-skill-uses-main-claude-hook`
- `HookRunner` — infrastructure class that defines `execute()`, not a CLI tool

## Research Findings

### Scope Type Mapping

Each class's `run()` method must accept the correct scope type based on what `main()` currently creates:

| Scope in main() | run() accepts | Test scope |
|------------------|---------------|------------|
| `new MainClaudeTool()` → `ClaudeTool` | `JvmScope` (wider interface, testable) | `TestClaudeTool(tempDir, tempDir)` |
| `new MainClaudeHook()` → `ClaudeHook` | `JvmScope` (wider interface, testable) | `TestClaudeTool(tempDir, tempDir)` |
| No scope (pure utility) | `JvmScope` | `TestClaudeTool(tempDir, tempDir)` |

All 34 classes needing run() extraction use `MainClaudeTool` except:
- `EnforceStatusOutput` uses `MainClaudeHook`
- `MarkdownWrapper` and `StatusAlignmentValidator` use no scope (pure utilities)

### Correct Patterns (from existing examples)

**main() pattern (BatchReader.java lines 159-165):**
```java
public static void main(String[] args)
{
  try (ClaudeTool scope = new MainClaudeTool())
  {
    run(scope, args, System.out);
  }
}
```

**run() pattern (BatchReader.java line 178):**
```java
public static void run(JvmScope scope, String[] args, PrintStream out)
{
  requireThat(args, "args").isNotNull();
  requireThat(out, "out").isNotNull();
  // All business logic here
}
```

**MainTest pattern (BatchReaderMainTest.java):**
```java
public class BatchReaderMainTest
{
  @Test
  public void noArgsProducesBlockResponseWithUsage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("batch-reader-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      BatchReader.run(scope, new String[]{}, out);
      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      // Verify output content
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
```

### Classes That Read from stdin

Some classes read from `System.in` in their `main()`. For these, `run()` must accept an `InputStream` parameter
in addition to the standard `(JvmScope, String[], PrintStream)`:
- `EnforceStatusOutput` — reads hook JSON from stdin
- `StatusAlignmentValidator` — reads validation input from stdin
- `TokenCounter` — reads file paths from stdin

For these, the signature is: `public static void run(JvmScope scope, String[] args, InputStream in, PrintStream out)`

### main() Error Handling Pattern

`main()` must follow the single-scope error handling pattern from `.claude/rules/java.md`:
```java
public static void main(String[] args)
{
  try (ClaudeTool scope = new MainClaudeTool())
  {
    try
    {
      run(scope, args, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(ClassName.class);
      log.error("Unexpected error", e);
      System.out.println(new ClaudeHook(scope).block(
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
```

For classes that currently catch `IOException` or `IllegalArgumentException` in `main()`, move those catches
into `run()` (they become part of the business logic). `main()` only catches `RuntimeException | AssertionError`.

## Pre-conditions

- All 63 `main()` classes exist in `client/src/main/java/`

## Post-conditions

- [x] Every non-hook CLI class has a `public static void run(scope, String[] args, PrintStream out)`
  method (or equivalent signature matching the class's scope type)
- [x] Every `main()` in a non-hook CLI class contains exactly: scope instantiation +
  `run(scope, args, System.out)` + unexpected-error catch block — no argument parsing,
  no business logic, no conditional branches
- [x] Every non-hook CLI class has a `*MainTest.java` (or equivalent tests in its existing
  test file) that calls `run()` directly with:
  - Missing/invalid arguments → verifies error output and/or exception
  - Representative valid arguments → verifies output contains expected content
- [x] `mvn -f client/pom.xml verify -e` passes

## TDD Approach

For each class in the "needs work" categories:
1. Write failing tests for `run()` (argument validation, error paths, happy path)
2. Extract `run()` from `main()` logic (or add it where absent)
3. Thin `main()` to scope + `run()` delegation only
4. Verify tests pass

## Implementation Notes

- The `run()` method signature must match the class's scope type:
  - Infrastructure CLI tools (no session vars): `JvmScope`
  - Session CLI tools: `ClaudeTool`
- Argument parsing and all error handling (including `IOException` and
  `IllegalArgumentException`) belong in `run()`, not `main()`
- `main()` catches only `RuntimeException | AssertionError` for unexpected errors
- `GetSkill` is covered by `2.1-fix-get-skill-uses-main-claude-hook` — skip it here

## Sub-Agent Waves

### Wave 1

Extract `run()` and add `*MainTest.java` for all **skills/ package** classes (19 classes) plus the
5 classes that already have `run()` but need `*MainTest.java`:

**Need run() extraction + MainTest (skills/ package — all use `MainClaudeTool`, so run() accepts `JvmScope`):**

For each of these 19 classes in `client/src/main/java/io/github/cowwoc/cat/hooks/skills/`:
1. `EmpiricalTestRunner`
2. `GetAddOutput`
3. `GetCheckpointOutput`
4. `GetCleanupOutput`
5. `GetConfigOutput`
6. `GetIssueCompleteOutput`
7. `GetNextIssueOutput`
8. `GetOutput`
9. `GetRetrospectiveOutput`
10. `GetStakeholderConcernBox`
11. `GetStakeholderReviewBox`
12. `GetStakeholderSelectionBox`
13. `GetStatusOutput`
14. `GetStatuslineOutput`
15. `GetSubagentStatusOutput`
16. `GetTokenReportOutput`
17. `ProgressBanner`
18. `SkillTestRunner`
19. `VerifyAudit`

For each class above:
1. Read the current `main()` method to understand its structure
2. Create `public static void run(JvmScope scope, String[] args, PrintStream out)` containing all logic from `main()`
3. Add `requireThat(args, "args").isNotNull(); requireThat(out, "out").isNotNull();` at the start of `run()`
4. Replace all `System.out.println(...)` / `System.out.print(...)` calls with `out.println(...)` / `out.print(...)`
5. Move any `IOException`/`IllegalArgumentException` catch blocks from `main()` into `run()`
6. Thin `main()` to: `try (ClaudeTool scope = new MainClaudeTool()) { try { run(scope, args, System.out); } catch (RuntimeException | AssertionError e) { ... } }`
7. Create `*MainTest.java` in `client/src/test/java/io/github/cowwoc/cat/hooks/test/` with at minimum:
   - `noArgsProducesErrorOutput()` — calls `run(scope, new String[]{}, out)` and verifies error/block output
   - Follow the `BatchReaderMainTest` pattern: `TestClaudeTool`, `ByteArrayOutputStream`, `PrintStream`, try-finally cleanup

**Need MainTest only (already have run()):**

For each of these 5 classes, create `*MainTest.java` following the same pattern:
20. `AotTraining` — in `client/src/main/java/io/github/cowwoc/cat/hooks/` (uses `MainClaudeHook`, run() returns int)
21. `HookRegistrar` — in `client/src/main/java/io/github/cowwoc/cat/hooks/util/`
22. `IssueLock` — in `client/src/main/java/io/github/cowwoc/cat/hooks/util/`
23. `VerifyDeferPlanGeneration` — in `client/src/main/java/io/github/cowwoc/cat/hooks/util/`
24. `WriteSessionMarker` — in `client/src/main/java/io/github/cowwoc/cat/hooks/util/`

For each class above:
1. Read the existing `run()` method signature
2. Create `*MainTest.java` that calls `run()` with missing/invalid args and verifies error output

Commit: `refactor: extract run() and add MainTest for skills/ package CLI classes`

### Wave 2

Extract `run()` and add `*MainTest.java` for all **util/ package** and **hooks/ package** classes (15 classes):

**Need run() extraction + MainTest (util/ package — all use `MainClaudeTool` except where noted):**

For each of these 13 classes in `client/src/main/java/io/github/cowwoc/cat/hooks/util/`:
1. `ExistingWorkChecker`
2. `Feedback`
3. `GetFile`
4. `InvestigationContextExtractor`
5. `IssueCreator`
6. `MarkdownWrapper` — **no scope** (pure utility); add `JvmScope` parameter for consistency; `run()` also needs `InputStream in` since it reads from stdin
7. `MergeAndCleanup`
8. `RetrospectiveMigrator`
9. `RootCauseAnalyzer`
10. `SessionAnalyzer`
11. `StatusAlignmentValidator` — **no scope** (pure utility); add `JvmScope` parameter; `run()` needs `InputStream in`
12. `StatuslineCommand`
13. `StatuslineInstall`

**Need run() extraction + MainTest (hooks/ package):**

14. `EnforceStatusOutput` — uses `MainClaudeHook`; `run()` needs `InputStream in` since it reads hook JSON from stdin
15. `TokenCounter` — uses `MainClaudeTool`; `run()` needs `InputStream in` since it reads file paths from stdin

Apply the same transformation pattern as Wave 1. For classes that read from `System.in`, the signature is:
`public static void run(JvmScope scope, String[] args, InputStream in, PrintStream out)`

After all classes are updated, run the full test suite:
```bash
mvn -f client/pom.xml verify -e
```

Update `index.json`: set status to `closed`, progress to 100%.

Commit: `refactor: extract run() and add MainTest for util/ and hooks/ package CLI classes`
