package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.DefaultJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetNextTaskOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetNextTaskOutput functionality.
 * <p>
 * Tests verify box rendering output structure and business logic for
 * extracting scope and reading issue goals from PLAN.md fixtures.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetNextTaskOutputTest
{
  /**
   * Verifies that extractScope extracts version prefix correctly.
   */
  @Test
  public void extractScopeFromVersionPrefixedIssue() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      String result = output.extractScopePublic("2.1-add-feature");
      requireThat(result, "result").isEqualTo("v2.1");
    }
  }

  /**
   * Verifies that extractScope handles single digit version.
   */
  @Test
  public void extractScopeFromSingleDigitVersion() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      String result = output.extractScopePublic("3-fix-bug");
      requireThat(result, "result").isEqualTo("v3");
    }
  }

  /**
   * Verifies that extractScope returns empty string for empty input.
   */
  @Test
  public void extractScopeReturnsEmptyForEmptyInput() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      String result = output.extractScopePublic("");
      requireThat(result, "result").isEqualTo("");
    }
  }

  /**
   * Verifies that extractScope returns unknown when no dash separator.
   */
  @Test
  public void extractScopeReturnsUnknownForNoDash() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      String result = output.extractScopePublic("noDashHere");
      requireThat(result, "result").isEqualTo("noDashHere");
    }
  }

  /**
   * Verifies that extractScope preserves non-numeric prefix as-is.
   */
  @Test
  public void extractScopePreservesNonNumericPrefix() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      String result = output.extractScopePublic("feature-add-login");
      requireThat(result, "result").isEqualTo("feature");
    }
  }

  /**
   * Verifies that readIssueGoal extracts first paragraph from PLAN.md.
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

      try (JvmScope scope = new DefaultJvmScope())
      {
        GetNextTaskOutput output = new GetNextTaskOutput(scope);
        String goal = output.readIssueGoalPublic(tempDir.toString());
        requireThat(goal, "goal").isEqualTo("This is the first paragraph.");
      }
    }
    finally
    {
      Files.deleteIfExists(tempDir.resolve("PLAN.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that readIssueGoal returns fallback message when no Goal section.
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

      try (JvmScope scope = new DefaultJvmScope())
      {
        GetNextTaskOutput output = new GetNextTaskOutput(scope);
        String goal = output.readIssueGoalPublic(tempDir.toString());
        requireThat(goal, "goal").isEqualTo("No goal found");
      }
    }
    finally
    {
      Files.deleteIfExists(tempDir.resolve("PLAN.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that readIssueGoal returns fallback message when PLAN.md missing.
   */
  @Test
  public void readIssueGoalReturnsNoGoalFoundWhenFileMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-issue");
    try
    {
      try (JvmScope scope = new DefaultJvmScope())
      {
        GetNextTaskOutput output = new GetNextTaskOutput(scope);
        String goal = output.readIssueGoalPublic(tempDir.toString());
        requireThat(goal, "goal").isEqualTo("No goal found");
      }
    }
    finally
    {
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that readIssueGoal handles goal with trailing whitespace.
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

      try (JvmScope scope = new DefaultJvmScope())
      {
        GetNextTaskOutput output = new GetNextTaskOutput(scope);
        String goal = output.readIssueGoalPublic(tempDir.toString());
        requireThat(goal, "goal").isEqualTo("First paragraph with leading/trailing spaces.");
      }
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
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorThrowsOnNullScope()
  {
    new GetNextTaskOutput(null);
  }

  /**
   * Verifies that getNextTaskBox throws for null completedIssue.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getNextTaskBoxThrowsOnNullCompletedIssue() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox(null, "main", "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for blank completedIssue.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getNextTaskBoxThrowsOnBlankCompletedIssue() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("", "main", "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for null baseBranch.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getNextTaskBoxThrowsOnNullBaseBranch() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("2.1-test", null, "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for blank baseBranch.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getNextTaskBoxThrowsOnBlankBaseBranch() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("2.1-test", "", "session123", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for null sessionId.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getNextTaskBoxThrowsOnNullSessionId() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("2.1-test", "main", null, "/tmp", "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for blank sessionId.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getNextTaskBoxThrowsOnBlankSessionId() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("2.1-test", "main", "", "/tmp", "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for null projectDir.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getNextTaskBoxThrowsOnNullProjectDir() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("2.1-test", "main", "session123", null, "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for blank projectDir.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getNextTaskBoxThrowsOnBlankProjectDir() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("2.1-test", "main", "session123", "", "");
    }
  }

  /**
   * Verifies that getNextTaskBox throws for null excludePattern.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getNextTaskBoxThrowsOnNullExcludePattern() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      output.getNextTaskBox("2.1-test", "main", "session123", "/tmp", null);
    }
  }

  /**
   * Verifies that getNextTaskBox accepts empty excludePattern.
   */
  @Test
  public void getNextTaskBoxAcceptsEmptyExcludePattern() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetNextTaskOutput output = new GetNextTaskOutput(scope);
      String result = output.getNextTaskBox("2.1-test", "main", "session123", "/tmp", "");
      requireThat(result, "result").isNotNull();
    }
  }
}
