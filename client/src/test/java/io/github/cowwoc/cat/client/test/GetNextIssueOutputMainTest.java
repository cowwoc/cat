/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.skills.GetNextIssueOutput;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link GetNextIssueOutput#run(JvmScope, String[], PrintStream)} CLI error path handling.
 */
public class GetNextIssueOutputMainTest
{
  /**
   * Verifies that invoking run() with no arguments throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Usage.*")
  public void noArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-next-issue-output-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      GetNextIssueOutput.run(scope, new String[]{}, out);
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
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void nullArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-next-issue-output-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetNextIssueOutput.run(scope, null,
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
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void nullOutThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-next-issue-output-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetNextIssueOutput.run(scope, new String[]{}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unknown flag throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown flag.*--bogus.*")
  public void unknownArgThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-next-issue-output-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      GetNextIssueOutput.run(scope, new String[]{"--bogus", "value"}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that omitting --completed-issue throws IllegalArgumentException with Usage message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Usage.*")
  public void missingCompletedIssueThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-next-issue-output-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      GetNextIssueOutput.run(scope,
        new String[]{"--target-branch", "main", "--session-id", "test-session",
          "--project-dir", tempDir.toString()},
        out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that providing all four required flags produces a non-blank box, even when the project has no
   * issues (graceful fallback to scope-complete box).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void allRequiredFlagsHappyPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-next-issue-output-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      GetNextIssueOutput.run(scope,
        new String[]{"--completed-issue", "2.1-my-issue", "--target-branch", "main",
          "--session-id", "test-session", "--project-dir", tempDir.toString()},
        out);
      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the --exclude-pattern flag is recognized and does not throw an exception.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void excludePatternFlagIsRecognized() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-next-issue-output-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      GetNextIssueOutput.run(scope,
        new String[]{"--completed-issue", "2.1-my-issue", "--target-branch", "main",
          "--session-id", "test-session", "--project-dir", tempDir.toString(),
          "--exclude-pattern", "skip-*"},
        out);
      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
