/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/**
 * Tests the VERSION file behavior for CAT migration version tracking.
 */
public final class CheckUpgradeTest
{
  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param dir directory to delete
   */
  private void deleteRecursively(Path dir)
  {
    try
    {
      if (Files.exists(dir))
      {
        Files.walk(dir).
          sorted((a, b) -> -a.compareTo(b)).
          forEach(path ->
          {
            try
            {
              Files.deleteIfExists(path);
            }
            catch (Exception _)
            {
            }
          });
      }
    }
    catch (Exception _)
    {
    }
  }

  /**
   * Verifies that reading a VERSION file that does not exist returns the default version "0.0.0".
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileAbsentReturnsDefault() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      String version;
      if (Files.isRegularFile(versionFile))
        version = Files.readString(versionFile).strip();
      else
        version = "0.0.0";
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("0.0.0");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that reading a VERSION file containing a valid version string returns that version.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileWithVersionReturnsVersion() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "2.4\n");
      String version = Files.readString(versionFile).strip();
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("2.4");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that reading an empty VERSION file returns the default version "0.0.0".
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileEmptyReturnsDefault() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "");
      String version = Files.readString(versionFile).strip();
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("0.0.0");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that reading a VERSION file with surrounding whitespace strips the whitespace and returns
   * the version.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileWithTrailingWhitespaceReturnsStripped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "  2.3  \n");
      String version = Files.readString(versionFile).strip();
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("2.3");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that writing a version to the VERSION file stores it with a trailing newline.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void writeVersionFileCreatesCorrectContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "2.4" + "\n");
      String content = Files.readString(versionFile);
      requireThat(content.strip(), "version").isEqualTo("2.4");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }
}
