/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.StatusAlignmentValidator;
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
 * Tests for StatusAlignmentValidator.run() CLI handling.
 */
public class StatusAlignmentValidatorMainTest
{
  /**
   * Verifies that run() reads from stdin and produces validation output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyInputProducesOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("status-alignment-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayInputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      StatusAlignmentValidator.run(scope, new String[]{}, in, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null input stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*in.*")
  public void nullInThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("status-alignment-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      StatusAlignmentValidator.run(scope, new String[]{}, null,
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
    Path tempDir = Files.createTempDirectory("status-alignment-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      StatusAlignmentValidator.run(scope, new String[]{},
        new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
