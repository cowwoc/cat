/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.RecordLearning;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for RecordLearning.run() CLI error path handling.
 * <p>
 * Verifies that when the CLI encounters errors (empty stdin, invalid JSON),
 * it produces valid HookOutput JSON with "decision": "block" on stdout instead of throwing exceptions
 * or producing malformed output.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class RecordLearningMainTest
{
  /**
   * Verifies that empty stdin produces a block response on stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyStdinProducesBlockResponse() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      InputStream emptyInput = new ByteArrayInputStream(new byte[0]);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      RecordLearning.run(scope, emptyInput, out);

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
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that invalid JSON on stdin produces a block response on stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidJsonOnStdinProducesBlockResponse() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      InputStream invalidJson = new ByteArrayInputStream(
        "not valid json at all".getBytes(StandardCharsets.UTF_8));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      RecordLearning.run(scope, invalidJson, out);

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
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void nullScopeThrowsException()
  {
    RecordLearning.run(null, new ByteArrayInputStream(new byte[0]),
      new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
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
    Path tempDir = Files.createTempDirectory("record-learning-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      RecordLearning.run(scope, null,
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
    Path tempDir = Files.createTempDirectory("record-learning-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      RecordLearning.run(scope, new ByteArrayInputStream(new byte[0]), null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
