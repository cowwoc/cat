/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookResult;
import io.github.cowwoc.cat.claude.hook.PreToolUseHook;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PreToolUseHook} CLI entry point via {@code run()}.
 */
public class PreToolUseHookMainTest
{
  /**
   * Verifies that run() returns empty JSON for a non-Bash tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonBashToolReturnsEmptyJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-tool-use-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Read",
          "tool_input": {}
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PreToolUseHook(scope).run(scope);

        requireThat(result.output().strip(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() returns empty JSON when no command is specified.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyCommandReturnsEmptyJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-tool-use-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Bash",
          "tool_input": {"command": ""}
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PreToolUseHook(scope).run(scope);

        requireThat(result.output().strip(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() processes a valid Bash command and returns a JSON response.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void bashCommandReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-tool-use-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Bash",
          "tool_input": {"command": "echo hello"}
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PreToolUseHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
