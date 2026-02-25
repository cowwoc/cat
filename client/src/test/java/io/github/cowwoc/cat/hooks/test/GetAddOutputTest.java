/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetAddOutput;
import io.github.cowwoc.cat.hooks.skills.IssueType;
import io.github.cowwoc.cat.hooks.skills.ItemType;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetAddOutput functionality.
 * <p>
 * Validates issue and version box rendering, multi-issue support,
 * and CLI argument parsing.
 * <p>
 * The {@code main()} method is not directly tested because it uses {@code System.exit()} and
 * {@code System.out.println()}, which are not testable in parallel tests without JVM-global mutation
 * ({@code System.setOut}/{@code System.setErr}). Instead, all business logic that {@code main()} delegates to
 * is fully covered by the tests in this class.
 */
public class GetAddOutputTest
{
  /**
   * Verifies that a single issue display contains the issue name, version, type, and dependencies.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleIssueDisplayContainsExpectedContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(ItemType.ISSUE, List.of("parse-tokens"), "2.0",
        IssueType.FEATURE, List.of(), "", "");

      requireThat(result, "result").contains("parse-tokens");
      requireThat(result, "result").contains("Version: 2.0");
      requireThat(result, "result").contains("Type: Feature");
      requireThat(result, "result").contains("Dependencies: None");
      requireThat(result, "result").contains("Issue Created");
      requireThat(result, "result").contains("/cat:work 2.0-parse-tokens");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a single issue display with dependencies shows them correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleIssueWithDependenciesShowsDependencies() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(ItemType.ISSUE, List.of("fix-auth"), "1.5",
        IssueType.BUGFIX, List.of("setup-db", "init-config"), "", "");

      requireThat(result, "result").contains("fix-auth");
      requireThat(result, "result").contains("Type: Bugfix");
      requireThat(result, "result").contains("Dependencies: setup-db, init-config");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple comma-separated issue names produce a numbered list display.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleIssuesDisplayNumberedList() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(ItemType.ISSUE, List.of("parse-tokens", "fix-auth", "add-logging"), "2.0",
        IssueType.FEATURE, List.of(), "", "");

      requireThat(result, "result").contains("1. parse-tokens");
      requireThat(result, "result").contains("2. fix-auth");
      requireThat(result, "result").contains("3. add-logging");
      requireThat(result, "result").contains("Issues Created");
      requireThat(result, "result").contains("Version: 2.0");
      requireThat(result, "result").contains("/cat:work 2.0-parse-tokens");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that version display contains the version name, number, and next command.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void versionDisplayContainsExpectedContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(ItemType.VERSION, List.of("Beta Release"), "2.1",
        null, List.of(), "v2.0", "/path/to/version");

      requireThat(result, "result").contains("v2.1: Beta Release");
      requireThat(result, "result").contains("Version Created");
      requireThat(result, "result").contains("Parent: v2.0");
      requireThat(result, "result").contains("Path: /path/to/version");
      requireThat(result, "result").contains("/cat:add (to add issues)");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that version display omits parent and path when they are empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void versionDisplayOmitsEmptyOptionalFields() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(ItemType.VERSION, List.of("Major Release"), "3.0",
        null, List.of(), "", "");

      requireThat(result, "result").contains("v3.0: Major Release");
      requireThat(result, "result").doesNotContain("Parent:");
      requireThat(result, "result").doesNotContain("Path:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that box rendering produces properly aligned borders.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void boxBordersAreAligned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(ItemType.ISSUE, List.of("test-issue"), "1.0",
        IssueType.FEATURE, List.of(), "", "");

      // The box should start with top-left corner and end with bottom-right corner
      String[] lines = result.split("\n");
      requireThat(lines[0], "topBorder").startsWith("\u256D");
      // Find the bottom border (line before the empty line + "Next:" line)
      boolean foundBottom = false;
      for (String line : lines)
      {
        if (line.startsWith("\u2570"))
        {
          foundBottom = true;
          break;
        }
      }
      requireThat(foundBottom, "foundBottomBorder").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null issueType defaults to Feature.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nullIssueTypeDefaultsToFeature() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(ItemType.ISSUE, List.of("some-issue"), "1.0",
        null, List.of(), "", "");

      requireThat(result, "result").contains("Type: Feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null itemType is rejected with NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsNullItemType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(null, List.of("test-issue"), "1.0", IssueType.FEATURE, List.of(), "", "");
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("itemType");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an empty itemNames list is rejected with IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsEmptyItemNames() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(ItemType.ISSUE, List.of(), "1.0", IssueType.FEATURE, List.of(), "", "");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("itemNames");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blank version is rejected with IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsBlankVersion() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(ItemType.ISSUE, List.of("test-issue"), "", IssueType.FEATURE, List.of(), "", "");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("version");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null dependencies is rejected with NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsNullDependencies() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(ItemType.ISSUE, List.of("test-issue"), "1.0", IssueType.FEATURE, null, "", "");
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("dependencies");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unknown argument is rejected with IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsUnknownArgument() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(new String[]{"--unknown", "value"});
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Unknown argument");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that --project-dir without a PATH value is rejected with IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsMissingProjectDirValue() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(new String[]{"--project-dir"});
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Missing PATH");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null itemNames is rejected with NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsNullItemNames() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(ItemType.ISSUE, null, "1.0", IssueType.FEATURE, List.of(), "", "");
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("itemNames");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a whitespace-only version is rejected with IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputRejectsWhitespaceOnlyVersion() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAddOutput output = new GetAddOutput(scope);
      try
      {
        output.getOutput(ItemType.ISSUE, List.of("test"), "   ", IssueType.FEATURE, List.of(), "", "");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("version");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
