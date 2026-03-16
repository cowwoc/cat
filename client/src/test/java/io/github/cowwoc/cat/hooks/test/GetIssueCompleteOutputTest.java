/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetIssueCompleteOutput;
import io.github.cowwoc.cat.hooks.util.IssueGoalReader;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetIssueCompleteOutput functionality.
 * <p>
 * Tests verify that issue complete and scope complete boxes are rendered correctly with proper
 * headers, borders, and content. Tests also verify the 2-arg getOutput() routing with internal
 * IssueDiscovery, the discoverAndRender() method, and the readGoalFromPlan() method.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class GetIssueCompleteOutputTest
{
  /**
   * Verifies that getIssueCompleteBox returns output containing the issue name and box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxContainsIssueNameAndBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Implement feature X", "main");
      requireThat(result, "result").contains("2.1-test").contains("Issue Complete");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Implement feature X", "main");
      requireThat(result, "result").contains("✓").contains("Issue Complete");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains completed issue name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxContainsCompletedIssue() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-add-login", "2.1-next", "Implement feature X",
        "main");
      requireThat(result, "result").contains("2.1-add-login");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains next issue using bold markdown format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxContainsNextIssue() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-add-validation",
        "Implement feature X", "main");
      requireThat(result, "result").contains("**Next:**").contains("2.1-add-validation");
    }
  }

  /**
   * Verifies that the issue-complete box uses bold markdown format for the "Next:" label,
   * enabling auto-continuation detection by the work skill.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxUsesMarkdownBoldNextLabel() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Some goal", "main");
      requireThat(result, "result").contains("**Next:**");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains next goal.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxContainsNextGoal() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Add user authentication",
        "main");
      requireThat(result, "result").contains("Add user authentication");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains target branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxContainsTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Goal text", "v2.1");
      requireThat(result, "result").contains("merged to v2.1");
    }
  }

  /**
   * Verifies that getIssueCompleteBox output contains continuation instructions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxContainsContinuationInstructions() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getIssueCompleteBox("2.1-test", "2.1-next", "Goal", "main");
      requireThat(result, "result").contains("Continuing").contains("stop").contains("abort");
    }
  }

  /**
   * Verifies that getIssueCompleteBox has box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
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
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteBoxContainsScopeNameAndBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getScopeCompleteBox("v2.1");
      requireThat(result, "result").contains("v2.1").contains("Scope Complete");
    }
  }

  /**
   * Verifies that getScopeCompleteBox output contains header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getScopeCompleteBox("v2.1");
      requireThat(result, "result").contains("✓").contains("Scope Complete");
    }
  }

  /**
   * Verifies that getScopeCompleteBox output contains scope name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteBoxContainsScopeName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getScopeCompleteBox("v3.0");
      requireThat(result, "result").contains("v3.0").contains("all issues complete");
    }
  }

  /**
   * Verifies that getScopeCompleteBox has box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
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
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*issueName.*")
  public void getIssueCompleteBoxThrowsOnNullIssueName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox(null, "2.1-next", "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for blank issueName.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*issueName.*")
  public void getIssueCompleteBoxThrowsOnBlankIssueName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("", "2.1-next", "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for null nextIssue.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*nextIssue.*")
  public void getIssueCompleteBoxThrowsOnNullNextIssue() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", null, "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for blank nextIssue.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*nextIssue.*")
  public void getIssueCompleteBoxThrowsOnBlankNextIssue() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", "", "Goal", "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for null nextGoal.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*nextGoal.*")
  public void getIssueCompleteBoxThrowsOnNullNextGoal() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", "2.1-next", null, "main");
    }
  }

  /**
   * Verifies that getIssueCompleteBox throws for blank nextGoal.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*nextGoal.*")
  public void getIssueCompleteBoxThrowsOnBlankNextGoal() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getIssueCompleteBox("2.1-test", "2.1-next", "", "main");
    }
  }

  /**
   * Verifies that getScopeCompleteBox throws for null scope.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scopeName.*")
  public void getScopeCompleteBoxThrowsOnNullScope() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getScopeCompleteBox(null);
    }
  }

  /**
   * Verifies that getScopeCompleteBox throws for blank scope.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*scopeName.*")
  public void getScopeCompleteBoxThrowsOnBlankScope() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getScopeCompleteBox("");
    }
  }

  /**
   * Verifies that constructor throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorThrowsOnNullScope()
  {
    new GetIssueCompleteOutput(null);
  }

  // -----------------------------------------------------------------------------------------
  // getOutput() routing tests
  // -----------------------------------------------------------------------------------------

  /**
   * Verifies that getOutput with 0 args returns empty string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputZeroArgsReturnEmpty() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getOutput(new String[]{});
      requireThat(result, "result").isEmpty();
    }
  }

  /**
   * Verifies that getOutput with 1 arg routes to getScopeCompleteBox.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputSingleArgRoutesToScopeComplete() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getOutput(new String[]{"v2.1"});
      requireThat(result, "result").contains("Scope Complete");
      requireThat(result, "result").contains("v2.1");
    }
  }

  /**
   * Verifies that getOutput with 3 or more args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Expected 1 or 2 arguments.*")
  public void getOutputThreeArgsThrowsIllegalArgument() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      output.getOutput(new String[]{"a", "b", "c"});
    }
  }

  /**
   * Verifies that getOutput with 2 args discovers next issue and renders issue-complete box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputTwoArgDiscoveriesNextIssue() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-issue-complete-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      createIssueWithPlan(projectPath, "2", "1", "next-feature", "open",
        "## Goal\n\nImplement the next feature.\n");

      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getOutput(new String[]{"2.1-current-issue", "v2.1"});

      requireThat(result, "result").contains("2.1-next-feature");
      requireThat(result, "result").contains("Implement the next feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that getOutput with 2 args renders scope-complete when no next issue is found.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputTwoArgNoNextIssueRendersScope() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-issue-complete-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);
      String result = output.getOutput(new String[]{"2.1-current-issue", "v2.1"});

      requireThat(result, "result").contains("Scope Complete");
      requireThat(result, "result").contains("v2.1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  // -----------------------------------------------------------------------------------------
  // discoverAndRender() tests
  // -----------------------------------------------------------------------------------------

  /**
   * Verifies that discoverAndRender throws NullPointerException for null issueName.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*issueName.*")
  public void discoverAndRenderNullIssueNameThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      new GetIssueCompleteOutput(scope).discoverAndRender(null, "main");
    }
  }

  /**
   * Verifies that discoverAndRender throws IllegalArgumentException for blank issueName.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*issueName.*")
  public void discoverAndRenderBlankIssueNameThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      new GetIssueCompleteOutput(scope).discoverAndRender("", "main");
    }
  }

  /**
   * Verifies that discoverAndRender throws NullPointerException for null targetBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void discoverAndRenderNullTargetBranchThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      new GetIssueCompleteOutput(scope).discoverAndRender("2.1-fix-bug", null);
    }
  }

  /**
   * Verifies that discoverAndRender throws IllegalArgumentException for blank targetBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void discoverAndRenderBlankTargetBranchThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      new GetIssueCompleteOutput(scope).discoverAndRender("2.1-fix-bug", "");
    }
  }

  /**
   * Verifies that discoverAndRender with an issueName containing no dash falls back to scope-complete.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void discoverAndRenderIssueNameNoDashFallsBackToScope() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      String result = new GetIssueCompleteOutput(scope).discoverAndRender("nodash", "main");
      requireThat(result, "result").contains("Scope Complete");
    }
  }

  /**
   * Verifies that discoverAndRender with no open issues renders the scope-complete box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void discoverAndRenderNotFoundRendersScope() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-issue-complete-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      String result = new GetIssueCompleteOutput(scope).discoverAndRender("2.1-done-issue", "v2.1");
      requireThat(result, "result").contains("Scope Complete");
      requireThat(result, "result").contains("v2.1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies the successful path of discoverAndRender: with an open next issue, the issue-complete
   * box contains the next issue name, goal, and target branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void discoverAndRenderSuccessPathRendersIssueComplete() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-issue-complete-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      createIssueWithPlan(projectPath, "2", "1", "next-issue", "open",
        "## Goal\n\nBuild the API layer.\n");

      String result = new GetIssueCompleteOutput(scope).discoverAndRender("2.1-previous-issue",
        "v2.1");

      requireThat(result, "result").contains("Issue Complete");
      requireThat(result, "result").contains("2.1-next-issue");
      requireThat(result, "result").contains("Build the API layer");
      requireThat(result, "result").contains("v2.1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that discoverAndRender excludes the completed issue from next-issue discovery.
   * <p>
   * When only the completed issue exists in the version scope, discovery must return scope-complete
   * rather than re-suggesting the same issue as "next".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void discoverAndRenderExcludesCompletedIssue() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-issue-complete-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      // Only the completed issue exists; no other open issues in this version
      createIssueWithPlan(projectPath, "2", "1", "completed-feature", "open",
        "## Goal\n\nAlready done.\n");

      String result = new GetIssueCompleteOutput(scope).discoverAndRender("2.1-completed-feature",
        "v2.1");

      // Must show scope-complete since no other issues remain after excluding the completed one
      requireThat(result, "result").contains("Scope Complete");
      // Must NOT suggest the completed issue as the next issue to work on
      requireThat(result, "result").doesNotContain("**Next:** 2.1-completed-feature");
      // Must have box structure intact
      requireThat(result, "result").contains("╰");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that discoverAndRender finds a different open issue when the completed one is excluded.
   * <p>
   * When both the completed issue and another open issue exist, the next issue should be the other
   * issue, not the completed one.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void discoverAndRenderSkipsCompletedAndFindsOtherIssue() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-issue-complete-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      createIssueWithPlan(projectPath, "2", "1", "done-feature", "open",
        "## Goal\n\nAlready merged.\n");
      createIssueWithPlan(projectPath, "2", "1", "pending-feature", "open",
        "## Goal\n\nNext thing to build.\n");

      String result = new GetIssueCompleteOutput(scope).discoverAndRender("2.1-done-feature", "v2.1");

      // Must show the other open issue as next
      requireThat(result, "result").contains("2.1-pending-feature");
      requireThat(result, "result").contains("Next thing to build");
      // Must NOT show the completed issue as the "Next:" recommendation
      // (it may still appear in the "merged to" line, which is expected)
      requireThat(result, "result").contains("**Next:** 2.1-pending-feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that discoverAndRender correctly handles a patch-version issue ID.
   * <p>
   * For an issue ID like {@code 2.1.3-completed-feature}, the version is extracted as {@code 2.1.3}
   * and the exclude pattern is set to {@code completed-feature}. With only the completed issue
   * present, the result must be scope-complete.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void discoverAndRenderExcludesCompletedIssueWithPatchVersion() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("get-issue-complete-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      // Create a patch-version issue under .cat/issues/v2/v2.1/v2.1.3/completed-feature
      createIssueWithPlan(projectPath, "2", "1", "3", "completed-feature", "open",
        "## Goal\n\nAlready done.\n");

      String result = new GetIssueCompleteOutput(scope).discoverAndRender("2.1.3-completed-feature",
        "v2.1.3");

      // Must show scope-complete since no other issues remain after excluding the completed one
      requireThat(result, "result").contains("Scope Complete");
      // Must NOT suggest the completed issue as the next issue to work on
      requireThat(result, "result").doesNotContain("**Next:** 2.1.3-completed-feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  // -----------------------------------------------------------------------------------------
  // readGoalFromPlan() tests
  // -----------------------------------------------------------------------------------------

  /**
   * Verifies that readGoalFromPlan returns the goal text from a valid PLAN.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readGoalFromPlanValidPlan() throws IOException
  {
    Path planFile = Files.createTempFile("PLAN", ".md");
    try
    {
      Files.writeString(planFile,
        "# Plan\n\n## Goal\n\nFix the bug in the parser.\n\n## Steps\n\n- Step 1\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("Fix the bug in the parser.");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that readGoalFromPlan returns "No goal found" when the file does not exist.
   */
  @Test
  public void readGoalFromPlanMissingFile()
  {
    Path nonExistent = Path.of("/tmp/nonexistent-plan-" + System.nanoTime() + ".md");
    String goal = IssueGoalReader.readGoalFromPlan(nonExistent);
    requireThat(goal, "goal").isEqualTo("No goal found");
  }

  /**
   * Verifies that readGoalFromPlan returns "No goal found" when the ## Goal heading is absent.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readGoalFromPlanMissingGoalHeading() throws IOException
  {
    Path planFile = Files.createTempFile("PLAN", ".md");
    try
    {
      Files.writeString(planFile, "# Plan\n\n## Steps\n\n- Step 1\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("No goal found");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that readGoalFromPlan returns only the first paragraph when the Goal section
   * contains multiple paragraphs.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readGoalFromPlanMultiParagraphGoalReturnsFirst() throws IOException
  {
    Path planFile = Files.createTempFile("PLAN", ".md");
    try
    {
      Files.writeString(planFile,
        "## Goal\n\nFirst paragraph text.\n\nSecond paragraph text.\n\n## Steps\n\n- Step\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("First paragraph text.");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that readGoalFromPlan returns "No goal found" when the Goal section is empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readGoalFromPlanEmptyGoalSection() throws IOException
  {
    Path planFile = Files.createTempFile("PLAN", ".md");
    try
    {
      Files.writeString(planFile, "## Goal\n\n## Steps\n\n- Step\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("No goal found");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  // -----------------------------------------------------------------------------------------
  // Setup helpers
  // -----------------------------------------------------------------------------------------

  /**
   * Creates an issue directory with STATE.md and a PLAN.md with the given content.
   *
   * @param projectPath  the project root
   * @param major       the major version string
   * @param minor       the minor version string
   * @param issueName   the bare issue name
   * @param status      the issue status (e.g., "open")
   * @param planContent the full content to write into PLAN.md
   * @throws IOException if file creation fails
   */
  private void createIssueWithPlan(Path projectPath, String major, String minor, String issueName,
    String status, String planContent) throws IOException
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
    Files.writeString(issueDir.resolve("PLAN.md"), planContent);
  }

  /**
   * Creates a patch-version issue directory with STATE.md and a PLAN.md with the given content.
   *
   * @param projectPath  the project root
   * @param major       the major version string
   * @param minor       the minor version string
   * @param patch       the patch version string
   * @param issueName   the bare issue name
   * @param status      the issue status (e.g., "open")
   * @param planContent the full content to write into PLAN.md
   * @throws IOException if file creation fails
   */
  private void createIssueWithPlan(Path projectPath, String major, String minor, String patch,
    String issueName, String status, String planContent) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).
      resolve("v" + major + "." + minor + "." + patch).resolve(issueName);
    Files.createDirectories(issueDir);

    String stateContent = """
      # State

      - **Status:** %s
      - **Progress:** 0%%
      - **Dependencies:** []
      - **Blocks:** []
      """.formatted(status);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
    Files.writeString(issueDir.resolve("PLAN.md"), planContent);
  }
}
