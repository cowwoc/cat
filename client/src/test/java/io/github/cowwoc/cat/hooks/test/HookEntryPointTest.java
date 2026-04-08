/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.PreAskHook;
import io.github.cowwoc.cat.hooks.PostBashHook;
import io.github.cowwoc.cat.hooks.PreToolUseHook;
import io.github.cowwoc.cat.hooks.PostToolUseHook;
import io.github.cowwoc.cat.hooks.PostReadHook;
import io.github.cowwoc.cat.hooks.PreReadHook;
import io.github.cowwoc.cat.hooks.UserPromptSubmitHook;
import io.github.cowwoc.cat.hooks.PreIssueHook;

import io.github.cowwoc.cat.hooks.PreWriteHook;
import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.Strings;
import io.github.cowwoc.cat.hooks.edit.EnforceWorkflowCompletion;
import io.github.cowwoc.cat.hooks.task.EnforceWorktreeSafetyBeforeMerge;
import io.github.cowwoc.cat.hooks.write.EnforcePluginFileIsolation;
import io.github.cowwoc.cat.hooks.write.WarnBaseBranchEdit;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.testng.annotations.Test;

import java.io.BufferedReader;
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
  // --- UserPromptSubmitHook tests ---

  /**
   * Verifies that UserPromptSubmitHook returns empty JSON when given empty input.
   */
  @Test
  public void userPromptSubmitHookReturnsEmptyJsonForEmptyInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook("{\"session_id\": \"test-session\"}", tempDir, tempDir,
      tempDir))
    {
      HookResult hookResult = new UserPromptSubmitHook(scope).run(scope);

      requireThat(hookResult.output().trim(), "output").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that UserPromptSubmitHook returns empty JSON when no message is present.
   */
  @Test
  public void userPromptSubmitHookReturnsEmptyJsonWhenNoMessage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook("{\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new UserPromptSubmitHook(scope).run(scope);

      requireThat(hookResult.output().trim(), "output").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- PreToolUseHook tests ---

  /**
   * Verifies that PreToolUseHook returns empty JSON for non-Bash tools.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonForNonBashTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Read\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreToolUseHook(scope).run(scope);

      requireThat(hookResult.output().trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that PreToolUseHook returns empty JSON when Bash tool has no command.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWhenNoCommand() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Bash\", \"tool_input\": {}, \"session_id\": \"test-session\"}", tempDir, tempDir,
      tempDir))
    {
      HookResult hookResult = new PreToolUseHook(scope).run(scope);

      requireThat(hookResult.output().trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that PreToolUseHook returns empty JSON for Bash tool with command.
   */
  @Test
  public void getBashPretoolReturnsEmptyJsonWithCommand() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"ls -la\"}, " +
        "\"session_id\": \"test-session\", \"cwd\": \"/workspace\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreToolUseHook(scope).run(scope);

      requireThat(hookResult.output().trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- GetBashPostOutput tests ---

  /**
   * Verifies that GetBashPostOutput returns empty JSON for non-Bash tools.
   */
  @Test
  public void getBashPosttoolReturnsEmptyJsonForNonBashTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Read\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PostBashHook().run(scope);

      requireThat(hookResult.output().trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- GetReadOutput tests ---

  /**
   * Verifies that GetReadOutput returns empty JSON for Read tool.
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForReadTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Read\", \"tool_input\": {\"file_path\": \"/tmp/test\"}, " +
        "\"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreReadHook(scope).run(scope);

      requireThat(hookResult.output().trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that GetReadOutput returns empty JSON for unsupported tools.
   */
  @Test
  public void getReadPretoolReturnsEmptyJsonForUnsupportedTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Bash\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreReadHook(scope).run(scope);

      requireThat(hookResult.output().trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- GetReadPostOutput tests ---

  /**
   * Verifies that GetReadPostOutput returns empty JSON for Grep tool.
   */
  @Test
  public void getReadPosttoolReturnsEmptyJsonForGrepTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Grep\", \"tool_input\": {}, \"tool_result\": {}, \"session_id\": \"test-session\"}",
      tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PostReadHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- PostToolUseHook tests ---

  /**
   * Verifies that PostToolUseHook returns empty JSON when given empty input.
   */
  @Test
  public void getPosttoolReturnsEmptyJsonForEmptyInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook("{\"session_id\": \"test-session\"}", tempDir, tempDir,
      tempDir))
    {
      HookResult hookResult = new PostToolUseHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that PostToolUseHook returns empty JSON with a tool name present.
   */
  @Test
  public void getPosttoolReturnsEmptyJsonWithToolName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Write\", \"tool_result\": {}, \"session_id\": \"test-session\"}", tempDir, tempDir,
      tempDir))
    {
      HookResult hookResult = new PostToolUseHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- TestClaudeHook error path tests ---

  /**
   * Verifies that TestClaudeHook with malformed JSON throws IllegalStateException.
   */
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*malformed JSON.*")
  public void hookInputWithMalformedJsonThrows()
  {
    new TestClaudeHook("not valid json {{{");
  }

  /**
   * Verifies that TestClaudeHook with blank input throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*empty.*")
  public void hookInputWithBlankInputThrows()
  {
    new TestClaudeHook("   ");
  }

  /**
   * Verifies that TestClaudeHook with empty string throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*empty.*")
  public void hookInputWithEmptyStringThrows()
  {
    new TestClaudeHook("");
  }

  /**
   * Verifies that getString with a non-string value throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Expected string for key.*count.*")
  public void hookInputGetStringWithNonStringValueThrows()
  {
    try (TestClaudeHook scope = new TestClaudeHook("{\"count\": 42, \"session_id\": \"test-session\"}"))
    {
      scope.getString("count");
    }
  }

  /**
   * Verifies that getString returns empty string for a missing key.
   */
  @Test
  public void hookInputGetStringReturnEmptyForMissingKey()
  {
    try (TestClaudeHook scope = new TestClaudeHook("{\"key\": \"value\", \"session_id\": \"test-session\"}"))
    {
      String result = scope.getString("nonexistent");
      requireThat(result, "result").isEqualTo("");
    }
  }

  /**
   * Verifies that TestClaudeHook with null payload throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*json.*")
  public void hookInputReadFromNullStreamThrows()
  {
    new TestClaudeHook((String) null);
  }

  /**
   * Verifies that TestClaudeHook with valid JSON missing session_id throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id.*")
  public void hookInputWithoutSessionIdThrows()
  {
    new TestClaudeHook("{\"tool_name\": \"Bash\"}");
  }

  // --- Strings block/empty error path tests ---

  /**
   * Verifies that Strings.block with blank reason throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*reason.*")
  public void hookOutputBlockWithBlankReasonThrows() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      Strings.block(scope, "   ");
    }
  }

  /**
   * Verifies that Strings.block with null reason throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*reason.*")
  public void hookOutputBlockWithNullReasonThrows() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      Strings.block(scope, null);
    }
  }

  /**
   * Verifies that Strings.block with null scope throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void hookOutputWithNullMapperThrows()
  {
    Strings.block(null, "reason");
  }

  /**
   * Verifies that Strings.wrapSystemReminder with blank content throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*content.*")
  public void hookOutputWrapSystemReminderWithBlankContentThrows()
  {
    Strings.wrapSystemReminder("   ");
  }

  /**
   * Verifies that scope.additionalContext with blank hookEventName throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*hookEventName.*")
  public void hookOutputAdditionalContextWithBlankEventNameThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook("{\"session_id\": \"test-session\"}", tempDir, tempDir,
      tempDir))
    {
      scope.additionalContext("", "some context");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Strings.block produces valid JSON with the decision field.
   */
  @Test
  public void hookOutputBlockProducesValidJson() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      String result = Strings.block(scope, "test reason");

      requireThat(result, "result").contains("\"decision\"").contains("\"block\"").contains("\"test reason\"");
    }
  }

  /**
   * Verifies that Strings.empty produces an empty JSON object.
   */
  @Test
  public void hookOutputEmptyProducesEmptyJson()
  {
    String result = Strings.empty();

    requireThat(result, "result").isEqualTo("{}");
  }

  // --- GetAskOutput tests ---

  /**
   * Verifies that GetAskOutput returns empty JSON for non-AskUserQuestion tools.
   */
  @Test
  public void getAskPretoolReturnsEmptyForNonAskTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Read\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that GetAskOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getAskPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {}, \"session_id\": \"test-session\"}",
      tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- PreEditHook tests ---

  /**
   * Verifies that PreEditHook returns empty JSON for non-Edit tools.
   */
  @Test
  public void getEditPretoolReturnsEmptyForNonEditTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Read\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreWriteHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that PreEditHook returns empty JSON when tool_input is empty.
   */
  @Test
  public void getEditPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Edit\", \"tool_input\": {}, \"session_id\": \"test-session\"}",
      tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreWriteHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructing a TestClaudeHook throws IllegalArgumentException when session_id is missing.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id.*")
  public void getEditPretoolThrowsOnMissingSessionId()
  {
    new TestClaudeHook("{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}}");
  }

  // --- GetTaskOutput tests ---

  /**
   * Verifies that GetTaskOutput returns empty JSON for non-Task tools.
   */
  @Test
  public void getTaskPretoolReturnsEmptyForNonTaskTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Read\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreIssueHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that GetTaskOutput returns empty JSON when tool_input is empty.
   */
  @Test
  public void getTaskPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Task\", \"tool_input\": {}, \"session_id\": \"test-session\"}",
      tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreIssueHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- EnforceWorkflowCompletion tests ---

  /**
   * Verifies that EnforceWorkflowCompletion allows edits to non-index.json files.
   */
  @Test
  public void enforceWorkflowCompletionAllowsNonStateMdFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/tmp/test.txt\"}, \"session_id\": \"test\"}",
      tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreWriteHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- WarnUnsquashedApproval tests ---

  /**
   * Verifies that WarnUnsquashedApproval allows non-approval questions.
   */
  @Test
  public void warnUnsquashedApprovalAllowsNonApprovalQuestions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"What is your name?\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- WarnApprovalWithoutRenderDiff tests ---

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows non-approval questions.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsNonApprovalQuestions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Continue?\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- EnforceApprovalBeforeMerge tests ---

  /**
   * Verifies that EnforceApprovalBeforeMerge allows non-work-merge tasks.
   */
  @Test
  public void enforceApprovalBeforeMergeAllowsNonWorkMergeTasks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Task\", \"tool_input\": {\"subagent_type\": \"cat:implement\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreIssueHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that EnforceApprovalBeforeMerge allows tasks with empty subagent_type.
   */
  @Test
  public void enforceApprovalBeforeMergeAllowsEmptySubagentType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Task\", \"tool_input\": {}, \"session_id\": \"test\"}",
      tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreIssueHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- EnforceWorktreeSafetyBeforeMerge tests ---

  /**
   * Verifies that EnforceWorktreeSafetyBeforeMerge allows non-work-merge tasks even when CWD is inside a worktree.
   */
  @Test
  public void enforceWorktreeSafetyAllowsNonMergeTasksInsideWorktree() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{\"subagent_type\": \"cat:implement\"}");
      EnforceWorktreeSafetyBeforeMerge handler =
        new EnforceWorktreeSafetyBeforeMerge();
      TaskHandler.Result result = handler.check(toolInput, "test-session",
        scope.getCatWorkPath().resolve("worktrees").resolve("2.1-my-task").toString());
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that EnforceWorktreeSafetyBeforeMerge blocks work-merge-agent spawn when CWD is inside a worktree.
   */
  @Test
  public void enforceWorktreeSafetyBlocksMergeWhenCwdInsideWorktree() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{\"subagent_type\": \"cat:work-merge-agent\"}");
      EnforceWorktreeSafetyBeforeMerge handler =
        new EnforceWorktreeSafetyBeforeMerge();
      String worktreePath =
        scope.getCatWorkPath().resolve("worktrees").resolve("2.1-my-task").toString();
      TaskHandler.Result result = handler.check(toolInput, "test-session", worktreePath);
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
      requireThat(result.reason(), "reason").contains(worktreePath);
    }
  }

  /**
   * Verifies that EnforceWorktreeSafetyBeforeMerge allows work-merge-agent spawn when CWD is not inside a worktree.
   */
  @Test
  public void enforceWorktreeSafetyAllowsMergeWhenCwdIsWorkspace() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{\"subagent_type\": \"cat:work-merge-agent\"}");
      EnforceWorktreeSafetyBeforeMerge handler =
        new EnforceWorktreeSafetyBeforeMerge();
      TaskHandler.Result result = handler.check(toolInput, "test-session", "/workspace");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that EnforceWorktreeSafetyBeforeMerge allows work-merge-agent spawn when CWD is empty.
   */
  @Test
  public void enforceWorktreeSafetyAllowsMergeWhenCwdIsEmpty() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{\"subagent_type\": \"cat:work-merge-agent\"}");
      EnforceWorktreeSafetyBeforeMerge handler =
        new EnforceWorktreeSafetyBeforeMerge();
      TaskHandler.Result result = handler.check(toolInput, "test-session", "");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that EnforceWorktreeSafetyBeforeMerge blocks when CWD is a subdirectory of a worktree.
   */
  @Test
  public void enforceWorktreeSafetyBlocksMergeWhenCwdIsSubdirOfWorktree() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{\"subagent_type\": \"cat:work-merge-agent\"}");
      EnforceWorktreeSafetyBeforeMerge handler =
        new EnforceWorktreeSafetyBeforeMerge();
      String subdirPath =
        scope.getCatWorkPath().resolve("worktrees").resolve("2.1-my-task").resolve("src/main/java").toString();
      TaskHandler.Result result = handler.check(toolInput, "test-session", subdirPath);
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
    }
  }

  // --- EnforceWorkflowCompletion handler tests ---

  /**
   * Verifies that EnforceWorkflowCompletion warns when editing index.json with status closed.
   */
  @Test
  public void enforceWorkflowCompletionWarnsOnStatusClosed() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".cat/issues/v2/v2.1/my-task/index.json\", " +
        "\"new_string\": \"{\\\"status\\\":\\\"closed\\\"}\"}");
      FileWriteHandler.Result result = new EnforceWorkflowCompletion().check(toolInput, "test");
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
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \".cat/issues/v2/v2.1/my-task/index.json\", " +
        "\"new_string\": \"{\\\"status\\\":\\\"closed\\\"}\"}");
      FileWriteHandler.Result result = new EnforceWorkflowCompletion().check(toolInput, "test");
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
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \".cat/v2/v2.1/my-task/index.json\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreWriteHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that EnforceWorkflowCompletion allows when status is not closed.
   */
  @Test
  public void enforceWorkflowCompletionAllowsNonClosedStatus() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \".cat/v2/v2.1/fix-bug-123/index.json\", " +
      "\"new_string\": \"Status: in_progress\"}, \"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreWriteHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }


  // --- WarnApprovalWithoutRenderDiff handler tests ---

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows non-approval questions.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsNonApprovalInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"What color?\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WarnApprovalWithoutRenderDiff allows when CLAUDE_PROJECT_DIR is missing.
   */
  @Test
  public void warnApprovalWithoutRenderDiffAllowsMissingProjectDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Approve?\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- WarnUnsquashedApproval handler tests ---

  /**
   * Verifies that WarnUnsquashedApproval containsApprove method works correctly.
   */
  @Test
  public void warnUnsquashedApprovalDetectsApproveInInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Ready to approve?\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      // WarnUnsquashedApproval also checks git state - outside a task worktree it allows
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WarnUnsquashedApproval detects uppercase APPROVE.
   */
  @Test
  public void warnUnsquashedApprovalDetectsUppercaseApprove() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"APPROVE changes?\"}, " +
      "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      // WarnUnsquashedApproval also checks git state - outside a task worktree it allows
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }


  /**
   * Verifies that GetAskOutput returns additionalContext when handler provides it.
   */
  @Test
  public void getAskPretoolReturnsAdditionalContextEarly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"AskUserQuestion\", \"tool_input\": {\"question\": \"Continue?\"}, " +
      "\"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreAskHook(scope).run(scope);

      String result = hookResult.output().trim();
      // WarnApprovalWithoutRenderDiff and WarnUnsquashedApproval don't inject context in this case
      // This test verifies no crash occurs
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- EnforcePluginFileIsolation handler tests ---

  /**
   * Verifies that EnforcePluginFileIsolation blocks editing plugin files on protected branches.
   */
  @Test
  public void enforcePluginFileIsolationBlocksPluginFileOnProtectedBranch() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/plugin/hooks/test.py\"}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      // On the v2.1 branch (protected), plugin files should be blocked
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files");
    }
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows editing non-plugin files.
   */
  @Test
  public void enforcePluginFileIsolationAllowsNonPluginFiles() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
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
    try (TestClaudeTool scope = new TestClaudeTool())
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
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree("{}");
      FileWriteHandler.Result result = new WarnBaseBranchEdit(scope).check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that WarnBaseBranchEdit allows editing allowed patterns.
   */
  @Test
  public void warnBaseBranchEditAllowsAllowedPatterns() throws IOException
  {
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/.claude/settings.json\"}");
      FileWriteHandler.Result result = new WarnBaseBranchEdit(scope).check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  // --- WriteEditHook dispatcher tests ---

  /**
   * Verifies that WriteEditHook returns empty JSON for non-Write/Edit tools.
   */
  @Test
  public void getWriteEditPretoolReturnsEmptyForNonWriteEditTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Read\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreWriteHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WriteEditHook does not block when tool_input is empty.
   */
  @Test
  public void getWriteEditPretoolReturnsEmptyForEmptyToolInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Write\", \"tool_input\": {}, \"session_id\": \"test-session\"}",
      tempDir, tempDir, tempDir))
    {
      HookResult hookResult = new PreWriteHook(scope).run(scope);

      String result = hookResult.output().trim();
      requireThat(result, "result").
        doesNotContain("\"decision\" : \"block\"").
        doesNotContain("\"reason\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WriteEditHook uses case-insensitive matching for tool names.
   * <p>
   * Uses a temp directory path to avoid triggering EnforcePluginFileIsolation (which blocks
   * paths containing "plugin" or "client").
   */
  @Test
  public void getWriteEditPretoolUsesCaseInsensitiveMatching() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-write-hook-");
    try
    {
      String filePath = tempDir.resolve("test.txt").toString().replace("\\", "\\\\");
      try (TestClaudeHook scope = new TestClaudeHook(
        "{\"tool_name\": \"write\", \"tool_input\": {\"file_path\": \"" + filePath + "\"}, " +
        "\"session_id\": \"test\"}", tempDir, tempDir, tempDir))
      {
        HookResult hookResult = new PreWriteHook(scope).run(scope);

        String result = hookResult.output().trim();
        requireThat(result, "result").
          doesNotContain("\"decision\" : \"block\"").
          doesNotContain("\"reason\"");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WriteEditHook accepts Edit tool_name.
   * <p>
   * Uses a temp directory path to avoid triggering EnforcePluginFileIsolation (which blocks
   * paths containing "plugin" or "client").
   */
  @Test
  public void getWriteEditPretoolAcceptsEditToolName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-write-hook-");
    try
    {
      String filePath = tempDir.resolve("test.txt").toString().replace("\\", "\\\\");
      try (TestClaudeHook scope = new TestClaudeHook(
        "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"" + filePath + "\", " +
        "\"old_string\": \"a\", \"new_string\": \"b\"}, \"session_id\": \"test\"}", tempDir, tempDir, tempDir))
      {
        HookResult hookResult = new PreWriteHook(scope).run(scope);

        String result = hookResult.output().trim();
        requireThat(result, "result").
          doesNotContain("\"decision\" : \"block\"").
          doesNotContain("\"reason\"");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- WarnBaseBranchEdit handler tests (using real git state) ---

  /**
   * Verifies that WarnBaseBranchEdit warns on target branch for non-allowed files.
   * <p>
   * Creates a temp git repo on branch 'main' and uses a file path within it
   * so that branch detection derives from the file path, not the process CWD.
   */
  @Test
  public void warnBaseBranchEditWarnsOnTargetBranch() throws IOException
  {
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempGitRepo("main");
      try
      {
        String filePath = tempDir.resolve("hooks/src/main/java/SomeNewFile.java").toString();
        JsonNode toolInput = mapper.readTree(
          "{\"file_path\": \"" + filePath.replace("\\", "\\\\") + "\"}");
        FileWriteHandler.Result result = new WarnBaseBranchEdit(scope).check(toolInput, "test");
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
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path enginePom = Path.of("pom.xml").toAbsolutePath();
      String toolInputJson = String.format("{\"file_path\": \"%s\"}", enginePom.toString().replace("\\", "\\\\"));
      JsonNode toolInput = mapper.readTree(toolInputJson);
      FileWriteHandler.Result result = new WarnBaseBranchEdit(scope).check(toolInput, "test");
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
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/plugin/skills/my-skill/SKILL.md\"}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files");
    }
  }

  /**
   * Verifies that EnforcePluginFileIsolation allows non-source paths.
   * <p>
   * Test that non-plugin, non-client files are not blocked.
   */
  @Test
  public void enforcePluginFileIsolationAllowsNonPluginPaths() throws IOException
  {
    try (TestClaudeTool scope = new TestClaudeTool())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode toolInput = mapper.readTree(
        "{\"file_path\": \"/workspace/docs/README.md\"}");
      FileWriteHandler.Result result = new EnforcePluginFileIsolation().check(toolInput, "test");
      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  // --- WriteEditHook dispatcher tests (using real handlers) ---

  /**
   * Verifies that WriteEditHook allows non-plugin file with warning.
   * <p>
   * Test that non-plugin, non-allowed files produce non-blocking output from dispatcher
   * (warning goes to stderr).
   * <p>
   * Uses a temp directory path to avoid triggering EnforcePluginFileIsolation (which blocks
   * paths containing "plugin" or "client").
   */
  @Test
  public void getWriteEditPretoolAllowsNonPluginFileWithWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-write-hook-");
    try
    {
      String filePath = tempDir.resolve("some-new-source.java").toString().replace("\\", "\\\\");
      try (TestClaudeHook scope = new TestClaudeHook(
        "{\"tool_name\": \"Write\", \"tool_input\": {\"file_path\": \"" + filePath + "\"}, " +
        "\"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
      {
        HookResult hookResult = new PreWriteHook(scope).run(scope);

        String result = hookResult.output().trim();
        requireThat(result, "result").
          doesNotContain("\"decision\" : \"block\"").
          doesNotContain("\"reason\"");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- Warning accumulation tests ---

  /**
   * Verifies that multiple warnings from different handlers are all returned in HookResult.warnings().
   */
  @Test
  public void writeEditPretoolAccumulatesMultipleWarnings() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Write\", \"tool_input\": " +
      "{\"file_path\": \"/workspace/some-file.txt\"}, \"session_id\": \"test-session-123\"}",
      tempDir, tempDir, tempDir))
    {
      FileWriteHandler handler1 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from handler 1");
      FileWriteHandler handler2 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from handler 2");

      PreWriteHook dispatcher = new PreWriteHook(List.of(handler1, handler2));

      HookResult result = dispatcher.run(scope);

      requireThat(result.warnings(), "warnings").contains("Warning from handler 1");
      requireThat(result.warnings(), "warnings").contains("Warning from handler 2");
      requireThat(result.output(), "output").doesNotContain("\"decision\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple warnings from different handlers are all returned in HookResult.warnings()
   * for Edit operations.
   */
  @Test
  public void editPretoolAccumulatesMultipleWarnings() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Edit\", \"tool_input\": " +
      "{\"file_path\": \"/workspace/test.txt\"}, \"session_id\": \"test-session-456\"}",
      tempDir, tempDir, tempDir))
    {
      FileWriteHandler handler1 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from edit handler 1");
      FileWriteHandler handler2 = (toolInput, sessionId) -> FileWriteHandler.Result.warn("Warning from edit handler 2");

      PreWriteHook dispatcher = new PreWriteHook(List.of(handler1, handler2));

      HookResult result = dispatcher.run(scope);

      requireThat(result.warnings(), "warnings").contains("Warning from edit handler 1");
      requireThat(result.warnings(), "warnings").contains("Warning from edit handler 2");
      requireThat(result.output(), "output").doesNotContain("\"decision\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple warnings from different handlers are all returned in HookResult.warnings()
   * for Task operations.
   */
  @Test
  public void taskPretoolAccumulatesMultipleWarnings() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Task\", \"tool_input\": " +
      "{\"subagent_type\": \"cat:implement\"}, \"session_id\": \"test-session-789\"}",
      tempDir, tempDir, tempDir))
    {
      TaskHandler handler1 = (toolInput, sessionId, cwd) -> TaskHandler.Result.warn("Warning from task handler 1");
      TaskHandler handler2 = (toolInput, sessionId, cwd) -> TaskHandler.Result.warn("Warning from task handler 2");

      PreIssueHook dispatcher = new PreIssueHook(List.of(handler1, handler2));

      HookResult result = dispatcher.run(scope);

      requireThat(result.warnings(), "warnings").contains("Warning from task handler 1");
      requireThat(result.warnings(), "warnings").contains("Warning from task handler 2");
      requireThat(result.output(), "output").doesNotContain("\"decision\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when one handler warns and the next blocks, only the block is output and warnings are not included.
   */
  @Test
  public void writeEditPretoolBlocksWithoutWarnings() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"tool_name\": \"Write\", \"tool_input\": " +
      "{\"file_path\": \"/workspace/blocked.txt\"}, \"session_id\": \"test-session-999\"}",
      tempDir, tempDir, tempDir))
    {
      FileWriteHandler handler1 = (toolInput, sessionId) ->
        FileWriteHandler.Result.warn("This warning should not appear");
      FileWriteHandler handler2 = (toolInput, sessionId) ->
        FileWriteHandler.Result.block("Blocked by handler 2");

      PreWriteHook dispatcher = new PreWriteHook(List.of(handler1, handler2));

      HookResult result = dispatcher.run(scope);

      requireThat(result.warnings(), "warnings").doesNotContain("This warning should not appear");
      requireThat(result.output(), "output").contains("\"decision\"");
      requireThat(result.output(), "output").contains("\"block\"");
      requireThat(result.output(), "output").contains("Blocked by handler 2");
    }
  }

  // --- BlockWorktreeCd handler tests ---

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

    runGit(tempDir, "init");
    runGit(tempDir, "config", "user.email", "test@example.com");
    runGit(tempDir, "config", "user.name", "Test User");

    Files.writeString(tempDir.resolve("README.md"), "test");
    runGit(tempDir, "add", "README.md");
    runGit(tempDir, "commit", "-m", "Initial commit");

    if (!branchName.equals("master") && !branchName.equals("main"))
    {
      runGit(tempDir, "checkout", "-b", branchName);
    }
    if (branchName.equals("main") && !getCurrentBranchName(tempDir).equals("main"))
    {
      runGit(tempDir, "branch", "-m", "main");
    }

    return tempDir;
  }

  /**
   * Runs a git command in the specified directory.
   *
   * @param directory the directory to run the command in
   * @param args the git command arguments
   */
  private void runGit(Path directory, String... args)
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
