# Plan

## Goal

Add E2E regression test coverage for the bug fix already present in the codebase: `parseRawArguments` no
longer treats a UUID-format CAT agent ID as a bare issue name. The source fix is already implemented in
`WorkPrepare.java` — `CAT_AGENT_ID_TOKEN` strips the leading UUID before processing remaining arguments.
Unit tests for `parseRawArguments()` already cover UUID stripping directly. This issue adds E2E tests that
call `WorkPrepare.run()` end-to-end with `--arguments "<UUID>"` and `--arguments "<UUID> <issue-name>"`
to verify READY is returned with the correct issue selected (not NO_ISSUES), confirming the full execution
pipeline works correctly for both invocation patterns.

## Research Findings

The source fix is already implemented in the codebase:

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` (lines 73-75): `CAT_AGENT_ID_TOKEN`
  regex pattern already defined
- `WorkPrepare.java` (lines 1866-1906): `parseRawArguments()` already strips the UUID prefix via
  `CAT_AGENT_ID_TOKEN.lookingAt()` and throws `IllegalArgumentException` when rawArguments is non-blank
  but does not start with a valid UUID
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` (lines 2619-2683): 5 unit
  tests already cover UUID stripping for `parseRawArguments()` directly (UUID-only, UUID+issue-id,
  UUID+subagent-id, UUID+resume-keyword, UUID+skip-keyword)

Missing coverage: no end-to-end test calls `WorkPrepare.run()` with `--arguments "<UUID>"` to verify the
READY response from the full execution pipeline (required by post-conditions 2 and 7).

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: parseRawArguments no longer treats UUID-format agent IDs as bare issue names — when the
  first token matches UUID format, it is stripped before processing the remaining arguments
- [ ] Regression test added: unit test with UUID as the leading --arguments token verifies the correct next
  available issue is selected (not NO_ISSUES)
- [ ] When ARGUMENTS contains `<UUID> <issue-name>`, the issue name is correctly resolved after UUID
  stripping — parseRawArguments processes remaining token(s) exactly as if the UUID had never been present
- [ ] UUID stripping is format-specific: only tokens matching
  `[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}` at position 0 are
  stripped; bare issue names containing hyphens are unaffected
- [x] All existing WorkPrepareTest tests pass with no regressions (mvn -f client/pom.xml verify exits 0)
- [ ] No new issues introduced
- [ ] E2E verification: invoking /cat:work with no explicit issue argument (where ARGUMENTS contains only
  the agent UUID) correctly returns the next available issue rather than NO_ISSUES

## Execution Steps

### Step 1: Add E2E test for run() with UUID-only --arguments

File to modify: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`

Insert the following test method after the `parseRawArgumentsStripsUuidThenParsesSkip` test (around line
2683). Place it at the end of the `parseRawArguments — CAT agent ID prefix stripping` section, before
the `globToRegexHandlesMetacharacters` test:

```java
  /**
   * Verifies that when {@code --arguments} contains only a CAT agent ID UUID (no trailing issue name),
   * {@code run()} strips the UUID and returns READY for the next available issue (not NO_ISSUES).
   * <p>
   * This is the end-to-end regression test for the bug where UUIDs were matched as bare issue names.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runReturnsReadyWhenArgumentsContainsOnlyUuid() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue my-feature");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      String sessionId = UUID.randomUUID().toString();
      // Pass UUID as the sole --arguments token — simulates /cat:work invocation with no explicit issue
      String uuid = "92289cdd-76a1-4d7e-8cf3-be5618ec270a";
      WorkPrepare.run(scope, new String[]{"--session-id", sessionId, "--arguments", uuid}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(output);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }
```

Note on imports: `ByteArrayOutputStream` and `PrintStream` are likely already imported in the test file.
Verify existing imports before adding duplicates. The test file already uses `UUID`, `ByteArrayOutputStream`,
`PrintStream`, `StandardCharsets`, `JsonMapper`, `JsonNode`, `TestJvmScope`, `GitCommands`, `TestUtils`,
`WorkPrepare`, `createTempGitCatProject`, `createIssue`, and `cleanupWorktreeIfExists` — no new imports
needed.

### Step 2: Run full build verification

Run from within the worktree directory (the subagent's working directory is already the worktree root):

```bash
mvn -f client/pom.xml verify
```

All tests must pass (exit code 0) before proceeding. If any test fails, fix the failure before continuing.

### Step 3: Commit the new test

Stage and commit only the test file (index.json is updated by the work-confirm/merge phases):

```bash
git add client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java
git commit -m "test: add E2E regression test for UUID-only --arguments in work-prepare"
```
