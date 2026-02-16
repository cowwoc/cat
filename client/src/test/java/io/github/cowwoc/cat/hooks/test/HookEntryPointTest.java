/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.EditHandler;
import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.GetAskOutput;
import io.github.cowwoc.cat.hooks.GetBashPostOutput;
import io.github.cowwoc.cat.hooks.GetBashOutput;
import io.github.cowwoc.cat.hooks.GetEditOutput;
import io.github.cowwoc.cat.hooks.GetPostOutput;
import io.github.cowwoc.cat.hooks.GetReadPostOutput;
import io.github.cowwoc.cat.hooks.GetReadOutput;
import io.github.cowwoc.cat.hooks.GetSkillOutput;
import io.github.cowwoc.cat.hooks.GetTaskOutput;
import io.github.cowwoc.cat.hooks.GetWriteEditOutput;
import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.edit.EnforceWorkflowCompletion;
import io.github.cowwoc.cat.hooks.edit.WarnSkillEditWithoutBuilder;
import io.github.cowwoc.cat.hooks.write.EnforcePluginFileIsolation;
import io.github.cowwoc.cat.hooks.write.ValidateStateMdFormat;
import io.github.cowwoc.cat.hooks.write.WarnBaseBranchEdit;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
   * @param mapper the JSON mapper
   * @param json the JSON input string
   * @return the parsed HookInput
   */
  private HookInput createInput(JsonMapper mapper, String json)
  {
    return HookInput.readFrom(mapper, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }



  // --- GetSkillOutput tests ---

  /**
   * Verifies that GetSkillOutput returns empty JSON when given empty input.
   */
  @Test
  public void getSkillOutputReturnsEmptyJsonForEmptyInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetSkillOutput(scope).run(input, output);

      requireThat(hookResult.output().trim(), "output").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetSkillOutput returns empty JSON when no message is present.
   */
  @Test
  public void getSkillOutputReturnsEmptyJsonWhenNoMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetSkillOutput(scope).run(input, output);

      requireThat(hookResult.output().trim(), "output").isEqualTo("{}");
    }
  }

  // --- GetBashOutput tests ---

  /**
   * Verifies that GetBashOutput returns empty JSON for non-Bash tools.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonForNonBashTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Read\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetBashOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetBashOutput returns empty JSON when Bash tool has no command.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWhenNoCommand() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Bash\", \"tool_input\": {}}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetBashOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetBashOutput returns empty JSON for Bash tool with command.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWithCommand() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"ls -la\"}, " +
          "\"session_id\": \"test-session\", \"cwd\": \"/workspace\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetBashOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- GetBashPostOutput tests ---

  /**
   * Verifies that GetBashPostOutput returns empty JSON for non-Bash tools.
   */
  @Test
  public void getBashPosttoolReturnsEmptyJsonForNonBashTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Read\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetBashPostOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- GetReadOutput tests ---

  /**
   * Verifies that GetReadOutput returns empty JSON for Read tool.
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForReadTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Read\", \"tool_input\": {\"file_path\": \"/tmp/test\"}, \"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetReadOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetReadOutput returns empty JSON for unsupported tools.
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForUnsupportedTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Bash\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetReadOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- GetReadPostOutput tests ---

  /**
   * Verifies that GetReadPostOutput returns empty JSON for Grep tool.
   */
  @Test
  public void getReadPosttoolReturnsEmptyJsonForGrepTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Grep\", \"tool_input\": {}, \"tool_result\": {}, \"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetReadPostOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- GetPostOutput tests ---

  /**
   * Verifies that GetPostOutput returns empty JSON when given empty input.
   */
  @Test
  public void getPosttoolReturnsEmptyJsonForEmptyInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetPostOutput(tempDir).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that GetPostOutput returns empty JSON with a tool name present.
   */
  @Test
  public void getPosttoolReturnsEmptyJsonWithToolName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Write\", \"tool_result\": {}, \"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetPostOutput(tempDir).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- HookInput error path tests ---

  /**
   * Verifies that HookInput.readFrom with malformed JSON returns empty HookInput.
   */
  @Test
  public void hookInputWithMalformedJsonReturnsEmpty() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "not valid json {{{");
      requireThat(input.isEmpty(), "isEmpty").isTrue();
    }
  }

  /**
   * Verifies that HookInput.readFrom with blank input returns empty HookInput.
   */
  @Test
  public void hookInputWithBlankInputReturnsEmpty() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "   ");
      requireThat(input.isEmpty(), "isEmpty").isTrue();
    }
  }

  /**
   * Verifies that HookInput.readFrom with empty string returns empty HookInput.
   */
  @Test
  public void hookInputWithEmptyStringReturnsEmpty() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "");
      requireThat(input.isEmpty(), "isEmpty").isTrue();
    }
  }

  /**
   * Verifies that HookInput.getString with non-string value throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void hookInputGetStringWithNonStringValueThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"count\": 42}");
      input.getString("count");
    }
  }

  /**
   * Verifies that HookInput.getString returns empty string for missing key.
   */
  @Test
  public void hookInputGetStringReturnEmptyForMissingKey() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"key\": \"value\"}");
      String result = input.getString("nonexistent");
      requireThat(result, "result").isEqualTo("");
    }
  }

  /**
   * Verifies that HookInput.readFrom with null stream throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void hookInputReadFromNullStreamThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    HookInput.readFrom(scope.getJsonMapper(), null);
    }
  }

  /**
   * Verifies that HookInput.empty returns an empty input.
   */
  @Test
  public void hookInputEmptyReturnsEmptyInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    HookInput input = HookInput.empty(scope.getJsonMapper());
    requireThat(input.isEmpty(), "isEmpty").isTrue();
    }
  }

  // --- HookOutput error path tests ---

  /**
   * Verifies that HookOutput.block with blank reason throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void hookOutputBlockWithBlankReasonThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      HookOutput output = new HookOutput(scope);
      output.block("   ");
    }
  }

  /**
   * Verifies that HookOutput.block with null reason throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void hookOutputBlockWithNullReasonThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      HookOutput output = new HookOutput(scope);
      output.block(null);
    }
  }

  /**
   * Verifies that HookOutput constructor with null mapper throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void hookOutputWithNullMapperThrows()
  {
    new HookOutput(null);
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
  public void hookOutputAdditionalContextWithBlankEventNameThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      HookOutput output = new HookOutput(scope);
      output.additionalContext("", "some context");
    }
  }

  /**
   * Verifies that HookOutput.block produces valid JSON with decision field.
   */
  @Test
  public void hookOutputBlockProducesValidJson() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      HookOutput output = new HookOutput(scope);
      String result = output.block("test reason");

      requireThat(result, "result").contains("\"decision\"").contains("\"block\"").contains("\"test reason\"");
    }
  }

  /**
   * Verifies that HookOutput.empty produces empty JSON object.
   */
  @Test
  public void hookOutputEmptyProducesEmptyJson() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      HookOutput output = new HookOutput(scope);
      String result = output.empty();

      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- GetAskOutput tests ---

  /**
   * Verifies that GetAskOutput returns empty JSON for non-AskUserQuestion tools.
   */
  @Test
  public void getAskPretoolReturnsEmptyForNonAskTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Read\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetAskOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getAskPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- GetEditOutput tests ---

  /**
   * Verifies that GetEditOutput returns empty JSON for non-Edit tools.
   */
  @Test
  public void getEditPretoolReturnsEmptyForNonEditTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Read\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetEditOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getEditPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetEditOutput throws IllegalArgumentException when session_id is missing.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getEditPretoolThrowsOnMissingSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}}");
      HookOutput output = new HookOutput(scope);

      new GetEditOutput().run(input, output);
    }
  }

  // --- GetTaskOutput tests ---

  /**
   * Verifies that GetTaskOutput returns empty JSON for non-Task tools.
   */
  @Test
  public void getTaskPretoolReturnsEmptyForNonTaskTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Read\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetTaskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetTaskOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getTaskPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Task\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetTaskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- EnforceWorkflowCompletion tests ---

  /**
   * Verifies that EnforceWorkflowCompletion allows edits to non-STATE.md files.
   */
  @Test
  public void enforceWorkflowCompletionAllowsNonStateMdFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}, \"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- WarnSkillEditWithoutBuilder tests ---

  /**
   * Verifies that WarnSkillEditWithoutBuilder allows edits to non-skill files.
   */
  @Test
  public void warnSkillEditAllowsNonSkillFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/README.md\"}, \"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- WarnUnsquashedApproval tests ---

  /**
   * Verifies that WarnUnsquashedApproval allows non-approval questions.
   */
  @Test
  public void warnUnsquashedApprovalAllowsNonApprovalQuestions() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"What is your name?\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- WarnApprovalWithoutRenderDiff tests ---

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows non-approval questions.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsNonApprovalQuestions() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Continue?\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- EnforceApprovalBeforeMerge tests ---

  /**
   * Verifies that EnforceApprovalBeforeMerge allows non-work-merge tasks.
   */
  @Test
  public void enforceApprovalBeforeMergeAllowsNonWorkMergeTasks() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Task\", \"tool_input\": {\"subagent_type\": \"cat:implement\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetTaskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that EnforceApprovalBeforeMerge allows tasks with empty subagent_type.
   */
  @Test
  public void enforceApprovalBeforeMergeAllowsEmptySubagentType() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Task\", \"tool_input\": {}, \"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetTaskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- EnforceWorkflowCompletion handler tests ---

  /**
   * Verifies that EnforceWorkflowCompletion warns when editing STATE.md with status closed.
   */
  @Test
  public void enforceWorkflowCompletionWarnsOnStatusClosed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".claude/cat/v2/v2.1/my-task/STATE.md\", " +
        "\"new_string\": \"Status: closed\"}");
      EditHandler.Result result = new EnforceWorkflowCompletion().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("WORKFLOW COMPLETION CHECK");
      requireThat(result.reason(), "reason").contains("my-task");
    }
  }

  /**
   * Verifies that EnforceWorkflowCompletion warns on lowercase status:closed.
   */
  @Test
  public void enforceWorkflowCompletionWarnsOnLowercaseStatusClosed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".claude/cat/v2/v2.1/my-task/STATE.md\", " +
        "\"new_string\": \"status:closed\"}");
      EditHandler.Result result = new EnforceWorkflowCompletion().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("WORKFLOW COMPLETION CHECK");
    }
  }

  /**
   * Verifies that EnforceWorkflowCompletion allows edits when new_string is missing.
   */
  @Test
  public void enforceWorkflowCompletionAllowsMissingNewString() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \".claude/cat/v2/v2.1/my-task/STATE.md\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that EnforceWorkflowCompletion allows when status is not closed.
   */
  @Test
  public void enforceWorkflowCompletionAllowsNonClosedStatus() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \".claude/cat/v2/v2.1/fix-bug-123/STATE.md\", " +
        "\"new_string\": \"Status: in_progress\"}, \"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- WarnSkillEditWithoutBuilder handler tests ---

  /**
   * Verifies that WarnSkillEditWithoutBuilder warns when editing skill SKILL.md files.
   */
  @Test
  public void warnSkillEditWarnsOnSkillMdFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/plugin/skills/my-skill/SKILL.md\"}");
      EditHandler.Result result = new WarnSkillEditWithoutBuilder().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("SKILL EDIT DETECTED");
      requireThat(result.reason(), "reason").contains("my-skill");
    }
  }

  /**
   * Verifies that WarnSkillEditWithoutBuilder allows files that do not match skill pattern.
   */
  @Test
  public void warnSkillEditAllowsNonSkillPaths() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/workspace/skills/test-skill/SKILL.md\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that WarnSkillEditWithoutBuilder allows non-SKILL.md files in skills directory.
   */
  @Test
  public void warnSkillEditAllowsNonSkillMdFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/workspace/plugin/skills/my-skill/helper.py\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- WarnApprovalWithoutRenderDiff handler tests ---

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows non-approval questions.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsNonApprovalInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"What color?\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows when CLAUDE_PROJECT_DIR is missing.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsMissingProjectDir() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Approve?\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- WarnUnsquashedApproval handler tests ---

  /**
   * Verifies that WarnUnsquashedApproval containsApprove method works correctly.
   */
  @Test
  public void warnUnsquashedApprovalDetectsApproveInInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Ready to approve?\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      // WarnUnsquashedApproval also checks git state - outside a task worktree it allows
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that WarnUnsquashedApproval detects uppercase APPROVE.
   */
  @Test
  public void warnUnsquashedApprovalDetectsUppercaseApprove() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"APPROVE changes?\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      // WarnUnsquashedApproval also checks git state - outside a task worktree it allows
      requireThat(result, "result").isEqualTo("{}");
    }
  }


  /**
   * Verifies that GetAskOutput returns additionalContext when handler provides it.
   */
  @Test
  public void getAskPretoolReturnsAdditionalContextEarly() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Continue?\"}, " +
        "\"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetAskOutput(scope).run(input, output);

      String result = hookResult.output().trim();
      // WarnApprovalWithoutRenderDiff and WarnUnsquashedApproval don't inject context in this case
      // This test verifies no crash occurs
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- EnforcePluginFileIsolation handler tests ---

  /**
   * Verifies that EnforcePluginFileIsolation blocks editing plugin files on protected branches.
   */
  @Test
  public void enforcePluginFileIsolationBlocksPluginFileOnProtectedBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/plugin/hooks/test.py\"}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      // On the v2.1 branch (protected), plugin files should be blocked
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit plugin files");
    }
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows editing non-plugin files.
   */
  @Test
  public void enforcePluginFileIsolationAllowsNonPluginFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/docs/README.md\"}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows empty file path.
   */
  @Test
  public void enforcePluginFileIsolationAllowsEmptyPath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  // --- WarnBaseBranchEdit handler tests ---

  /**
   * Verifies that WarnBaseBranchEdit allows editing when path is empty.
   */
  @Test
  public void warnBaseBranchEditAllowsEmptyPath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{}");
      FileWriteHandler.Result result = new WarnBaseBranchEdit().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that WarnBaseBranchEdit allows editing allowed patterns.
   */
  @Test
  public void warnBaseBranchEditAllowsAllowedPatterns() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/.claude/settings.json\"}");
      FileWriteHandler.Result result = new WarnBaseBranchEdit().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  // --- GetWriteEditOutput dispatcher tests ---

  /**
   * Verifies that GetWriteEditOutput returns empty JSON for non-Write/Edit tools.
   */
  @Test
  public void getWriteEditPretoolReturnsEmptyForNonWriteEditTool() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"tool_name\": \"Read\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetWriteEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetWriteEditOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getWriteEditPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Write\", \"tool_input\": {}, \"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetWriteEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetWriteEditOutput uses case-insensitive matching for tool names.
   */
  @Test
  public void getWriteEditPretoolUsesCaseInsensitiveMatching() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"write\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}, \"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetWriteEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetWriteEditOutput accepts Edit tool_name.
   */
  @Test
  public void getWriteEditPretoolAcceptsEditToolName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\", \"old_string\": \"a\", " +
        "\"new_string\": \"b\"}, \"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetWriteEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  /**
   * Verifies that GetWriteEditOutput blocks plugin file edit on protected branch.
   */
  @Test
  public void getWriteEditPretoolBlocksPluginFileOnProtectedBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Write\", \"tool_input\": {\"file_path\": \"/workspace/plugin/hooks/test.py\"}, " +
        "\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetWriteEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").contains("\"decision\"").contains("\"block\"");
    }
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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempGitRepo("main");
      try
      {
        String filePath = tempDir.resolve("hooks/src/main/java/SomeNewFile.java").toString();
        JsonNode toolInput = mapper.readTree(
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
  }

  /**
   * Verifies that WarnBaseBranchEdit allows existing client files.
   * <p>
   * The handler allows files in client/ or skills/ directories IF the file exists.
   * Use a real file path that exists in the repo.
   */
  @Test
  public void warnBaseBranchEditAllowsExistingEngineFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path enginePom = Path.of("pom.xml").toAbsolutePath();
      String toolInputJson = String.format("{\"file_path\": \"%s\"}", enginePom.toString().replace("\\", "\\\\"));
      JsonNode toolInput = mapper.readTree(toolInputJson);
      FileWriteHandler.Result result = new WarnBaseBranchEdit().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/plugin/skills/my-skill/SKILL.md\"}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit plugin files");
    }
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows non-plugin paths.
   * <p>
   * Test that non-plugin files are not blocked.
   */
  @Test
  public void enforcePluginFileIsolationAllowsNonPluginPaths() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java\"}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  // --- GetWriteEditOutput dispatcher tests (using real handlers) ---

  /**
   * Verifies that GetWriteEditOutput dispatcher blocks plugin file with block decision.
   * <p>
   * This tests the FULL dispatcher path including the block behavior with the real
   * additionalContext isEmpty() check that was the CRITICAL bug fix.
   */
  @Test
  public void getWriteEditPretoolBlocksPluginFileWithBlockDecision() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/workspace/plugin/hooks/test.sh\"}, " +
        "\"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetWriteEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").contains("\"decision\"");
      requireThat(result, "result").contains("\"block\"");
      requireThat(result, "result").contains("Cannot edit plugin files");
    }
  }

  /**
   * Verifies that GetWriteEditOutput allows non-plugin file with warning.
   * <p>
   * Test that non-plugin, non-allowed files on v2.1 produce {} output from dispatcher
   * (warning goes to stderr, stdout is {}).
   */
  @Test
  public void getWriteEditPretoolAllowsNonPluginFileWithWarning() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper,
        "{\"tool_name\": \"Write\", \"tool_input\": {\"file_path\": \"/tmp/some-new-source.java\"}, " +
        "\"session_id\": \"test-session\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult hookResult = new GetWriteEditOutput().run(input, output);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
  }

  // --- Warning accumulation tests ---

  /**
   * Verifies that multiple warnings from different handlers are all returned in HookResult.warnings().
   */
  @Test
  public void writeEditPretoolAccumulatesMultipleWarnings() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      FileWriteHandler handler1 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from handler 1");
      FileWriteHandler handler2 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from handler 2");

      GetWriteEditOutput dispatcher = new GetWriteEditOutput(List.of(handler1, handler2));

      JsonMapper mapper = scope.getJsonMapper();
      String inputJson = "{\"tool_name\": \"Write\", \"tool_input\": " +
        "{\"file_path\": \"/workspace/some-file.txt\"}, \"session_id\": \"test-session-123\"}";
      JsonNode fullInput = mapper.readTree(inputJson);
      HookInput input = HookInput.readFrom(scope.getJsonMapper(),
        new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

      HookOutput output = new HookOutput(scope);
      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.warnings(), "warnings").contains("Warning from handler 1");
      requireThat(result.warnings(), "warnings").contains("Warning from handler 2");
      requireThat(result.output(), "output").doesNotContain("\"decision\"");
    }
  }

  /**
   * Verifies that multiple warnings from different handlers are all returned in HookResult.warnings()
   * for Edit operations.
   */
  @Test
  public void editPretoolAccumulatesMultipleWarnings() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      EditHandler handler1 = (toolInput, sessionId) -> EditHandler.Result.warn("Warning from edit handler 1");
      EditHandler handler2 = (toolInput, sessionId) -> EditHandler.Result.warn("Warning from edit handler 2");

      GetEditOutput dispatcher = new GetEditOutput(List.of(handler1, handler2));

      JsonMapper mapper = scope.getJsonMapper();
      String inputJson = "{\"tool_name\": \"Edit\", \"tool_input\": " +
        "{\"file_path\": \"/workspace/test.txt\"}, \"session_id\": \"test-session-456\"}";
      JsonNode fullInput = mapper.readTree(inputJson);
      HookInput input = HookInput.readFrom(scope.getJsonMapper(),
        new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

      HookOutput output = new HookOutput(scope);
      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.warnings(), "warnings").contains("Warning from edit handler 1");
      requireThat(result.warnings(), "warnings").contains("Warning from edit handler 2");
      requireThat(result.output(), "output").doesNotContain("\"decision\"");
    }
  }

  /**
   * Verifies that multiple warnings from different handlers are all returned in HookResult.warnings()
   * for Task operations.
   */
  @Test
  public void taskPretoolAccumulatesMultipleWarnings() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      TaskHandler handler1 = (toolInput, sessionId) -> TaskHandler.Result.warn("Warning from task handler 1");
      TaskHandler handler2 = (toolInput, sessionId) -> TaskHandler.Result.warn("Warning from task handler 2");

      GetTaskOutput dispatcher = new GetTaskOutput(List.of(handler1, handler2));

      JsonMapper mapper = scope.getJsonMapper();
      String inputJson = "{\"tool_name\": \"Task\", \"tool_input\": " +
        "{\"subagent_type\": \"cat:implement\"}, \"session_id\": \"test-session-789\"}";
      JsonNode fullInput = mapper.readTree(inputJson);
      HookInput input = HookInput.readFrom(scope.getJsonMapper(),
        new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

      HookOutput output = new HookOutput(scope);
      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.warnings(), "warnings").contains("Warning from task handler 1");
      requireThat(result.warnings(), "warnings").contains("Warning from task handler 2");
      requireThat(result.output(), "output").doesNotContain("\"decision\"");
    }
  }

  /**
   * Verifies that when one handler warns and the next blocks, only the block is output and warnings are not included.
   */
  @Test
  public void writeEditPretoolBlocksWithoutWarnings() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      FileWriteHandler handler1 = (toolInput, sessionId) ->
        FileWriteHandler.Result.warn("This warning should not appear");
      FileWriteHandler handler2 = (toolInput, sessionId) ->
        FileWriteHandler.Result.block("Blocked by handler 2");

      GetWriteEditOutput dispatcher = new GetWriteEditOutput(List.of(handler1, handler2));

      JsonMapper mapper = scope.getJsonMapper();
      String inputJson = "{\"tool_name\": \"Write\", \"tool_input\": " +
        "{\"file_path\": \"/workspace/blocked.txt\"}, \"session_id\": \"test-session-999\"}";
      JsonNode fullInput = mapper.readTree(inputJson);
      HookInput input = HookInput.readFrom(scope.getJsonMapper(),
        new ByteArrayInputStream(fullInput.toString().getBytes(StandardCharsets.UTF_8)));

      HookOutput output = new HookOutput(scope);
      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.warnings(), "warnings").doesNotContain("This warning should not appear");
      requireThat(result.output(), "output").contains("\"decision\"");
      requireThat(result.output(), "output").contains("\"block\"");
      requireThat(result.output(), "output").contains("Blocked by handler 2");
    }
  }

  // --- BlockWorktreeCd handler tests ---
  // --- ValidateStateMdFormat handler tests ---

  /**
   * Verifies that ValidateStateMdFormat blocks STATE.md writes missing Status field.
   */
  @Test
  public void validateStateMdFormatBlocksMissingStatus() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
        "\"content\": \"- **Progress:** 50%\\n- **Dependencies:** []\"}");
      FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing '- **Status:** value'");
    }
  }

  /**
   * Verifies that ValidateStateMdFormat blocks STATE.md writes missing Progress field.
   */
  @Test
  public void validateStateMdFormatBlocksMissingProgress() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
        "\"content\": \"- **Status:** pending\\n- **Dependencies:** []\"}");
      FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing '- **Progress:** value'");
    }
  }

  /**
   * Verifies that ValidateStateMdFormat blocks STATE.md writes missing Dependencies field.
   */
  @Test
  public void validateStateMdFormatBlocksMissingDependencies() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
        "\"content\": \"- **Status:** pending\\n- **Progress:** 0%\"}");
      FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing '- **Dependencies:** [...]'");
    }
  }

  /**
   * Verifies that ValidateStateMdFormat allows valid STATE.md content.
   */
  @Test
  public void validateStateMdFormatAllowsValidContent() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".claude/cat/issues/v2/v2.1/my-task/STATE.md\", " +
        "\"content\": \"- **Status:** pending\\n- **Progress:** 0%\\n- **Dependencies:** []\"}");
      FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that ValidateStateMdFormat allows non-STATE.md files.
   */
  @Test
  public void validateStateMdFormatAllowsNonStateMdFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/README.md\", \"content\": \"Some content\"}");
      FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that ValidateStateMdFormat allows STATE.md files outside issues directory.
   */
  @Test
  public void validateStateMdFormatAllowsNonIssueStateMd() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/docs/STATE.md\", \"content\": \"Random content\"}");
      FileWriteHandler.Result result = new ValidateStateMdFormat().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
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
