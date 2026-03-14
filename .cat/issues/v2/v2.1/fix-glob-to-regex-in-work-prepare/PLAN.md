# Plan: fix-glob-to-regex-in-work-prepare

## Problem

`work-prepare` throws `PatternSyntaxException` ("Unclosed character class") when any PLAN.md contains
backtick-quoted strings with regex metacharacters (e.g., `[ ]`). The `extractPlannedFiles` method extracts
all backtick-quoted strings from "Files to Create" and "Files to Modify" sections, including non-file-path
content like `- **Dependencies:** []`. When such strings contain `*`, the glob-to-regex conversion escapes
only `.` and replaces `*` with `[^/]*`, leaving `[` and `]` unescaped, causing `Pattern.compile` to fail.

## Parent Requirements

None

## Reproduction Code

```
// Extracted "planned file" from PLAN.md backtick content:
// - **Dependencies:** []
// After replace(".", "\\.").replace("*", "[^/]*"):
// - [^/]*[^/]*Dependencies:[^/]*[^/]* []
// Pattern.compile(".*" + above) → PatternSyntaxException: Unclosed character class near index 39
String buggyPattern = "- **Dependencies:** []"
    .replace(".", "\\.")
    .replace("*", "[^/]*");
Pattern.compile(".*" + buggyPattern); // throws PatternSyntaxException
```

## Expected vs Actual

- **Expected:** `work-prepare` runs successfully; backtick strings containing regex metacharacters do not crash pattern compilation
- **Actual:** `PatternSyntaxException: Unclosed character class near index 39` causes `work-prepare` to return `{"status":"ERROR","message":"Unexpected error: Unclosed character class near index 39 ..."}`

## Root Cause

The glob-to-regex conversion in `WorkPrepare.java` (lines 1362–1367) only escapes `.` → `\\.` and replaces
`*` → `[^/]*`, but does not escape other regex metacharacters (`[`, `]`, `(`, `)`, `{`, `}`, `+`, `?`, `^`,
`$`, `|`). `Pattern.quote()` should be used on the literal segments between `*` characters.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** The file-overlap suspicious-commit detection may stop matching files if the fix is incorrect
- **Mitigation:** Existing WorkPrepare tests cover the suspicious-commit detection path; a new test covers the crash scenario

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — replace the manual `.replace()` chain with `Pattern.quote()` on literal segments (lines 1362–1367)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add a test for the bug scenario

## Test Cases

- [ ] Bug scenario: a PLAN.md containing backtick-quoted text with `[`, `]`, and `*` does not cause PatternSyntaxException
- [ ] Existing glob patterns (e.g., `plugin/agents/stakeholder-*.md`) still match correctly

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` at lines 1362–1367 (inside the `for (String plannedFile : plannedFiles)` loop within `checkTargetBranchCommits`), replace:
  ```java
  String regexPattern = plannedFile.
    replace(".", "\\.").
    replace("*", "[^/]*");
  globPatterns.put(plannedFile, Pattern.compile(".*" + regexPattern));
  ```
  with:
  ```java
  String[] parts = plannedFile.split("\\*", -1);
  StringJoiner regexJoiner = new StringJoiner("[^/]*");
  for (String part : parts)
    regexJoiner.add(Pattern.quote(part));
  String regexPattern = regexJoiner.toString();
  globPatterns.put(plannedFile, Pattern.compile(".*" + regexPattern));
  ```
  Also check the imports section of `WorkPrepare.java`: if there is no line `import java.util.StringJoiner;` already present, add it in alphabetical order among the other `java.util.*` imports. `Pattern.quote()` is already available — no new import needed for it.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

- Add a test method `globToRegexHandlesMetacharacters` to `WorkPrepareTest.java`. The exact method body to write:
  ```java
  /**
   * Verifies that a PLAN.md containing backtick-quoted text with regex metacharacters
   * (e.g., "[]") does not cause a PatternSyntaxException during execute.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void globToRegexHandlesMetacharacters() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "my-feature", "open");

      // Overwrite the PLAN.md with a "## Files to Modify" section whose backtick entry
      // contains * and [] — these are regex metacharacters that trigger the bug
      Path planPath = projectDir.resolve(".cat").resolve("issues")
        .resolve("v2").resolve("v2.1").resolve("my-feature").resolve("PLAN.md");
      Files.writeString(planPath, """
        # Plan: my-feature

        ## Goal

        Test regex metacharacter handling.

        ## Files to Modify

        - Remove the line `- **Dependencies:** []`
        """);

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue with metacharacter PLAN.md");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isNotEqualTo("ERROR");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }
  ```
  Insert this method after the last existing `@Test` method in the file (before the private helper methods). The `Files` import is already present. No new imports are required.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`

- Run all tests from the root of the worktree created for this issue. To find that directory, run `git worktree list` and identify the worktree whose branch matches the issue branch name (e.g., `2.1-fix-glob-to-regex-in-work-prepare`). Then run: `cd <that-worktree-path> && mvn -f client/pom.xml test`. All tests must exit 0.
  - Files: (none — validation only)

## Post-conditions

- [ ] `work-prepare` returns a non-ERROR status when PLAN.md contains backtick-quoted text with regex metacharacters
- [ ] Glob pattern matching (e.g., `plugin/agents/stakeholder-*.md`) still correctly identifies suspicious commits
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
