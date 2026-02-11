package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.GetHelpOutput;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetHelpOutput functionality.
 * <p>
 * Tests verify that help output generation produces correctly formatted
 * displays with proper workflow diagram and command hierarchy.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetHelpOutputTest
{
  /**
   * Verifies that getOutput returns output containing CAT commands and workflow information.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputContainsCatCommandsAndWorkflow() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("CAT").contains("Commands").contains("Workflow");
  }

  /**
   * Verifies that help output contains workflow diagram.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsWorkflowDiagram() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("Workflow");
  }

  /**
   * Verifies that help output contains add command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsAddCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:add");
  }

  /**
   * Verifies that help output contains work command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsWorkCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:work");
  }

  /**
   * Verifies that help output contains status command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsStatusCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:status");
  }

  /**
   * Verifies that help output contains cleanup command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsCleanupCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:cleanup");
  }

  /**
   * Verifies that help output contains config command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsConfigCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:config");
  }

  /**
   * Verifies that help output contains reference to CAT commands.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsHelpCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("CAT");
  }

  /**
   * Verifies that help output contains workflow steps.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsWorkflowSteps() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("Create");
  }

  /**
   * Verifies that help output has proper markdown structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputHasBoxStructure() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("#").contains("**");
  }

  /**
   * Verifies that help output contains command hierarchy.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsCommandHierarchy() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("Commands");
  }

  /**
   * Verifies that help output contains multiple command groups.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsMultipleCommandGroups() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    int addCount = result.split("/cat:add", -1).length - 1;
    int workCount = result.split("/cat:work", -1).length - 1;
    int statusCount = result.split("/cat:status", -1).length - 1;

    requireThat(addCount, "addCount").isGreaterThan(0);
    requireThat(workCount, "workCount").isGreaterThan(0);
    requireThat(statusCount, "statusCount").isGreaterThan(0);
  }

  /**
   * Verifies that help output contains trust levels documentation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsTrustLevels() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("Low").contains("Medium").contains("High");
  }

  /**
   * Verifies that help output contains /cat:init command reference.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsInitCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:init");
  }

  /**
   * Verifies that help output contains .claude/cat directory structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsDirectoryStructure() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains(".claude/cat/");
  }

  /**
   * Verifies that help output contains configuration options.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsConfigOptions() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("trust").contains("verify").contains("curiosity");
  }

  /**
   * Verifies that help output contains advanced commands section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsAdvancedCommands() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("Advanced Commands");
  }

  /**
   * Verifies that help output contains hierarchy file references.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsHierarchyFiles() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("STATE.md").contains("PLAN.md");
  }

  /**
   * Verifies that help output contains PROJECT.md reference in hierarchy tree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsProjectMd() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("PROJECT.md");
  }

  /**
   * Verifies that help output contains ROADMAP.md reference in hierarchy tree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsRoadmapMd() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("ROADMAP.md");
  }

  /**
   * Verifies that help output contains cat-config.json reference in hierarchy tree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsCatConfigJson() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("cat-config.json");
  }

  /**
   * Verifies that help output contains code block formatting for diagrams.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsCodeBlocks() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("```");
  }

  /**
   * Verifies that help output is static (same result regardless of invocation).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputIsStatic() throws IOException
  {
    GetHelpOutput handler1 = new GetHelpOutput();
    GetHelpOutput handler2 = new GetHelpOutput();
    String result1 = handler1.getOutput();
    String result2 = handler2.getOutput();
    requireThat(result1, "result1").isEqualTo(result2);
  }

  /**
   * Verifies that help output contains research command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsResearchCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:research");
  }

  /**
   * Verifies that help output contains spawn-subagent command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsSpawnSubagentCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:spawn-subagent");
  }

  /**
   * Verifies that help output contains token-report command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsTokenReportCommand() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("/cat:token-report");
  }

  /**
   * Verifies that help output contains the patience configuration option.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpOutputContainsPatienceConfig() throws IOException
  {
    GetHelpOutput handler = new GetHelpOutput();
    String result = handler.getOutput();
    requireThat(result, "result").contains("patience");
  }
}
