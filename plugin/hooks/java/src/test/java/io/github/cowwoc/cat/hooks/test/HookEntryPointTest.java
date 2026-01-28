package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.GetBashPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetBashPretoolOutput;
import io.github.cowwoc.cat.hooks.GetPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetReadPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetReadPretoolOutput;
import io.github.cowwoc.cat.hooks.GetSkillOutput;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for hook entry points.
 *
 * <p>These tests verify that each entry point correctly parses JSON input
 * and produces the expected JSON output.</p>
 *
 * <p>Tests are designed for parallel execution - each test is self-contained
 * with no shared state.</p>
 */
public class HookEntryPointTest
{
  /**
   * Runs a hook's main method with simulated stdin and captures stdout.
   *
   * @param mainMethod the main method to run
   * @param stdinContent the content to provide as stdin
   * @return the captured stdout content, trimmed
   * @throws IOException if an I/O error occurs during stream operations
   */
  private String runHook(Runnable mainMethod, String stdinContent) throws IOException
  {
    InputStream originalIn = System.in;
    PrintStream originalOut = System.out;

    try (ByteArrayInputStream testIn = new ByteArrayInputStream(
        stdinContent.getBytes(StandardCharsets.UTF_8));
         ByteArrayOutputStream testOut = new ByteArrayOutputStream())
    {
      System.setIn(testIn);
      System.setOut(new PrintStream(testOut, true, StandardCharsets.UTF_8));

      mainMethod.run();

      return testOut.toString(StandardCharsets.UTF_8).trim();
    }
    finally
    {
      System.setIn(originalIn);
      System.setOut(originalOut);
    }
  }

  // --- GetSkillOutput tests ---

  /**
   * Verifies that GetSkillOutput returns empty JSON when given empty input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getSkillOutputReturnsEmptyJsonForEmptyInput() throws IOException
  {
    String result = runHook(() -> GetSkillOutput.main(new String[]{}), "{}");
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetSkillOutput returns empty JSON when no message is present.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getSkillOutputReturnsEmptyJsonWhenNoMessage() throws IOException
  {
    String result = runHook(
      () -> GetSkillOutput.main(new String[]{}), "{\"session_id\": \"test\"}");
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that extractSkillName correctly parses skill names from various input formats.
   */
  @Test
  public void extractSkillNameParsesVariousFormats()
  {
    requireThat(GetSkillOutput.extractSkillName("/cat:work"), "skillWork").isEqualTo("work");
    requireThat(GetSkillOutput.extractSkillName("cat:status"), "skillStatus").isEqualTo("status");
    requireThat(GetSkillOutput.extractSkillName("/cat:add task"), "skillAddWithArgs").
      isEqualTo("add");
    requireThat(GetSkillOutput.extractSkillName("  /cat:init  "), "skillInitWhitespace").
      isEqualTo("init");
    requireThat(GetSkillOutput.extractSkillName("hello world"), "nonSkillInput").isEmpty();
    requireThat(GetSkillOutput.extractSkillName(""), "emptyInput").isEmpty();
  }

  // --- GetBashPretoolOutput tests ---

  /**
   * Verifies that GetBashPretoolOutput returns empty JSON for non-Bash tools.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonForNonBashTool() throws IOException
  {
    String result = runHook(
      () -> GetBashPretoolOutput.main(new String[]{}), "{\"tool_name\": \"Read\"}");
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetBashPretoolOutput returns empty JSON when Bash tool has no command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWhenNoCommand() throws IOException
  {
    String result = runHook(
      () -> GetBashPretoolOutput.main(new String[]{}),
      "{\"tool_name\": \"Bash\", \"tool_input\": {}}");
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetBashPretoolOutput returns empty JSON for Bash tool with command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWithCommand() throws IOException
  {
    String result = runHook(
      () -> GetBashPretoolOutput.main(new String[]{}),
      "{\"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"ls -la\"}}");
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetBashPosttoolOutput tests ---

  /**
   * Verifies that GetBashPosttoolOutput returns empty JSON for non-Bash tools.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getBashPosttoolReturnsEmptyJsonForNonBashTool() throws IOException
  {
    String result = runHook(
      () -> GetBashPosttoolOutput.main(new String[]{}), "{\"tool_name\": \"Read\"}");
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetReadPretoolOutput tests ---

  /**
   * Verifies that GetReadPretoolOutput returns empty JSON for Read tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForReadTool() throws IOException
  {
    String result = runHook(
      () -> GetReadPretoolOutput.main(new String[]{}),
      "{\"tool_name\": \"Read\", \"tool_input\": {\"file_path\": \"/tmp/test\"}}");
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetReadPretoolOutput returns empty JSON for unsupported tools.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForUnsupportedTool() throws IOException
  {
    String result = runHook(
      () -> GetReadPretoolOutput.main(new String[]{}), "{\"tool_name\": \"Bash\"}");
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetReadPosttoolOutput tests ---

  /**
   * Verifies that GetReadPosttoolOutput returns empty JSON for Grep tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getReadPosttoolReturnsEmptyJsonForGrepTool() throws IOException
  {
    String result = runHook(
      () -> GetReadPosttoolOutput.main(new String[]{}),
      "{\"tool_name\": \"Grep\", \"tool_input\": {}, \"tool_result\": {}}");
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetPosttoolOutput tests ---

  /**
   * Verifies that GetPosttoolOutput returns empty JSON when given empty input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getPosttoolReturnsEmptyJsonForEmptyInput() throws IOException
  {
    String result = runHook(() -> GetPosttoolOutput.main(new String[]{}), "{}");
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetPosttoolOutput returns empty JSON with a tool name present.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getPosttoolReturnsEmptyJsonWithToolName() throws IOException
  {
    String result = runHook(
      () -> GetPosttoolOutput.main(new String[]{}),
      "{\"tool_name\": \"Write\", \"tool_result\": {}}");
    requireThat(result, "result").isEqualTo("{}");
  }
}
