/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetNextIssueOutput;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.cat.hooks.util.IssueGoalReader;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetNextIssueOutput functionality.
 * <p>
 * Tests verify box rendering output structure and business logic for
 * reading issue goals from PLAN.md fixtures.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetNextIssueOutputTest
{
  /**
   * Verifies that IssueGoalReader extracts first paragraph from PLAN.md.
   */
  @Test
  public void readIssueGoalExtractsFirstParagraph() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-issue");
    try
    {
      Path planPath = tempDir.resolve("PLAN.md");
      String planContent = """
        # Issue Plan

        ## Goal

        This is the first paragraph.

        This is the second paragraph.

        ## Implementation

        Steps here.
        """;
      Files.writeString(planPath, planContent);

      String goal = IssueGoalReader.readGoalFromPlan(planPath);
      requireThat(goal, "goal").isEqualTo("This is the first paragraph.");
    }
    finally
    {
      Files.deleteIfExists(tempDir.resolve("PLAN.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that IssueGoalReader returns fallback message when no Goal section.
   */
  @Test
  public void readIssueGoalReturnsNoGoalFoundWhenSectionMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-issue");
    try
    {
      Path planPath = tempDir.resolve("PLAN.md");
      String planContent = """
        # Issue Plan

        ## Implementation

        Steps here.
        """;
      Files.writeString(planPath, planContent);

      String goal = IssueGoalReader.readGoalFromPlan(planPath);
      requireThat(goal, "goal").isEqualTo("No goal found");
    }
    finally
    {
      Files.deleteIfExists(tempDir.resolve("PLAN.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that IssueGoalReader returns fallback message when PLAN.md missing.
   */
  @Test
  public void readIssueGoalReturnsNoGoalFoundWhenFileMissing()
  {
    Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "test-issue-missing-" +
      System.nanoTime());
    Path planPath = tempDir.resolve("PLAN.md");
    String goal = IssueGoalReader.readGoalFromPlan(planPath);
    requireThat(goal, "goal").isEqualTo("No goal found");
  }

  /**
   * Verifies that IssueGoalReader trims leading/trailing whitespace from goal text.
   */
  @Test
  public void readIssueGoalTrimsWhitespace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-issue");
    try
    {
      Path planPath = tempDir.resolve("PLAN.md");
      String planContent = """
        ## Goal

          First paragraph with leading/trailing spaces.

        ## Next
        """;
      Files.writeString(planPath, planContent);

      String goal = IssueGoalReader.readGoalFromPlan(planPath);
      requireThat(goal, "goal").isEqualTo("First paragraph with leading/trailing spaces.");
    }
    finally
    {
      Files.deleteIfExists(tempDir.resolve("PLAN.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that constructor throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorThrowsOnNullScope()
  {
    new GetNextIssueOutput(null);
  }

  /**
   * Verifies that getNextIssueBox throws for null completedIssue.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*completedIssue.*")
  public void getNextIssueBoxThrowsOnNullCompletedIssue() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox(null, "main", "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for blank completedIssue.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*completedIssue.*")
  public void getNextIssueBoxThrowsOnBlankCompletedIssue() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("", "main", "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for null targetBranch.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void getNextIssueBoxThrowsOnNullTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("2.1-test", null, "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for blank targetBranch.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void getNextIssueBoxThrowsOnBlankTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("2.1-test", "", "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for null sessionId.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void getNextIssueBoxThrowsOnNullSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("2.1-test", "main", null, "/tmp", "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for blank sessionId.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void getNextIssueBoxThrowsOnBlankSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("2.1-test", "main", "", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for null projectPath.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*projectPath.*")
  public void getNextIssueBoxThrowsOnNullProjectDir() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("2.1-test", "main", "session123", null, "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for blank projectPath.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*projectPath.*")
  public void getNextIssueBoxThrowsOnBlankProjectDir() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("2.1-test", "main", "session123", "", "");
    }
  }

  /**
   * Verifies that getNextIssueBox throws for null excludePattern.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*excludePattern.*")
  public void getNextIssueBoxThrowsOnNullExcludePattern() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getNextIssueBox("2.1-test", "main", "session123", "/tmp", null);
    }
  }

  /**
   * Verifies that getNextIssueBox accepts empty excludePattern and returns output with box structure.
   */
  @Test
  public void getNextIssueBoxAcceptsEmptyExcludePattern() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      String result = output.getNextIssueBox("2.1-test", "main", "session123", "/tmp", "");
      requireThat(result, "result").isNotEmpty();
    }
  }

  /**
   * Verifies that getOutput parses CLI-style arguments correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputParsesArguments() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      String[] args = {
        "--completed-issue", "2.1-test-issue",
        "--target-branch", "v2.1",
        "--session-id", "test-session-123",
        "--project-dir", "/tmp"
      };
      String result = output.getOutput(args);
      requireThat(result, "result").isNotEmpty();
    }
  }

  /**
   * Verifies that getOutput throws for missing required arguments.
   *
   * @throws IOException expected for missing arguments
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*GetNextIssueOutput.getOutput\\(\\) requires.*")
  public void getOutputThrowsForMissingArguments() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getOutput(new String[]{"--completed-issue", "2.1-test"});
    }
  }

  /**
   * Verifies that TestJvmScope rejects a blank session ID, matching the production contract
   * where CLAUDE_SESSION_ID must be set.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = ".*must not be blank.*")
  public void getOutputThrowsWhenSessionIdMissing() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project-session");
    Path pluginRoot = Files.createTempDirectory("test-plugin-session");
    Path envFile = Files.createTempFile("test-env", ".sh");
    try
    {
      // Constructing with blank sessionId must throw, matching MainJvmScope behavior
      new TestJvmScope(projectPath, pluginRoot, "", envFile, TerminalType.WINDOWS_TERMINAL);
    }
    finally
    {
      Files.deleteIfExists(envFile);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that getOutput preserves JSON-like argument values containing curly braces.
   * <p>
   * This is the primary regression test for the zsh JSON quoting bug where arguments containing
   * {@code {}} were silently dropped or corrupted before being passed to the Java layer.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputPreservesJsonArgWithCurlyBraces() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      String jsonValue = "{\"key\":\"value\"}";
      String[] args = {
        "--completed-issue", "2.1-test-issue",
        "--target-branch", "v2.1",
        "--session-id", "test-session-123",
        "--project-dir", "/tmp",
        "--exclude-pattern", jsonValue
      };
      String result = output.getOutput(args);
      requireThat(result, "result").isNotEmpty();
    }
  }

  /**
   * Verifies that getOutput throws NullPointerException for null args.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void getOutputThrowsOnNullArgs() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      output.getOutput(null);
    }
  }

  /**
   * Verifies that getOutput falls back to scope session ID and project dir when those arguments are omitted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputFallsBackToScopeWhenSessionIdAndProjectDirOmitted() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      // Omit --session-id and --project-dir; scope provides defaults
      String[] args = {
        "--completed-issue", "2.1-test-issue",
        "--target-branch", "v2.1"
      };
      String result = output.getOutput(args);
      requireThat(result, "result").isNotEmpty();
    }
  }

  /**
   * Verifies that getNextIssueBox excludes the completed issue from next-issue discovery.
   * <p>
   * When only the completed issue exists in the project, discovery must not return it as the
   * next issue.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getNextIssueBoxExcludesCompletedIssue() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-next-issue-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      // Only the completed issue exists; no other open issues
      createIssueWithState(projectPath, "2", "1", "done-feature", "open");

      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      // Use a valid UUID-format session ID so IssueLock.validateSessionId passes
      String result = output.getNextIssueBox("2.1-done-feature", "v2.1",
        "00000000-0000-0000-0000-000000000001", projectPath.toString(), "");

      // Must show scope-complete since no other issues remain after excluding the completed one
      requireThat(result, "result").contains("Scope Complete");
      // Must NOT suggest the completed issue as the next issue to work on
      requireThat(result, "result").doesNotContain("**Next:** 2.1-done-feature");
      // Must have box structure intact
      requireThat(result, "result").contains("╰");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that getNextIssueBox finds a different open issue when the completed one is excluded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getNextIssueBoxSkipsCompletedAndFindsOtherIssue() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-next-issue-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      createIssueWithState(projectPath, "2", "1", "done-feature", "open");
      createIssueWithState(projectPath, "2", "1", "pending-feature", "open");

      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      // Use a valid UUID-format session ID so IssueLock.validateSessionId passes
      String result = output.getNextIssueBox("2.1-done-feature", "v2.1",
        "00000000-0000-0000-0000-000000000002", projectPath.toString(), "");

      // Must suggest the other issue as next
      requireThat(result, "result").contains("2.1-pending-feature");
      // Must NOT suggest the completed issue as the next issue to work on
      requireThat(result, "result").doesNotContain("**Next:** 2.1-done-feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that getNextIssueBox combines both the completed-issue bare name and the external
   * excludePattern when both are non-empty.
   * <p>
   * With three issues present and the completed issue plus one excluded by external pattern, only
   * the remaining issue should appear as the next suggestion.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getNextIssueBoxCombinesCompletedAndExternalExcludePatterns() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-next-issue-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      createIssueWithState(projectPath, "2", "1", "done-feature", "open");
      createIssueWithState(projectPath, "2", "1", "skip-feature", "open");
      createIssueWithState(projectPath, "2", "1", "next-feature", "open");

      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      // External excludePattern excludes "2.1-skip-feature"; completed issue is "2.1-done-feature"
      // Both should be excluded, leaving only "next-feature" as next
      String result = output.getNextIssueBox("2.1-done-feature", "v2.1",
        "00000000-0000-0000-0000-000000000003", projectPath.toString(), "2.1-skip-feature");

      // Must NOT suggest either excluded issue
      requireThat(result, "result").doesNotContain("**Next:** 2.1-done-feature");
      requireThat(result, "result").doesNotContain("**Next:** 2.1-skip-feature");
      // Must suggest the remaining issue
      requireThat(result, "result").contains("2.1-next-feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that getNextIssueBox handles an issue ID with no dash gracefully.
   * <p>
   * When the completed issue ID contains no dash, it is used as the exclude pattern unchanged.
   * With an empty project, the method should return a scope-complete box without throwing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getNextIssueBoxHandlesIssueIdWithNoDash() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-next-issue-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      GetNextIssueOutput output = new GetNextIssueOutput(scope);
      // "nodash" has no '-' so extractBareName returns "nodash" as the exclude pattern
      String result = output.getNextIssueBox("nodash", "v2.1",
        "00000000-0000-0000-0000-000000000004", projectPath.toString(), "");

      // Must not throw; must return non-empty output (scope-complete)
      requireThat(result, "result").isNotNull().isNotEmpty();
      requireThat(result, "result").contains("Scope Complete");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  // -----------------------------------------------------------------------------------------
  // Setup helpers
  // -----------------------------------------------------------------------------------------

  /**
   * Creates an issue directory with STATE.md.
   *
   * @param projectPath the project root
   * @param major      the major version string
   * @param minor      the minor version string
   * @param issueName  the bare issue name
   * @param status     the issue status (e.g., "open")
   * @throws IOException if file creation fails
   */
  private void createIssueWithState(Path projectPath, String major, String minor, String issueName,
    String status) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String stateContent = """
      # State

      - **Status:** %s
      - **Progress:** 0%%
      - **Dependencies:** []
      - **Blocks:** []
      """.formatted(status);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
  }
}
