/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookResult;
import io.github.cowwoc.cat.claude.hook.PostToolUseFailureHook;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PostToolUseFailureHook} CLI entry point via {@code run()}.
 */
public class PostToolUseFailureHookMainTest
{
  /**
   * Verifies that run() returns a valid JSON response for empty tool input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyToolInputReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("post-tool-use-failure-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Bash",
          "tool_input": {}
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PostToolUseFailureHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() returns a valid JSON response with a tool error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toolErrorReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("post-tool-use-failure-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Bash",
          "tool_input": {"command": "false"},
          "tool_error": "Command failed with exit code 1"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PostToolUseFailureHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
