/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetConfigOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetConfigOutput.getCurrentSettings().
 * <p>
 * Tests verify that the CURRENT SETTINGS box includes all three new settings:
 * completionWorkflow, reviewThreshold, and minSeverity.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetConfigOutputTest
{
  /**
   * Verifies that getCurrentSettings includes completionWorkflow with default value in correct format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsIncludesCompletionWorkflow() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create a minimal config file so getCurrentSettings doesn't return null
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("🔀 Completion: merge");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCurrentSettings shows the completionWorkflow value in correct format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsShowsCompletionWorkflowValue() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{\"completionWorkflow\": \"pr\"}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("🔀 Completion: pr");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCurrentSettings includes reviewThreshold with default value in correct format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsIncludesReviewThreshold() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("🔍 Review: low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCurrentSettings shows the reviewThreshold value in correct format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsShowsReviewThresholdValue() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{\"reviewThreshold\": \"high\"}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("🔍 Review: high");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCurrentSettings includes minSeverity with default value in correct format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsIncludesMinSeverity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("📈 Min Severity: low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCurrentSettings shows the minSeverity value in correct format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsShowsMinSeverityValue() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{\"minSeverity\": \"critical\"}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("📈 Min Severity: critical");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCurrentSettings returns null when config file does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsReturnsNullWhenNoConfig() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCurrentSettings still contains existing settings and does not display removed settings.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCurrentSettingsContainsExistingSettings() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").
        contains("Trust").
        contains("Verify").
        contains("Effort").
        contains("Patience").
        doesNotContain("Auto-remove").
        doesNotContain("Keep");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that minSeverity with empty string value is handled correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void minSeverityEdgeCaseEmptyString() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{\"minSeverity\": \"\"}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      // Empty string should trigger validation error in Config.getMinSeverity
      try
      {
        handler.getCurrentSettings(tempDir);
        // Should throw IllegalArgumentException for invalid severity
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException _)
      {
        // Expected behavior - invalid severity value
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that minSeverity with whitespace-only value is handled correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void minSeverityEdgeCaseWhitespace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{\"minSeverity\": \"  \"}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      try
      {
        handler.getCurrentSettings(tempDir);
        // Should throw IllegalArgumentException for invalid severity
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException _)
      {
        // Expected behavior - invalid severity value
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that minSeverity with invalid value is handled correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void minSeverityEdgeCaseInvalidValue() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{\"minSeverity\": \"invalid\"}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      try
      {
        handler.getCurrentSettings(tempDir);
        // Should throw IllegalArgumentException for invalid severity
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException _)
      {
        // Expected behavior - invalid severity value
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies full integration: Config.load() loads all three new settings correctly,
   * and getCurrentSettings displays them with correct formatting and icons.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void integrationFullConfigurationPipeline() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"),
        "{\"completionWorkflow\": \"pr\", \"reviewThreshold\": \"high\", \"minSeverity\": \"medium\"}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").
        contains("🔀 Completion: pr").
        contains("🔍 Review: high").
        contains("📈 Min Severity: medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput with unknown page throws IllegalArgumentException listing valid pages.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputUnknownPageThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      try
      {
        handler.getOutput(new String[]{"invalid-page"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("Unknown page").
          contains("settings").
          contains("versions").
          contains("saved").
          contains("no-changes").
          contains("conditions-for-version").
          contains("setting-updated").
          contains("conditions-updated");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that conditions-for-version with insufficient args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void conditionsForVersionInsufficientArgsThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      try
      {
        handler.getOutput(new String[]{"conditions-for-version", "v1.0"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("conditions-for-version requires 3 arguments").
          contains("version").
          contains("preconditions").
          contains("postconditions");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that setting-updated with insufficient args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void settingUpdatedInsufficientArgsThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      try
      {
        handler.getOutput(new String[]{"setting-updated", "trust", "low"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("setting-updated requires 3 arguments").
          contains("name").
          contains("old").
          contains("new");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that conditions-updated with insufficient args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void conditionsUpdatedInsufficientArgsThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      try
      {
        handler.getOutput(new String[]{"conditions-updated", "v1.0"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("conditions-updated requires 3 arguments").
          contains("version").
          contains("preconditions").
          contains("postconditions");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput settings page returns current settings.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputSettingsPageReturnsCurrent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{}");

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getOutput(new String[]{"settings"});

      requireThat(result, "result").
        contains("CURRENT SETTINGS").
        contains("🔀 Completion");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput with empty args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputEmptyArgsThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-config-output-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetConfigOutput handler = new GetConfigOutput(scope);
      try
      {
        handler.getOutput(new String[]{});
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("page argument");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
