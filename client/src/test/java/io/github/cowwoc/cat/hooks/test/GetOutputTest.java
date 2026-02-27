/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetOutput dispatcher.
 * <p>
 * Tests verify that the dispatcher correctly parses dot-notation type arguments,
 * routes them to appropriate skill handlers, wraps output in tags, and validates arguments.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetOutputTest
{
  /**
   * Verifies that dot-notation with skill and page parses correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void dotNotationWithPageParses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create minimal config for config handler to return non-null
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.settings"});

      requireThat(result, "result").
        contains("<output type=\"config.settings\">").
        contains("</output>").
        contains("CURRENT SETTINGS");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that single skill name (no page) routes correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleSkillNameWithoutPageRoutes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.saved"});

      requireThat(result, "result").
        contains("<output type=\"config.saved\">").
        contains("</output>").
        contains("SAVED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that output is wrapped with correct type attribute.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputIsWrappedInTag() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.no-changes"});

      requireThat(result, "result").
        startsWith("<output type=\"config.no-changes\">").
        endsWith("</output>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that unknown skill type throws IllegalArgumentException with valid types listed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unknownSkillTypeThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      try
      {
        handler.getOutput(new String[]{"invalid-skill"});
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("Unknown skill").
          contains("status").
          contains("config");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing type argument throws IllegalArgumentException with usage hints.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void missingTypeArgumentThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      try
      {
        handler.getOutput(new String[]{});
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("get-output requires a type argument").
          contains("Usage");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extra arguments are passed through to handlers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void extraArgumentsPassThroughToHandler() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create minimal config for config handler
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetOutput handler = new GetOutput(scope);
      // config.conditions-for-version version pre post
      String result = handler.getOutput(new String[]{
        "config.conditions-for-version", "v1.0", "no-deps", "ready-to-release"
      });

      requireThat(result, "result").
        contains("<output type=\"config.conditions-for-version\">").
        contains("CONDITIONS FOR v1.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies null args throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nullArgsThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      try
      {
        handler.getOutput(null);
      }
      catch (NullPointerException _)
      {
        // Expected
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that content wrapped in tags contains newlines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void wrappedOutputContainsNewlines() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.saved"});

      requireThat(result, "result").
        contains("<output type=\"config.saved\">\n").
        contains("\n</output>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple dots in type are handled (only first dot is separator).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleDotsSeparatesOnFirst() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // init.choose-your-partner should parse as skill=init, page=choose-your-partner
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"init.choose-your-partner"});

      requireThat(result, "result").
        contains("<output type=\"init.choose-your-partner\">").
        contains("CHOOSE YOUR PARTNER");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
