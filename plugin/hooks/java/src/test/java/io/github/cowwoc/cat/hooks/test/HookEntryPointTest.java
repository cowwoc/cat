package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.GetBashPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetBashPretoolOutput;
import io.github.cowwoc.cat.hooks.GetPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetReadPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetReadPretoolOutput;
import io.github.cowwoc.cat.hooks.GetSkillOutput;
import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for hook entry points.
 * <p>
 * These tests verify that each entry point correctly parses JSON input
 * and produces the expected JSON output.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class HookEntryPointTest
{
  /**
   * Creates a HookInput from a JSON string.
   *
   * @param json the JSON input string
   * @return the parsed HookInput
   */
  private HookInput createInput(String json)
  {
    return HookInput.readFrom(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Creates a HookOutput that captures output to a stream.
   *
   * @param capture the stream to capture output into
   * @return a HookOutput writing to the capture stream
   */
  private HookOutput createOutput(ByteArrayOutputStream capture)
  {
    return new HookOutput(new PrintStream(capture, true, StandardCharsets.UTF_8));
  }

  // --- GetSkillOutput tests ---

  /**
   * Verifies that GetSkillOutput returns empty JSON when given empty input.
   */
  @Test
  public void getSkillOutputReturnsEmptyJsonForEmptyInput()
  {
    HookInput input = createInput("{}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetSkillOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetSkillOutput returns empty JSON when no message is present.
   */
  @Test
  public void getSkillOutputReturnsEmptyJsonWhenNoMessage()
  {
    HookInput input = createInput("{\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetSkillOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetBashPretoolOutput tests ---

  /**
   * Verifies that GetBashPretoolOutput returns empty JSON for non-Bash tools.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonForNonBashTool()
  {
    HookInput input = createInput("{\"tool_name\": \"Read\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetBashPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetBashPretoolOutput returns empty JSON when Bash tool has no command.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWhenNoCommand()
  {
    HookInput input = createInput("{\"tool_name\": \"Bash\", \"tool_input\": {}}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetBashPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetBashPretoolOutput returns empty JSON for Bash tool with command.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWithCommand()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"ls -la\"}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetBashPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetBashPosttoolOutput tests ---

  /**
   * Verifies that GetBashPosttoolOutput returns empty JSON for non-Bash tools.
   */
  @Test
  public void getBashPosttoolReturnsEmptyJsonForNonBashTool()
  {
    HookInput input = createInput("{\"tool_name\": \"Read\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetBashPosttoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetReadPretoolOutput tests ---

  /**
   * Verifies that GetReadPretoolOutput returns empty JSON for Read tool.
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForReadTool()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Read\", \"tool_input\": {\"file_path\": \"/tmp/test\"}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetReadPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetReadPretoolOutput returns empty JSON for unsupported tools.
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForUnsupportedTool()
  {
    HookInput input = createInput("{\"tool_name\": \"Bash\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetReadPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetReadPosttoolOutput tests ---

  /**
   * Verifies that GetReadPosttoolOutput returns empty JSON for Grep tool.
   */
  @Test
  public void getReadPosttoolReturnsEmptyJsonForGrepTool()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Grep\", \"tool_input\": {}, \"tool_result\": {}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetReadPosttoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetPosttoolOutput tests ---

  /**
   * Verifies that GetPosttoolOutput returns empty JSON when given empty input.
   */
  @Test
  public void getPosttoolReturnsEmptyJsonForEmptyInput()
  {
    HookInput input = createInput("{}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetPosttoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetPosttoolOutput returns empty JSON with a tool name present.
   */
  @Test
  public void getPosttoolReturnsEmptyJsonWithToolName()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Write\", \"tool_result\": {}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetPosttoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- HookInput error path tests ---

  /**
   * Verifies that HookInput.readFrom with malformed JSON returns empty HookInput.
   */
  @Test
  public void hookInputWithMalformedJsonReturnsEmpty()
  {
    HookInput input = createInput("not valid json {{{");
    requireThat(input.isEmpty(), "isEmpty").isTrue();
  }

  /**
   * Verifies that HookInput.readFrom with blank input returns empty HookInput.
   */
  @Test
  public void hookInputWithBlankInputReturnsEmpty()
  {
    HookInput input = createInput("   ");
    requireThat(input.isEmpty(), "isEmpty").isTrue();
  }

  /**
   * Verifies that HookInput.readFrom with empty string returns empty HookInput.
   */
  @Test
  public void hookInputWithEmptyStringReturnsEmpty()
  {
    HookInput input = createInput("");
    requireThat(input.isEmpty(), "isEmpty").isTrue();
  }

  /**
   * Verifies that HookInput.getString with non-string value throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void hookInputGetStringWithNonStringValueThrows()
  {
    HookInput input = createInput("{\"count\": 42}");
    input.getString("count");
  }

  /**
   * Verifies that HookInput.getString returns empty string for missing key.
   */
  @Test
  public void hookInputGetStringReturnEmptyForMissingKey()
  {
    HookInput input = createInput("{\"key\": \"value\"}");
    String result = input.getString("nonexistent");
    requireThat(result, "result").isEqualTo("");
  }

  /**
   * Verifies that HookInput.readFrom with null stream throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void hookInputReadFromNullStreamThrows()
  {
    HookInput.readFrom(null);
  }

  /**
   * Verifies that HookInput.empty returns an empty input.
   */
  @Test
  public void hookInputEmptyReturnsEmptyInput()
  {
    HookInput input = HookInput.empty();
    requireThat(input.isEmpty(), "isEmpty").isTrue();
  }

  // --- HookOutput error path tests ---

  /**
   * Verifies that HookOutput.block with blank reason throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void hookOutputBlockWithBlankReasonThrows()
  {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);
    output.block("   ");
  }

  /**
   * Verifies that HookOutput.block with null reason throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void hookOutputBlockWithNullReasonThrows()
  {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);
    output.block(null);
  }

  /**
   * Verifies that HookOutput constructor with null stream throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void hookOutputWithNullStreamThrows()
  {
    new HookOutput(null);
  }

  /**
   * Verifies that HookOutput.warn with blank warning throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void hookOutputWarnWithBlankWarningThrows()
  {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);
    output.warn("");
  }

  /**
   * Verifies that HookOutput.wrapSystemReminder with blank content throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void hookOutputWrapSystemReminderWithBlankContentThrows()
  {
    HookOutput.wrapSystemReminder("   ");
  }

  /**
   * Verifies that HookOutput.additionalContext with blank hookEventName throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void hookOutputAdditionalContextWithBlankEventNameThrows()
  {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);
    output.additionalContext("", "some context");
  }

  /**
   * Verifies that HookOutput.block produces valid JSON with decision field.
   */
  @Test
  public void hookOutputBlockProducesValidJson()
  {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);
    output.block("test reason");

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").contains("\"decision\"").contains("\"block\"").contains("\"test reason\"");
  }

  /**
   * Verifies that HookOutput.empty produces empty JSON object.
   */
  @Test
  public void hookOutputEmptyProducesEmptyJson()
  {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);
    output.empty();

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }
}
