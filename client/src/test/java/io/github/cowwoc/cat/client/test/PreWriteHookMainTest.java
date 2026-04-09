/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookResult;
import io.github.cowwoc.cat.claude.hook.PreWriteHook;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PreWriteHook} CLI entry point via {@code run()}.
 */
public class PreWriteHookMainTest
{
  /**
   * Verifies that run() returns empty JSON for a non-Write/Edit tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonWriteToolReturnsEmptyJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-write-hook-main-test-");
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
        HookResult result = new PreWriteHook(scope).run(scope);

        requireThat(result.output().strip(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() returns a valid JSON response for a Write tool with an empty path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void writeToolWithEmptyPathReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-write-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Write",
          "tool_input": {"file_path": "", "content": "test"}
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PreWriteHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() processes a Write tool with a non-plugin file path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void writeToolWithNonPluginPathReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-write-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "Write",
          "tool_input": {"file_path": "%s/test.txt", "content": "hello"}
        }""".formatted(tempDir.toString().replace("\\", "\\\\"));
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new PreWriteHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
