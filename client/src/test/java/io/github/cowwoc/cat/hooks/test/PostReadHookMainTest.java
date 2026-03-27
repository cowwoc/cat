/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.PostReadHook;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PostReadHook} CLI entry point via {@code run()}.
 */
public class PostReadHookMainTest
{
  /**
   * Verifies that run() returns a valid JSON response for a Grep tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void grepToolReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("post-read-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Grep",
          "tool_input": {"pattern": "test"}
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PostReadHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() returns empty JSON for a non-read tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonReadToolReturnsEmptyJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("post-read-hook-main-test-");
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
        HookResult result = new PostReadHook(scope).run(scope);

        requireThat(result.output().strip(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
