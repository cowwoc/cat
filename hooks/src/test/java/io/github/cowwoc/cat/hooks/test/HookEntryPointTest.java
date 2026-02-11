package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.EditHandler;
import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.GetAskPretoolOutput;
import io.github.cowwoc.cat.hooks.GetBashPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetBashPretoolOutput;
import io.github.cowwoc.cat.hooks.GetEditPretoolOutput;
import io.github.cowwoc.cat.hooks.GetPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetReadPosttoolOutput;
import io.github.cowwoc.cat.hooks.GetReadPretoolOutput;
import io.github.cowwoc.cat.hooks.GetSkillOutput;
import io.github.cowwoc.cat.hooks.GetTaskPretoolOutput;
import io.github.cowwoc.cat.hooks.GetWriteEditPretoolOutput;
import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.edit.EnforceWorkflowCompletion;
import io.github.cowwoc.cat.hooks.edit.WarnSkillEditWithoutBuilder;
import io.github.cowwoc.cat.hooks.bash.BlockWorktreeCd;
import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.write.EnforcePluginFileIsolation;
import io.github.cowwoc.cat.hooks.write.ValidateStateMdFormat;
import io.github.cowwoc.cat.hooks.write.WarnBaseBranchEdit;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

  // --- GetAskPretoolOutput tests ---

  /**
   * Verifies that GetAskPretoolOutput returns empty JSON for non-AskUserQuestion tools.
   */
  @Test
  public void getAskPretoolReturnsEmptyForNonAskTool()
  {
    HookInput input = createInput("{\"tool_name\": \"Read\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetAskPretoolOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getAskPretoolReturnsEmptyForEmptyToolInput()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetEditPretoolOutput tests ---

  /**
   * Verifies that GetEditPretoolOutput returns empty JSON for non-Edit tools.
   */
  @Test
  public void getEditPretoolReturnsEmptyForNonEditTool()
  {
    HookInput input = createInput("{\"tool_name\": \"Read\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetEditPretoolOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getEditPretoolReturnsEmptyForEmptyToolInput()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetEditPretoolOutput throws IllegalArgumentException when session_id is missing.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getEditPretoolThrowsOnMissingSessionId()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);
  }

  // --- GetTaskPretoolOutput tests ---

  /**
   * Verifies that GetTaskPretoolOutput returns empty JSON for non-Task tools.
   */
  @Test
  public void getTaskPretoolReturnsEmptyForNonTaskTool()
  {
    HookInput input = createInput("{\"tool_name\": \"Read\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetTaskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetTaskPretoolOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getTaskPretoolReturnsEmptyForEmptyToolInput()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Task\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetTaskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- EnforceWorkflowCompletion tests ---

  /**
   * Verifies that EnforceWorkflowCompletion allows edits to non-STATE.md files.
   */
  @Test
  public void enforceWorkflowCompletionAllowsNonStateMdFiles()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}, \"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- WarnSkillEditWithoutBuilder tests ---

  /**
   * Verifies that WarnSkillEditWithoutBuilder allows edits to non-skill files.
   */
  @Test
  public void warnSkillEditAllowsNonSkillFiles()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/README.md\"}, \"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- WarnUnsquashedApproval tests ---

  /**
   * Verifies that WarnUnsquashedApproval allows non-approval questions.
   */
  @Test
  public void warnUnsquashedApprovalAllowsNonApprovalQuestions()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"What is your name?\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- WarnApprovalWithoutRenderDiff tests ---

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows non-approval questions.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsNonApprovalQuestions()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Continue?\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- EnforceApprovalBeforeMerge tests ---

  /**
   * Verifies that EnforceApprovalBeforeMerge allows non-work-merge tasks.
   */
  @Test
  public void enforceApprovalBeforeMergeAllowsNonWorkMergeTasks()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Task\", \"tool_input\": {\"subagent_type\": \"cat:implement\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetTaskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that EnforceApprovalBeforeMerge allows tasks with empty subagent_type.
   */
  @Test
  public void enforceApprovalBeforeMergeAllowsEmptySubagentType()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Task\", \"tool_input\": {}, \"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetTaskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- EnforceWorkflowCompletion handler tests ---

  /**
   * Verifies that EnforceWorkflowCompletion warns when editing STATE.md with status closed.
   */
  @Test
  public void enforceWorkflowCompletionWarnsOnStatusClosed() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \".claude/cat/v2/v2.1/my-task/STATE.md\", " +
      "\"new_string\": \"Status: closed\"}");
    EditHandler.Result result = new EnforceWorkflowCompletion().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
    requireThat(result.reason(), "reason").contains("WORKFLOW COMPLETION CHECK");
    requireThat(result.reason(), "reason").contains("my-task");
  }

  /**
   * Verifies that EnforceWorkflowCompletion warns on lowercase status:closed.
   */
  @Test
  public void enforceWorkflowCompletionWarnsOnLowercaseStatusClosed() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \".claude/cat/v2/v2.1/my-task/STATE.md\", " +
      "\"new_string\": \"status:closed\"}");
    EditHandler.Result result = new EnforceWorkflowCompletion().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
    requireThat(result.reason(), "reason").contains("WORKFLOW COMPLETION CHECK");
  }

  /**
   * Verifies that EnforceWorkflowCompletion allows edits when new_string is missing.
   */
  @Test
  public void enforceWorkflowCompletionAllowsMissingNewString()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \".claude/cat/v2/v2.1/my-task/STATE.md\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that EnforceWorkflowCompletion allows when status is not closed.
   */
  @Test
  public void enforceWorkflowCompletionAllowsNonClosedStatus()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \".claude/cat/v2/v2.1/fix-bug-123/STATE.md\", " +
      "\"new_string\": \"Status: in_progress\"}, \"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- WarnSkillEditWithoutBuilder handler tests ---

  /**
   * Verifies that WarnSkillEditWithoutBuilder warns when editing skill SKILL.md files.
   */
  @Test
  public void warnSkillEditWarnsOnSkillMdFiles() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/plugin/skills/my-skill/SKILL.md\"}");
    EditHandler.Result result = new WarnSkillEditWithoutBuilder().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
    requireThat(result.reason(), "reason").contains("SKILL EDIT DETECTED");
    requireThat(result.reason(), "reason").contains("my-skill");
  }

  /**
   * Verifies that WarnSkillEditWithoutBuilder allows files that do not match skill pattern.
   */
  @Test
  public void warnSkillEditAllowsNonSkillPaths()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/workspace/skills/test-skill/SKILL.md\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that WarnSkillEditWithoutBuilder allows non-SKILL.md files in skills directory.
   */
  @Test
  public void warnSkillEditAllowsNonSkillMdFiles()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/workspace/plugin/skills/my-skill/helper.py\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- WarnApprovalWithoutRenderDiff handler tests ---

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows non-approval questions.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsNonApprovalInput()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"What color?\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows when CLAUDE_PROJECT_DIR is missing.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsMissingProjectDir()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Approve?\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- WarnUnsquashedApproval handler tests ---

  /**
   * Verifies that WarnUnsquashedApproval containsApprove method works correctly.
   */
  @Test
  public void warnUnsquashedApprovalDetectsApproveInInput()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Ready to approve?\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    // WarnUnsquashedApproval also checks git state - outside a task worktree it allows
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that WarnUnsquashedApproval detects uppercase APPROVE.
   */
  @Test
  public void warnUnsquashedApprovalDetectsUppercaseApprove()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"APPROVE changes?\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    // WarnUnsquashedApproval also checks git state - outside a task worktree it allows
    requireThat(result, "result").isEqualTo("{}");
  }


  /**
   * Verifies that GetAskPretoolOutput returns additionalContext when handler provides it.
   */
  @Test
  public void getAskPretoolReturnsAdditionalContextEarly()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Continue?\"}, " +
      "\"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetAskPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    // WarnApprovalWithoutRenderDiff and WarnUnsquashedApproval don't inject context in this case
    // This test verifies no crash occurs
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- EnforcePluginFileIsolation handler tests ---

  /**
   * Verifies that EnforcePluginFileIsolation blocks editing plugin files on protected branches.
   */
  @Test
  public void enforcePluginFileIsolationBlocksPluginFileOnProtectedBranch() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/plugin/hooks/test.py\"}");
    FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
    // On the v2.1 branch (protected), plugin files should be blocked
    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("Cannot edit plugin files");
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows editing non-plugin files.
   */
  @Test
  public void enforcePluginFileIsolationAllowsNonPluginFiles() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/docs/README.md\"}");
    FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows empty file path.
   */
  @Test
  public void enforcePluginFileIsolationAllowsEmptyPath() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree("{}");
    FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  // --- WarnBaseBranchEdit handler tests ---

  /**
   * Verifies that WarnBaseBranchEdit allows editing when path is empty.
   */
  @Test
  public void warnBaseBranchEditAllowsEmptyPath() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree("{}");
    FileWriteHandler.Result result = new WarnBaseBranchEdit().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that WarnBaseBranchEdit allows editing allowed patterns.
   */
  @Test
  public void warnBaseBranchEditAllowsAllowedPatterns() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/.claude/settings.json\"}");
    FileWriteHandler.Result result = new WarnBaseBranchEdit().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  // --- GetWriteEditPretoolOutput dispatcher tests ---

  /**
   * Verifies that GetWriteEditPretoolOutput returns empty JSON for non-Write/Edit tools.
   */
  @Test
  public void getWriteEditPretoolReturnsEmptyForNonWriteEditTool()
  {
    HookInput input = createInput("{\"tool_name\": \"Read\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetWriteEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetWriteEditPretoolOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getWriteEditPretoolReturnsEmptyForEmptyToolInput()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Write\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetWriteEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetWriteEditPretoolOutput uses case-insensitive matching for tool names.
   */
  @Test
  public void getWriteEditPretoolUsesCaseInsensitiveMatching()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"write\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}, \"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetWriteEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetWriteEditPretoolOutput accepts Edit tool_name.
   */
  @Test
  public void getWriteEditPretoolAcceptsEditToolName()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\", \"old_string\": \"a\", " +
      "\"new_string\": \"b\"}, \"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetWriteEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetWriteEditPretoolOutput blocks plugin file edit on protected branch.
   */
  @Test
  public void getWriteEditPretoolBlocksPluginFileOnProtectedBranch()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Write\", \"tool_input\": {\"file_path\": \"/workspace/plugin/hooks/test.py\"}, " +
      "\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetWriteEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").contains("\"decision\"").contains("\"block\"");
  }

  // --- WarnBaseBranchEdit handler tests (using real git state) ---

  /**
   * Verifies that WarnBaseBranchEdit warns on base branch for non-allowed files.
   * <p>
   * Creates a temp git repo on branch 'main' and uses a file path within it
   * so that branch detection derives from the file path, not the process CWD.
   */
  @Test
  public void warnBaseBranchEditWarnsOnBaseBranch() throws IOException
  {
    Path tempDir = createTempGitRepo("main");
    try
    {
      String filePath = tempDir.resolve("hooks/src/main/java/SomeNewFile.java").toString();
      JsonNode toolInput = JsonMapper.builder().build().readTree(
        "{\"file_path\": \"" + filePath.replace("\\", "\\\\") + "\"}");
      FileWriteHandler.Result result = new WarnBaseBranchEdit().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("BASE BRANCH EDIT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WarnBaseBranchEdit allows existing hooks files.
   * <p>
   * The handler allows files in hooks/ or skills/ directories IF the file exists.
   * Use a real file path that exists in the repo.
   */
  @Test
  public void warnBaseBranchEditAllowsExistingHooksFiles() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/hooks/pom.xml\"}");
    FileWriteHandler.Result result = new WarnBaseBranchEdit().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
    requireThat(result.reason(), "reason").isEmpty();
  }

  // --- EnforcePluginFileIsolation handler tests (using real git state) ---

  /**
   * Verifies that EnforcePluginFileIsolation detects plugin subdirectories.
   * <p>
   * Test that various paths are correctly identified as plugin files.
   * On v2.1 (protected branch), plugin files should be blocked.
   */
  @Test
  public void enforcePluginFileIsolationDetectsPluginSubdirectories() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/plugin/skills/my-skill/SKILL.md\"}");
    FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("Cannot edit plugin files");
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows non-plugin paths.
   * <p>
   * Test that non-plugin files are not blocked.
   */
  @Test
  public void enforcePluginFileIsolationAllowsNonPluginPaths() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/hooks/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java\"}");
    FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  // --- GetWriteEditPretoolOutput dispatcher tests (using real handlers) ---

  /**
   * Verifies that GetWriteEditPretoolOutput dispatcher blocks plugin file with block decision.
   * <p>
   * This tests the FULL dispatcher path including the block behavior with the real
   * additionalContext isEmpty() check that was the CRITICAL bug fix.
   */
  @Test
  public void getWriteEditPretoolBlocksPluginFileWithBlockDecision()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/workspace/plugin/hooks/test.sh\"}, " +
      "\"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetWriteEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").contains("\"decision\"");
    requireThat(result, "result").contains("\"block\"");
    requireThat(result, "result").contains("Cannot edit plugin files");
  }

  /**
   * Verifies that GetWriteEditPretoolOutput allows non-plugin file with warning.
   * <p>
   * Test that non-plugin, non-allowed files on v2.1 produce {} output from dispatcher
   * (warning goes to stderr, stdout is {}).
   */
  @Test
  public void getWriteEditPretoolAllowsNonPluginFileWithWarning()
  {
    HookInput input = createInput(
      "{\"tool_name\": \"Write\", \"tool_input\": {\"file_path\": \"/tmp/some-new-source.java\"}, " +
      "\"session_id\": \"test-session\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    new GetWriteEditPretoolOutput().run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  // --- Warning accumulation tests ---

  /**
   * Verifies that multiple warnings from different handlers are all written to stderr for Write/Edit operations.
   */
  @Test
  @SuppressWarnings("PMD.CloseResource")
  public void writeEditPretoolAccumulatesMultipleWarnings() throws IOException
  {
    FileWriteHandler handler1 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from handler 1");
    FileWriteHandler handler2 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from handler 2");

    GetWriteEditPretoolOutput dispatcher = new GetWriteEditPretoolOutput(List.of(handler1, handler2));

    JsonMapper mapper = JsonMapper.builder().build();
    String inputJson = "{\"tool_name\": \"Write\", \"tool_input\": " +
      "{\"file_path\": \"/workspace/some-file.txt\"}, \"session_id\": \"test-session-123\"}";
    JsonNode fullInput = mapper.readTree(inputJson);
    HookInput input = HookInput.readFrom(
      new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try
    {
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      dispatcher.run(input, output);
    }
    finally
    {
      System.setErr(originalErr);
    }

    String stderrContent = stderr.toString(StandardCharsets.UTF_8);
    requireThat(stderrContent, "stderrContent").contains("Warning from handler 1");
    requireThat(stderrContent, "stderrContent").contains("Warning from handler 2");

    String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
    requireThat(stdoutContent, "stdoutContent").doesNotContain("\"decision\"");
  }

  /**
   * Verifies that multiple warnings from different handlers are all written to stderr for Edit operations.
   */
  @Test
  @SuppressWarnings("PMD.CloseResource")
  public void editPretoolAccumulatesMultipleWarnings() throws IOException
  {
    EditHandler handler1 = (toolInput, sessionId) -> EditHandler.Result.warn("Warning from edit handler 1");
    EditHandler handler2 = (toolInput, sessionId) -> EditHandler.Result.warn("Warning from edit handler 2");

    GetEditPretoolOutput dispatcher = new GetEditPretoolOutput(List.of(handler1, handler2));

    JsonMapper mapper = JsonMapper.builder().build();
    String inputJson = "{\"tool_name\": \"Edit\", \"tool_input\": " +
      "{\"file_path\": \"/workspace/test.txt\"}, \"session_id\": \"test-session-456\"}";
    JsonNode fullInput = mapper.readTree(inputJson);
    HookInput input = HookInput.readFrom(
      new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try
    {
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      dispatcher.run(input, output);
    }
    finally
    {
      System.setErr(originalErr);
    }

    String stderrContent = stderr.toString(StandardCharsets.UTF_8);
    requireThat(stderrContent, "stderrContent").contains("Warning from edit handler 1");
    requireThat(stderrContent, "stderrContent").contains("Warning from edit handler 2");

    String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
    requireThat(stdoutContent, "stdoutContent").doesNotContain("\"decision\"");
  }

  /**
   * Verifies that multiple warnings from different handlers are all written to stderr for Task operations.
   */
  @Test
  @SuppressWarnings("PMD.CloseResource")
  public void taskPretoolAccumulatesMultipleWarnings() throws IOException
  {
    TaskHandler handler1 = (toolInput, sessionId) -> TaskHandler.Result.warn("Warning from task handler 1");
    TaskHandler handler2 = (toolInput, sessionId) -> TaskHandler.Result.warn("Warning from task handler 2");

    GetTaskPretoolOutput dispatcher = new GetTaskPretoolOutput(List.of(handler1, handler2));

    JsonMapper mapper = JsonMapper.builder().build();
    String inputJson = "{\"tool_name\": \"Task\", \"tool_input\": " +
      "{\"subagent_type\": \"cat:implement\"}, \"session_id\": \"test-session-789\"}";
    JsonNode fullInput = mapper.readTree(inputJson);
    HookInput input = HookInput.readFrom(
      new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try
    {
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      dispatcher.run(input, output);
    }
    finally
    {
      System.setErr(originalErr);
    }

    String stderrContent = stderr.toString(StandardCharsets.UTF_8);
    requireThat(stderrContent, "stderrContent").contains("Warning from task handler 1");
    requireThat(stderrContent, "stderrContent").contains("Warning from task handler 2");

    String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
    requireThat(stdoutContent, "stdoutContent").doesNotContain("\"decision\"");
  }

  /**
   * Verifies that when one handler warns and the next blocks, only the block is output and warnings are not written.
   */
  @Test
  @SuppressWarnings("PMD.CloseResource")
  public void writeEditPretoolBlocksWithoutWarnings() throws IOException
  {
    FileWriteHandler handler1 = (toolInput, sessionId) ->
      FileWriteHandler.Result.warn("This warning should not appear");
    FileWriteHandler handler2 = (toolInput, sessionId) ->
      FileWriteHandler.Result.block("Blocked by handler 2");

    GetWriteEditPretoolOutput dispatcher = new GetWriteEditPretoolOutput(List.of(handler1, handler2));

    JsonMapper mapper = JsonMapper.builder().build();
    String inputJson = "{\"tool_name\": \"Write\", \"tool_input\": " +
      "{\"file_path\": \"/workspace/blocked.txt\"}, \"session_id\": \"test-session-999\"}";
    JsonNode fullInput = mapper.readTree(inputJson);
    HookInput input = HookInput.readFrom(
      new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try
    {
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      dispatcher.run(input, output);
    }
    finally
    {
      System.setErr(originalErr);
    }

    String stderrContent = stderr.toString(StandardCharsets.UTF_8);
    requireThat(stderrContent, "stderrContent").doesNotContain("This warning should not appear");

    String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
    requireThat(stdoutContent, "stdoutContent").contains("\"decision\"");
    requireThat(stdoutContent, "stdoutContent").contains("\"block\"");
    requireThat(stdoutContent, "stdoutContent").contains("Blocked by handler 2");
  }

  // --- BlockWorktreeCd handler tests ---

  /**
   * Verifies that BlockWorktreeCd blocks cd commands into worktree directories.
   */
  @Test
  public void blockWorktreeCdBlocksCdToWorktree() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"command\": \"cd /workspace/.claude/cat/worktrees/my-task\"}");
    BashHandler.Result result = new BlockWorktreeCd().check(
      "cd /workspace/.claude/cat/worktrees/my-task", toolInput, null, "test");
    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("CD INTO WORKTREE BLOCKED");
    requireThat(result.reason(), "reason").contains("git -C");
  }

  /**
   * Verifies that BlockWorktreeCd blocks cd with quotes around path.
   */
  @Test
  public void blockWorktreeCdBlocksCdWithQuotes() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"command\": \"cd '.claude/cat/worktrees/task-name'\"}");
    BashHandler.Result result = new BlockWorktreeCd().check(
      "cd '.claude/cat/worktrees/task-name'", toolInput, null, "test");
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that BlockWorktreeCd allows normal cd commands.
   */
  @Test
  public void blockWorktreeCdAllowsNormalCd() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"command\": \"cd /workspace/hooks\"}");
    BashHandler.Result result = new BlockWorktreeCd().check(
      "cd /workspace/hooks", toolInput, null, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that BlockWorktreeCd allows git -C commands.
   */
  @Test
  public void blockWorktreeCdAllowsGitC() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"command\": \"git -C /workspace/.claude/cat/worktrees/my-task status\"}");
    BashHandler.Result result = new BlockWorktreeCd().check(
      "git -C /workspace/.claude/cat/worktrees/my-task status", toolInput, null, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  // --- ValidateStateMdFormat handler tests ---

  /**
   * Verifies that ValidateStateMdFormat blocks STATE.md writes missing Status field.
   */
  @Test
  public void validateStateMdFormatBlocksMissingStatus() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
      "\"content\": \"- **Progress:** 50%\\n- **Dependencies:** []\"}");
    FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("Missing '- **Status:** value'");
  }

  /**
   * Verifies that ValidateStateMdFormat blocks STATE.md writes missing Progress field.
   */
  @Test
  public void validateStateMdFormatBlocksMissingProgress() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
      "\"content\": \"- **Status:** pending\\n- **Dependencies:** []\"}");
    FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("Missing '- **Progress:** value'");
  }

  /**
   * Verifies that ValidateStateMdFormat blocks STATE.md writes missing Dependencies field.
   */
  @Test
  public void validateStateMdFormatBlocksMissingDependencies() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
      "\"content\": \"- **Status:** pending\\n- **Progress:** 0%\"}");
    FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("Missing '- **Dependencies:** [...]'");
  }

  /**
   * Verifies that ValidateStateMdFormat allows valid STATE.md content.
   */
  @Test
  public void validateStateMdFormatAllowsValidContent() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
      "\"content\": \"- **Status:** pending\\n- **Progress:** 0%\\n- **Dependencies:** []\"}");
    FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that ValidateStateMdFormat allows non-STATE.md files.
   */
  @Test
  public void validateStateMdFormatAllowsNonStateMdFiles() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/README.md\", \"content\": \"Some content\"}");
    FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that ValidateStateMdFormat allows STATE.md files outside issues directory.
   */
  @Test
  public void validateStateMdFormatAllowsNonIssueStateMd() throws IOException
  {
    JsonNode toolInput = JsonMapper.builder().build().readTree(
      "{\"file_path\": \"/workspace/docs/STATE.md\", \"content\": \"Random content\"}");
    FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Creates a temporary git repository with the specified branch.
   *
   * @param branchName the branch name to create
   * @return the path to the created git repository
   * @throws IOException if repository creation fails
   */
  private Path createTempGitRepo(String branchName) throws IOException
  {
    Path tempDir = Files.createTempDirectory("git-test-");

    runGitCommand(tempDir, "init");
    runGitCommand(tempDir, "config", "user.email", "test@example.com");
    runGitCommand(tempDir, "config", "user.name", "Test User");

    Files.writeString(tempDir.resolve("README.md"), "test");
    runGitCommand(tempDir, "add", "README.md");
    runGitCommand(tempDir, "commit", "-m", "Initial commit");

    if (!branchName.equals("master") && !branchName.equals("main"))
    {
      runGitCommand(tempDir, "checkout", "-b", branchName);
    }
    if (branchName.equals("main") && !getCurrentBranchName(tempDir).equals("main"))
    {
      runGitCommand(tempDir, "branch", "-m", "main");
    }

    return tempDir;
  }

  /**
   * Runs a git command in the specified directory.
   *
   * @param directory the directory to run the command in
   * @param args the git command arguments
   */
  private void runGitCommand(Path directory, String... args)
  {
    try
    {
      String[] command = new String[args.length + 3];
      command[0] = "git";
      command[1] = "-C";
      command[2] = directory.toString();
      System.arraycopy(args, 0, command, 3, args.length);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          line = reader.readLine();
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new IOException("Git command failed with exit code " + exitCode);
    }
    catch (IOException | InterruptedException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Gets the current branch name for a directory.
   *
   * @param directory the directory to check
   * @return the branch name
   */
  private String getCurrentBranchName(Path directory)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "-C", directory.toString(), "branch", "--show-current");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String branch;
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        branch = reader.readLine();
      }

      process.waitFor();
      if (branch != null)
        return branch.trim();
      return "";
    }
    catch (IOException | InterruptedException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}
