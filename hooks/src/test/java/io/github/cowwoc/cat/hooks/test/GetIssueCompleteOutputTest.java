package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetIssueCompleteOutput;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetIssueCompleteOutput functionality.
 * <p>
 * Tests verify that issue complete and scope complete boxes are
 * rendered correctly with proper headers, borders, and content.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetIssueCompleteOutputTest
{
  /**
   * Verifies that getIssueCompleteBox returns output containing the issue name and box structure.
   */
  @Test
  public void getIssueCompleteBoxContainsIssueNameAndBoxStructure() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Implement feature X", "main");
      requireThat(result, "result").contains("2.1-test").contains("Issue Complete");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains header.
   */
  @Test
  public void getIssueCompleteBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Implement feature X", "main");
      requireThat(result, "result").contains("✓").contains("Issue Complete");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains completed issue name.
   */
  @Test
  public void getIssueCompleteBoxContainsCompletedIssue() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-add-login", "2.1-next", "Implement feature X", "main");
      requireThat(result, "result").contains("2.1-add-login");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains next issue.
   */
  @Test
  public void getIssueCompleteBoxContainsNextIssue() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-add-validation", "Implement feature X", "main");
      requireThat(result, "result").contains("Next:").contains("2.1-add-validation");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains next goal.
   */
  @Test
  public void getIssueCompleteBoxContainsNextGoal() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Add user authentication", "main");
      requireThat(result, "result").contains("Add user authentication");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains base branch.
   */
  @Test
  public void getIssueCompleteBoxContainsBaseBranch() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Goal text", "v2.1");
      requireThat(result, "result").contains("merged to v2.1");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains continuation instructions.
   */
  @Test
  public void getIssueCompleteBoxContainsContinuationInstructions() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Goal", "main");
      requireThat(result, "result").contains("Continuing").contains("stop").contains("abort");
    }
  }

  /**
   * Verifies that getIssueCompleteBox has box structure.
   */
  @Test
  public void getIssueCompleteBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Goal", "main");
      String[] lines = result.split("\n");
      requireThat(lines[0], "firstLine").startsWith("╭");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
    }
  }

  /**
   * Verifies that getScopeCompleteBox returns output containing the scope name and box structure.
   */
  @Test
  public void getScopeCompleteBoxContainsScopeNameAndBoxStructure() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getScopeCompleteBox("v2.1");
      requireThat(result, "result").contains("v2.1").contains("Scope Complete");
    }
  }

  /**
   * Verifies that getScopeCompleteBox output contains header.
   */
  @Test
  public void getScopeCompleteBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getScopeCompleteBox("v2.1");
      requireThat(result, "result").contains("✓").contains("Scope Complete");
    }
  }

  /**
   * Verifies that getScopeCompleteBox output contains scope name.
   */
  @Test
  public void getScopeCompleteBoxContainsScopeName() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getScopeCompleteBox("v3.0");
      requireThat(result, "result").contains("v3.0").contains("all issues complete");
    }
  }

  /**
   * Verifies that getScopeCompleteBox has box structure.
   */
  @Test
  public void getScopeCompleteBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getScopeCompleteBox("v2.1");
      String[] lines = result.split("\n");
      requireThat(lines[0], "firstLine").startsWith("╭");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for null issueName.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getIssueCompleteBoxThrowsOnNullIssueName() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox(null, "2.1-next", "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for blank issueName.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getIssueCompleteBoxThrowsOnBlankIssueName() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("", "2.1-next", "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for null nextIssue.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getIssueCompleteBoxThrowsOnNullNextIssue() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", null, "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for blank nextIssue.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getIssueCompleteBoxThrowsOnBlankNextIssue() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", "", "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for null nextGoal.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getIssueCompleteBoxThrowsOnNullNextGoal() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", "2.1-next", null, "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for blank nextGoal.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getIssueCompleteBoxThrowsOnBlankNextGoal() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", "2.1-next", "", "main");
    }
  }

  /**
   * Verifies that getScopeCompleteBox throws for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getScopeCompleteBoxThrowsOnNullScope() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getScopeCompleteBox(null);
    }
  }

  /**
   * Verifies that getScopeCompleteBox throws for blank scope.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getScopeCompleteBoxThrowsOnBlankScope() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getScopeCompleteBox("");
    }
  }

  /**
   * Verifies that constructor throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorThrowsOnNullScope()
  {
    new GetIssueCompleteOutput(null);
  }
}
