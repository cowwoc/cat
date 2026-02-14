/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetAddOutput;
import io.github.cowwoc.cat.hooks.skills.GetConfigOutput;
import io.github.cowwoc.cat.hooks.skills.ItemType;
import io.github.cowwoc.cat.hooks.skills.TaskType;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for handler output generators.
 * <p>
 * Tests verify that handler output classes generate correct display boxes
 * and formatted output for various skill handlers.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class HandlerOutputTest
{
  /**
   * Verifies that GetAddOutput generates issue display with correct content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputGeneratesIssueDisplay() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "parse-tokens",
        "2.0",
        TaskType.FEATURE,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("parse-tokens").
        contains("Version: 2.0").contains("Feature");
    }
  }

  /**
   * Verifies that GetAddOutput includes dependencies in issue display.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputIncludesDependencies() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "test-issue",
        "2.1",
        TaskType.BUGFIX,
        List.of("dep1", "dep2"),
        "",
        "");

      requireThat(result, "result").contains("test-issue").contains("dep1").contains("dep2");
    }
  }

  /**
   * Verifies that GetAddOutput shows None for empty dependencies.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputShowsNoneForEmptyDependencies() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "test-issue",
        "2.1",
        TaskType.FEATURE,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("Dependencies: None");
    }
  }

  /**
   * Verifies that GetAddOutput generates version display.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputGeneratesVersionDisplay() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "v3.0",
        "3.0",
        null,
        List.of(),
        "Parent: v2.1",
        "/path/to/version");

      requireThat(result, "result").contains("v3.0").contains("Parent:");
    }
  }

  /**
   * Verifies that GetAddOutput includes next command hint for issues.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputIncludesNextCommandHint() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "my-task",
        "2.0",
        TaskType.FEATURE,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("/cat:work").contains("2.0-my-task");
    }
  }

  /**
   * Verifies that GetAddOutput defaults to FEATURE type when null.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputDefaultsToFeatureType() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "test-issue",
        "2.0",
        null,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("Feature");
    }
  }

  /**
   * Verifies that GetAddOutput formats task type correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputFormatsTaskType() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "test-issue",
        "2.0",
        TaskType.BUGFIX,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("Bugfix");
    }
  }

  /**
   * Verifies that version display without parent info omits the Parent line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionWithoutParentOmitsParentLine() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "Major-Release",
        "3.0",
        null,
        List.of(),
        "",
        "");

      requireThat(result, "result").doesNotContain("Parent:");
    }
  }

  /**
   * Verifies that version display without path omits the Path line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionWithoutPathOmitsPathLine() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "Major-Release",
        "3.0",
        null,
        List.of(),
        "v2",
        "");

      requireThat(result, "result").contains("Parent: v2").doesNotContain("Path:");
    }
  }

  /**
   * Verifies that issue display has box structure with corners and vertical lines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputIssueDisplayHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "test-issue",
        "2.0",
        TaskType.FEATURE,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("╭").contains("╰").
        contains("╮").contains("╯").contains("│");
    }
  }

  /**
   * Verifies that issue display contains checkmark emoji in header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputIssueDisplayContainsCheckmarkEmoji() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "test-issue",
        "2.0",
        TaskType.FEATURE,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("✅ Issue Created");
    }
  }

  /**
   * Verifies that version display contains checkmark emoji in header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionDisplayContainsCheckmarkEmoji() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "New-Features",
        "2.1",
        null,
        List.of(),
        "v2",
        ".claude/cat/issues/v2/v2.1");

      requireThat(result, "result").contains("✅ Version Created");
    }
  }

  /**
   * Verifies that version display has box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionDisplayHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "New-Features",
        "2.1",
        null,
        List.of(),
        "v2",
        ".claude/cat/issues/v2/v2.1");

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that version display includes the version name in "vX.X: Name" format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionDisplayIncludesVersionName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "New-Features",
        "2.1",
        null,
        List.of(),
        "v2",
        ".claude/cat/issues/v2/v2.1");

      requireThat(result, "result").contains("v2.1: New-Features");
    }
  }

  /**
   * Verifies that version display includes the /cat:add next command hint.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionDisplayIncludesNextCommand() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "New-Features",
        "2.1",
        null,
        List.of(),
        "v2",
        "");

      requireThat(result, "result").contains("/cat:add");
    }
  }

  /**
   * Verifies that GetConfigOutput returns null when config file does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getConfigOutputReturnsNullWhenConfigMissing() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("handler-test");
      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").isNull();
    }
  }

  /**
   * Verifies that GetConfigOutput generates settings display when config exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getConfigOutputGeneratesSettingsDisplay() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("handler-test");
      try
      {
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Path configFile = catDir.resolve("cat-config.json");
        Files.writeString(configFile, """
          {
            "trust": "high",
            "verify": "all",
            "curiosity": "medium",
            "patience": "low",
            "autoRemoveWorktrees": false
          }
          """);

        GetConfigOutput handler = new GetConfigOutput(scope);
        String result = handler.getCurrentSettings(tempDir);

        requireThat(result, "result").contains("Trust").contains("high");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that GetConfigOutput shows auto-remove setting.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getConfigOutputShowsAutoRemoveSetting() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("handler-test");
      try
      {
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Path configFile = catDir.resolve("cat-config.json");
        Files.writeString(configFile, """
          {
            "trust": "medium",
            "autoRemoveWorktrees": true
          }
          """);

        GetConfigOutput handler = new GetConfigOutput(scope);
        String result = handler.getCurrentSettings(tempDir);

        requireThat(result, "result").contains("Auto-remove");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that GetConfigOutput shows keep setting when auto-remove is false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getConfigOutputShowsKeepSetting() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("handler-test");
      try
      {
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Path configFile = catDir.resolve("cat-config.json");
        Files.writeString(configFile, """
          {
            "trust": "medium",
            "autoRemoveWorktrees": false
          }
          """);

        GetConfigOutput handler = new GetConfigOutput(scope);
        String result = handler.getCurrentSettings(tempDir);

        requireThat(result, "result").contains("Keep");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  // --- Tests migrated from Python test_add_handler.py ---

  /**
   * Verifies that GetAddOutput issue display contains the version in "Version: X.X" format.
   * <p>
   * Corresponds to Python test_version_contains_path which checks
   * that the Path line is present in version display output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionDisplayContainsPath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "New-Features",
        "2.1",
        null,
        List.of(),
        "v2",
        ".claude/cat/issues/v2/v2.1");

      requireThat(result, "result").contains("Path: .claude/cat/issues/v2/v2.1");
    }
  }

  /**
   * Verifies that GetAddOutput uses default values when minimal parameters are provided.
   * <p>
   * Corresponds to Python test_version_uses_defaults. The Java API requires
   * non-blank itemName and version, so the defaults test verifies "v0.0: New Version"
   * matches what the Python handler produces for minimal context.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputVersionUsesProvidedDefaults() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.VERSION,
        "New-Version",
        "0.0",
        null,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("v0.0: New-Version");
    }
  }

  /**
   * Verifies that GetAddOutput issue display uses default task type Feature when null is passed.
   * <p>
   * Corresponds to Python test_task_uses_defaults which verifies
   * "Type: Feature" appears when no task type is specified.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputIssueDisplayUsesDefaultType() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "unknown-task",
        "0.0",
        null,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("unknown-task").
        contains("Version: 0.0").contains("Feature");
    }
  }

  /**
   * Verifies that GetAddOutput issue display contains SCRIPT OUTPUT marker equivalent.
   * <p>
   * The Python test checks for "SCRIPT OUTPUT ADD DISPLAY" and "INSTRUCTION:" markers.
   * In Java, the output generator builds a box with a checkmark header. This test verifies
   * the box content contains the issue header and next command hint, which serve the same
   * purpose as the Python markers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getAddOutputIssueDisplayContainsHeaderAndNextHint() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(
        ItemType.ISSUE,
        "parse-tokens",
        "2.0",
        TaskType.FEATURE,
        List.of(),
        "",
        "");

      requireThat(result, "result").contains("Issue Created").contains("Next:");
    }
  }
}
