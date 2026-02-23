/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.task.EnforceApprovalBeforeMerge;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link EnforceApprovalBeforeMerge} direct approval message detection.
 * <p>
 * Tests verify that direct user messages containing "approve and merge" (or similar phrases)
 * are accepted as valid approval, in addition to the AskUserQuestion wizard flow.
 */
public final class EnforceApprovalBeforeMergeTest
{
  private static final String SESSION_ID = "test-session";

  /**
   * Writes a cat-config.json with the given trust level to the project directory.
   *
   * @param projectDir the project root directory
   * @param trust the trust level ("high", "medium", or "low")
   * @throws IOException if the config file cannot be written
   */
  private static void writeCatConfig(Path projectDir, String trust) throws IOException
  {
    Path catDir = projectDir.resolve(".claude").resolve("cat");
    Files.createDirectories(catDir);
    Files.writeString(catDir.resolve("cat-config.json"), """
      {"trust": "%s"}
      """.formatted(trust));
  }

  /**
   * Writes a session JSONL file with the given content.
   *
   * @param projectDir the project root directory (also the config dir in TestJvmScope)
   * @param sessionId the session ID
   * @param content the JSONL content to write
   * @throws IOException if the session file cannot be written
   */
  private static void writeSessionFile(Path projectDir, String sessionId, String content)
    throws IOException
  {
    Path sessionDir = projectDir.resolve("projects").resolve("-workspace");
    Files.createDirectories(sessionDir);
    Files.writeString(sessionDir.resolve(sessionId + ".jsonl"), content);
  }

  /**
   * Creates a tool input JSON node for spawning a cat:work-merge subagent.
   *
   * @param mapper the JSON mapper
   * @return the tool input JSON node
   * @throws IOException if the JSON cannot be parsed
   */
  private static JsonNode createMergeToolInput(JsonMapper mapper) throws IOException
  {
    return mapper.readTree("""
      {"subagent_type": "cat:work-merge"}""");
  }

  /**
   * Verifies that a user message containing "approve and merge" is accepted as direct approval.
   * <p>
   * The hook must allow the merge when a user message with "approve and merge" is found in
   * the session history, without requiring the AskUserQuestion wizard flow.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void approveAndMergeInUserMessageAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"approve and merge"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a user message containing "approved merge" is accepted as direct approval.
   * <p>
   * Variations of "approve and merge" such as "approved merge" must also be recognized.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void approvedMergeInUserMessageAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"approved merge"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a user message containing only "approve" (without "merge") is NOT accepted.
   * <p>
   * The word "approve" alone is too common in other contexts; both keywords must appear together.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void approveAloneInUserMessageBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"I approve the design"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that "approve and merge" in an assistant message (not user) is NOT accepted.
   * <p>
   * Direct approval detection must only recognize user messages, not assistant or system messages.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void approveAndMergeInAssistantMessageBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"assistant","message":{"content":[{"type":"text","text":"approve and merge"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the AskUserQuestion flow still works as an approval path.
   * <p>
   * The existing wizard-based approval must continue to work alongside direct message approval.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void askUserQuestionApprovalFlowAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"assistant","content":"askuserquestion approve and merge options"}
        {"type":"assistant","content":"user_approval confirmed"}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that trust="high" always allows merge without any approval check.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void highTrustAlwaysAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "high");

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler allows non-merge subagent types without any approval check.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonMergeSubagentTypeAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = mapper.readTree("""
        {"subagent_type": "cat:work-execute"}""");

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that "approve merge" (without "and") in a user message is accepted as direct approval.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void approveMergeWithoutAndInUserMessageAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"approve merge"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no session file results in a block when trust is medium.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void missingSessionFileBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      // No session file written

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that trust="low" with no approval message blocks the merge.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lowTrustWithoutApprovalBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "low");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"looks good to me"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that "merge and approve" (reverse keyword order) is still accepted as direct approval.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void reverseKeywordOrderAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"merge and approve"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a negation like "don't approve the merge" is NOT accepted as approval.
   * <p>
   * Only specific approval phrases should match, not arbitrary text containing both keywords.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void negationWithBothKeywordsBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"don't approve the merge yet"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a discussion message mentioning both keywords is NOT accepted as approval.
   * <p>
   * Messages like "Can you approve the merge request on GitHub?" are not approval intents.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void discussionWithBothKeywordsBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"Can you approve the merge request on GitHub?"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that "Approve and merge" with leading capital is accepted.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void capitalizedApproveAndMergeAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-approval-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"Approve and merge"}]}}
        """);

      EnforceApprovalBeforeMerge handler = new EnforceApprovalBeforeMerge(scope);
      JsonNode toolInput = createMergeToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
