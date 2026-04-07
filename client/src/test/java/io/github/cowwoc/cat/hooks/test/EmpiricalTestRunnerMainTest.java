/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link EmpiricalTestRunner#run(ClaudeTool, String[], PrintStream)} CLI error path handling.
 */
public class EmpiricalTestRunnerMainTest
{
  /**
   * Verifies that invoking run() with --help produces usage output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpFlagProducesUsageOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-runner-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      int exitCode = EmpiricalTestRunner.run(scope, new String[]{"--help"}, out);
      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").contains("Usage");
      requireThat(exitCode, "exitCode").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invoking run() with no --config throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*--config.*")
  public void missingConfigThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-runner-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      EmpiricalTestRunner.run(scope, new String[]{"--trials", "1"}, out);
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
    Path tempDir = Files.createTempDirectory("empirical-test-runner-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner.run(scope, null,
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
    Path tempDir = Files.createTempDirectory("empirical-test-runner-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner.run(scope, new String[]{}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unknown flag produces an IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown argument.*--bogus.*")
  public void unknownArgThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-runner-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      EmpiricalTestRunner.run(scope, new String[]{"--bogus", "value"}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that -h produces the same usage output as --help.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void shortHelpFlagProducesUsageOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-runner-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      int exitCode = EmpiricalTestRunner.run(scope, new String[]{"-h"}, out);
      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").contains("Usage");
      requireThat(exitCode, "exitCode").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that passing a non-integer value to --trials throws NumberFormatException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NumberFormatException.class)
  public void trialsNonIntegerThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-runner-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner.run(scope, new String[]{"--trials", "not-a-number"},
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
