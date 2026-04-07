/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.MarkdownWrapper;
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
 * Tests for MarkdownWrapper.run() CLI handling.
 */
public class MarkdownWrapperMainTest
{
  /**
   * Verifies that run() wraps content from stdin and writes to stdout when no file argument is given.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void stdinContentIsWrappedToStdout() throws IOException
  {
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      String input = "This is a short line.";
      ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      MarkdownWrapper.run(scope, new String[]{}, in, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("This is a short line.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws IllegalArgumentException for an invalid width argument.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void invalidWidthThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      MarkdownWrapper.run(scope, new String[]{"--width", "abc"}, in, out);
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
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      MarkdownWrapper.run(scope, null,
        new ByteArrayInputStream(new byte[0]),
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
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      MarkdownWrapper.run(scope, new String[]{},
        new ByteArrayInputStream(new byte[0]), null);
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
    expectedExceptionsMessageRegExp = ".*Unknown argument.*--bogus.*")
  public void unknownArgThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      MarkdownWrapper.run(scope, new String[]{"--bogus"},
        new ByteArrayInputStream(new byte[0]), out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException when the input stream is null.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*in.*")
  public void nullInThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      MarkdownWrapper.run(scope, new String[]{}, null, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() with --width 20 wraps stdin content so no output line exceeds 20 characters.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void customWidthIsApplied() throws IOException
  {
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      String input = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12 word13";
      ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      MarkdownWrapper.run(scope, new String[]{"--width", "20"}, in, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").isNotBlank();
      for (String line : output.split("\n"))
        requireThat(line.length(), "line.length()").isLessThanOrEqualTo(20);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() with a file path argument wraps the file in place.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void filePathArgWrapsFileInPlace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("markdown-wrapper-main-test-");
    Path tempFile = Files.createTempFile("markdown-wrapper-test-", ".md");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      String longContent = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10";
      Files.writeString(tempFile, longContent, StandardCharsets.UTF_8);

      ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      MarkdownWrapper.run(scope, new String[]{tempFile.toString(), "--width", "20"}, in, out);

      String newContent = Files.readString(tempFile, StandardCharsets.UTF_8);
      requireThat(newContent, "newContent").isNotEqualTo(longContent);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
