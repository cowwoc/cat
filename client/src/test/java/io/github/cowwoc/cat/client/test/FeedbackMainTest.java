/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.util.Feedback;
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
 * Tests for Feedback.run() CLI error path handling.
 */
public class FeedbackMainTest
{
  /**
   * Verifies that invoking run() with no arguments produces a block response with usage information.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noArgsProducesBlockResponseWithUsage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("feedback-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      Feedback.run(scope, new String[]{}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);

      requireThat(json.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(json.get("reason").asString(), "reason").contains("Usage");
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
    Path tempDir = Files.createTempDirectory("feedback-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Feedback.run(scope, null,
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
    Path tempDir = Files.createTempDirectory("feedback-main-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Feedback.run(scope, new String[]{"dummy"}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
