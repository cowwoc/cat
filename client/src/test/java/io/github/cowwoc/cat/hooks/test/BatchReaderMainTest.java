/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.BatchReader;
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
 * Tests for BatchReader.run() CLI error path handling.
 * <p>
 * Verifies that when the CLI encounters errors (missing args, invalid flags),
 * it produces valid HookOutput JSON with "decision": "block" on stdout instead of throwing exceptions
 * or producing malformed output.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class BatchReaderMainTest
{
  /**
   * Verifies that invoking run() with no arguments produces a block response with usage information.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noArgsProducesBlockResponseWithUsage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("batch-reader-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      BatchReader.run(scope, new String[]{}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);

      requireThat(json.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(json.get("reason").asString(), "reason").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an invalid --max-files value produces a block response on stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidMaxFilesProducesBlockResponse() throws IOException
  {
    Path tempDir = Files.createTempDirectory("batch-reader-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      // Pass an invalid --max-files value — triggers IllegalArgumentException path
      BatchReader.run(scope, new String[]{"**/*.java", "--max-files", "not-a-number"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);

      requireThat(json.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(json.get("reason").asString(), "reason").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void nullScopeThrowsException()
  {
    BatchReader.run(null, new String[]{},
      new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
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
    Path tempDir = Files.createTempDirectory("batch-reader-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      BatchReader.run(scope, null,
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
    Path tempDir = Files.createTempDirectory("batch-reader-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      BatchReader.run(scope, new String[]{}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
