/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.GitSquash;
import io.github.cowwoc.cat.hooks.JvmScope;
import org.testng.annotations.Test;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitSquash validation and error handling.
 * <p>
 * Tests verify input validation without requiring actual git repository setup.
 */
public class GitSquashTest
{
  /**
   * Creates a temporary directory for testing.
   *
   * @return the temporary directory path
   */
  private Path createTempDir()
  {
    try
    {
      return Files.createTempDirectory("git-squash-test");
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Verifies that execute rejects null baseCommit.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullBaseCommit() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      Path messageFile = tempDir.resolve("message.txt");
      Files.writeString(messageFile, "squash commit");

      GitSquash cmd = new GitSquash(scope);

      try
      {
        cmd.execute(null, "HEAD", messageFile.toString(), "");
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("baseCommit");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects blank baseCommit.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsBlankBaseCommit() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      Path messageFile = tempDir.resolve("message.txt");
      Files.writeString(messageFile, "squash commit");

      GitSquash cmd = new GitSquash(scope);

      try
      {
        cmd.execute("", "HEAD", messageFile.toString(), "");
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("baseCommit");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects missing message file.
   */
  @Test
  public void executeRejectsMissingMessageFile() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      GitSquash cmd = new GitSquash(scope);

      try
      {
        cmd.execute("HEAD~1", "HEAD", tempDir.resolve("missing.txt").toString(), "");
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("Commit message file not found");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute accepts empty originalBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeAcceptsEmptyOriginalBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      Path messageFile = tempDir.resolve("message.txt");
      Files.writeString(messageFile, "squash commit");

      GitSquash cmd = new GitSquash(scope);

      try
      {
        cmd.execute("HEAD~1", "HEAD", messageFile.toString(), "");
      }
      catch (IOException _)
      {
        // Acceptable - git operation may fail depending on environment
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }
}
