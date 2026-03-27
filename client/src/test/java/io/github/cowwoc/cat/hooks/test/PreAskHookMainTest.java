/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.PreAskHook;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PreAskHook} CLI entry point via both {@code run()} and the static
 * {@code run(scope, args, in, out)} method.
 */
public class PreAskHookMainTest
{
  /**
   * Verifies that run() returns empty JSON for a non-AskUserQuestion tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonAskToolReturnsEmptyJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-ask-hook-main-test-");
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
        HookResult result = new PreAskHook(scope).run(scope);

        requireThat(result.output().strip(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the static run() method produces output for an AskUserQuestion tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void staticRunProducesOutputForAskTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-ask-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "tool_name": "AskUserQuestion",
          "tool_input": {"question": "Continue?"}
        }""";
      try (ClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        PreAskHook.run(scope, new String[]{}, new ByteArrayInputStream(new byte[0]), out);

        String output = buffer.toString(StandardCharsets.UTF_8).strip();
        requireThat(output, "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the static run() throws NullPointerException for null args.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void staticRunNullArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-ask-hook-main-test-");
    try (ClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      PreAskHook.run(scope, null, new ByteArrayInputStream(new byte[0]),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the static run() throws NullPointerException for null input stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*in.*")
  public void staticRunNullInThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-ask-hook-main-test-");
    try (ClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      PreAskHook.run(scope, new String[]{}, null,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the static run() throws NullPointerException for null output stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void staticRunNullOutThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("pre-ask-hook-main-test-");
    try (ClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      PreAskHook.run(scope, new String[]{}, new ByteArrayInputStream(new byte[0]), null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
