/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.TokenCounter;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for TokenCounter.run() CLI error path handling.
 */
public class TokenCounterMainTest
{
  /**
   * Verifies that invoking run() with no arguments throws IllegalArgumentException with usage information.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Usage.*TokenCounter.*")
  public void noArgsThrowsExceptionWithUsage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("token-counter-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      TokenCounter.run(scope, new String[]{}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() counts tokens in a valid file and produces JSON output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void validFileProducesTokenCount() throws IOException
  {
    Path tempDir = Files.createTempDirectory("token-counter-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path testFile = tempDir.resolve("test.md");
      Files.writeString(testFile, "Hello world, this is a test file.", StandardCharsets.UTF_8);

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      TokenCounter.run(scope, new String[]{testFile.toString()}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);

      requireThat(json.has(testFile.toString()), "hasFileKey").isTrue();
      int tokenCount = json.get(testFile.toString()).asInt();
      requireThat(tokenCount, "tokenCount").isGreaterThan(0);
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
    Path tempDir = Files.createTempDirectory("token-counter-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      TokenCounter.run(scope, null,
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
    Path tempDir = Files.createTempDirectory("token-counter-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      TokenCounter.run(scope, new String[]{"dummy"}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
