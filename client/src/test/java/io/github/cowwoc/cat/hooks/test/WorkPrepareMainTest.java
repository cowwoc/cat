/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.WorkPrepare;
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
 * Tests for WorkPrepare.run() CLI error path handling.
 * <p>
 * Verifies that when the CLI encounters errors (invalid trust level, missing configuration),
 * it produces valid HookOutput JSON with "decision": "block" on stdout instead of throwing exceptions
 * or producing malformed output.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class WorkPrepareMainTest
{
  /**
   * Verifies that an invalid trust level argument produces a block response on stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidTrustLevelProducesBlockResponse() throws IOException
  {
    Path tempDir = Files.createTempDirectory("work-prepare-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      // Pass an invalid trust level — this triggers the IllegalArgumentException path
      WorkPrepare.run(scope, new String[]{"--trust-level", "invalid-trust-level"}, out);

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
   * Verifies that run() with no arguments produces a valid JSON result on stdout (not an exception).
   * <p>
   * With no arguments, execute() runs with default parameters and returns a JSON response
   * (e.g., with status "ERROR" when .cat is not configured). The test verifies that
   * run() never propagates exceptions — it always writes to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noArgsProducesJsonOutputOnStdout() throws IOException
  {
    Path tempDir = Files.createTempDirectory("work-prepare-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      // No arguments — execute() returns a JSON result (status=ERROR for missing config)
      WorkPrepare.run(scope, new String[]{}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      // Output must be non-blank JSON (either a business result or a block response)
      requireThat(output, "output").isNotBlank();
      // Must be parseable as JSON — not a raw exception stack trace
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);
      requireThat(json, "json").isNotNull();
      // Must be an object with at least one field (business result or block response)
      requireThat(json.isObject(), "json.isObject()").isTrue();
      // Must contain a "status" field indicating a business result
      requireThat(json.get("status").asString(), "status").isNotBlank();
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
    WorkPrepare.run(null, new String[]{},
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
    Path tempDir = Files.createTempDirectory("work-prepare-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      WorkPrepare.run(scope, null,
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
    Path tempDir = Files.createTempDirectory("work-prepare-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      WorkPrepare.run(scope, new String[]{}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
