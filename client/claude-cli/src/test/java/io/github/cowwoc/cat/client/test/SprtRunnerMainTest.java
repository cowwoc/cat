/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import io.github.cowwoc.cat.tool.skills.ModelIdResolver;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link SprtRunner#run(CliTool, String[], PrintStream)} CLI error path handling.
 */
public class SprtRunnerMainTest
{
  /**
   * Verifies that invoking run() with no arguments throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*no command specified.*")
  public void noArgsThrowsException() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("sprt-runner-main-test-");
    try (CliTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SprtRunner.run(scope, new String[]{}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null args.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void nullArgsThrowsException() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("sprt-runner-main-test-");
    try (CliTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner.run(scope, null,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null output stream.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void nullOutThrowsException() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("sprt-runner-main-test-");
    try (CliTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner.run(scope, new String[]{}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that codex-like engine invocation proceeds to command validation when Claude is
   * unavailable, instead of failing on version detection.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*unknown command: unknown-command.*")
  public void codexEngineWithoutClaudeUsesBusiness() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("sprt-runner-main-test-");
    String originalValue = System.getProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY);
    System.setProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY,
      "cat-missing-claude-binary-for-test");
    try (CliTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SprtRunner.run(scope, new String[]{"unknown-command"}, out);
    }
    finally
    {
      restoreProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY, originalValue);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Restores a system property to its original value.
   *
   * @param key the property name
   * @param value the original value, or {@code null} if it was unset
   */
  private static void restoreProperty(String key, String value)
  {
    if (value == null)
      System.clearProperty(key);
    else
      System.setProperty(key, value);
  }
}
