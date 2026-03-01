/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.BlockUnauthorizedMergeCleanup;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link BlockUnauthorizedMergeCleanup}.
 */
public final class BlockUnauthorizedMergeCleanupTest
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
   * Verifies that commands not invoking merge-and-cleanup are allowed without any checks.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void unrelatedCommandIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "medium");
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);

      BashHandler.Result result = handler.check("git status", "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that trust="high" allows merge-and-cleanup invocation without approval check.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void highTrustAllowsWithoutApproval() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "high");
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);
      String command = "/path/to/merge-and-cleanup session-id issue-id";

      BashHandler.Result result = handler.check(command, "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that trust="medium" without approval blocks merge-and-cleanup.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mediumTrustWithoutApprovalBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"looks good"}]}}
        """);
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);
      String command = "/path/to/merge-and-cleanup session-id issue-id";

      BashHandler.Result result = handler.check(command, "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that trust="medium" with approval allows merge-and-cleanup.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mediumTrustWithApprovalAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"approve and merge"}]}}
        """);
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);
      String command = "/path/to/merge-and-cleanup session-id issue-id";

      BashHandler.Result result = handler.check(command, "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that trust="low" without approval blocks merge-and-cleanup.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lowTrustWithoutApprovalBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "low");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"looks good"}]}}
        """);
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);
      String command = "merge-and-cleanup session-id issue-id";

      BashHandler.Result result = handler.check(command, "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an AskUserQuestion wizard approval allows merge-and-cleanup.
   * <p>
   * When the user selects "Approve and merge" in the wizard flow, the session contains
   * both an AskUserQuestion invocation and a user tool_result with the approval text.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void wizardApprovalAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "medium");
      String assistantLine =
        "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\",\"content\":" +
        "[{\"type\":\"tool_use\",\"id\":\"tu1\",\"name\":\"AskUserQuestion\",\"input\":" +
        "{\"question\":\"Approve merge?\"}}]}}";
      String userLine =
        "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":" +
        "[{\"type\":\"tool_result\",\"tool_use_id\":\"tu1\"," +
        "\"content\":\"User answered: Approve and merge\"}]}}";
      writeSessionFile(tempDir, SESSION_ID, assistantLine + "\n" + userLine + "\n");
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);
      String command = "merge-and-cleanup session-id issue-id";

      BashHandler.Result result = handler.check(command, "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the block message directs the agent to use the correct approval gate.
   * <p>
   * The error message must mention Step 8 and the correct path through the work-with-issue workflow.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockMessageMentionsApprovalGate() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "medium");
      writeSessionFile(tempDir, SESSION_ID, """
        {"type":"user","message":{"content":[{"type":"text","text":"looks good"}]}}
        """);
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);
      String command = "merge-and-cleanup session-id issue-id";

      BashHandler.Result result = handler.check(command, "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Step 8");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing session file blocks merge-and-cleanup for non-high trust.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void missingSessionFileBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("block-unauthorized-merge-cleanup-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      writeCatConfig(tempDir, "medium");
      // No session file written intentionally
      BlockUnauthorizedMergeCleanup handler = new BlockUnauthorizedMergeCleanup(scope);
      String command = "merge-and-cleanup session-id issue-id";

      BashHandler.Result result = handler.check(command, "/workspace", null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
