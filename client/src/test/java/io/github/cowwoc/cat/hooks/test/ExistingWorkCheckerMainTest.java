/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.ExistingWorkChecker;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ExistingWorkChecker.run() CLI error path handling.
 */
public class ExistingWorkCheckerMainTest
{
  /**
   * Verifies that invoking run() with no arguments produces an error on stderr.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noArgsProducesError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("existing-work-checker-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
      ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
      PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

      boolean success = ExistingWorkChecker.run(scope, new String[]{}, out, err);

      requireThat(success, "success").isFalse();
      String errOutput = errBuffer.toString(StandardCharsets.UTF_8);
      requireThat(errOutput, "errOutput").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invoking run() with only --worktree but no --target-branch produces an error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void missingTargetBranchProducesError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("existing-work-checker-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
      ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
      PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

      boolean success = ExistingWorkChecker.run(scope,
        new String[]{"--worktree", tempDir.toString()}, out, err);

      requireThat(success, "success").isFalse();
      String errOutput = errBuffer.toString(StandardCharsets.UTF_8);
      requireThat(errOutput, "errOutput").contains("--target-branch");
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
    Path tempDir = Files.createTempDirectory("existing-work-checker-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ExistingWorkChecker.run(scope, new String[]{"dummy"}, null,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unknown flag produces an error on stderr and returns false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unknownArgProducesError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("existing-work-checker-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
      ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
      PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

      boolean success = ExistingWorkChecker.run(scope,
        new String[]{"--bogus", "value"}, out, err);

      requireThat(success, "success").isFalse();
      String errOutput = errBuffer.toString(StandardCharsets.UTF_8);
      requireThat(errOutput, "errOutput").contains("Unknown argument");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
