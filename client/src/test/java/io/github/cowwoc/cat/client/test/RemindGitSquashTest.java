/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.bash.RemindGitSquash;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link RemindGitSquash}.
 */
public final class RemindGitSquashTest
{
  /**
   * Builds a hook payload with the given command embedded in tool_input.command.
   *
   * @param command the bash command
   * @return the JSON payload string
   */
  private static String buildPayload(String command)
  {
    // Escape backslashes and double-quotes for JSON embedding
    String escaped = command.replace("\\", "\\\\").replace("\"", "\\\"");
    return """
      {
        "session_id": "session1",
        "tool_name": "Bash",
        "tool_input": {"command": "%s"}
      }""".formatted(escaped);
  }

  /**
   * Verifies that {@code git reset --soft} without a path argument is blocked.
   */
  @Test
  public void gitResetSoftWithoutPathIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rgs-test-");
    try
    {
      String command = "git reset --soft HEAD~1";
      try (TestClaudeHook scope = new TestClaudeHook(buildPayload(command), tempDir, tempDir, tempDir))
      {
        RemindGitSquash handler = new RemindGitSquash();

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("/cat:git-squash");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code git -C <path> reset --soft} with a path argument is blocked.
   * This form was previously not caught because the regex required "git" to immediately precede "reset".
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void gitResetSoftWithPathArgumentIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rgs-test-");
    try
    {
      // Compute the worktree path using the same formula as getCatWorkPath()
      String worktreePath = tempDir.resolve(".cat").resolve("work").resolve("worktrees").
        resolve("2.1-my-issue").toString();
      String command = "git -C " + worktreePath + " reset --soft v2.1";
      try (TestClaudeHook scope = new TestClaudeHook(buildPayload(command), tempDir, tempDir, tempDir))
      {
        RemindGitSquash handler = new RemindGitSquash();

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("/cat:git-squash");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that normal git commits are not blocked.
   */
  @Test
  public void normalGitCommitIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rgs-test-");
    try
    {
      String command = "git commit -m \"feature: add something\"";
      try (TestClaudeHook scope = new TestClaudeHook(buildPayload(command), tempDir, tempDir, tempDir))
      {
        RemindGitSquash handler = new RemindGitSquash();

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code git rebase -i} triggers a warning suggestion.
   */
  @Test
  public void gitRebaseInteractiveTriggersWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rgs-test-");
    try
    {
      String command = "git rebase -i HEAD~3";
      try (TestClaudeHook scope = new TestClaudeHook(buildPayload(command), tempDir, tempDir, tempDir))
      {
        RemindGitSquash handler = new RemindGitSquash();

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("/cat:git-squash");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
