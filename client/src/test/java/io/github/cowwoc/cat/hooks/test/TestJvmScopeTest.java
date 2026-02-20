/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for TestJvmScope implementation.
 */
public final class TestJvmScopeTest
{
  /**
   * Verifies that getJsonMapper() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  @SuppressWarnings("PMD.CloseResource")
  public void getJsonMapperThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try
    {
      JvmScope scope = new TestJvmScope(tempDir, tempDir);
      scope.close();

      try
      {
        scope.getJsonMapper();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("closed");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getDisplayUtils() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  @SuppressWarnings("PMD.CloseResource")
  public void getDisplayUtilsThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try
    {
      JvmScope scope = new TestJvmScope(tempDir, tempDir);
      scope.close();

      try
      {
        scope.getDisplayUtils();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("closed");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getClaudeProjectDir() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  @SuppressWarnings("PMD.CloseResource")
  public void getClaudeProjectDirThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try
    {
      JvmScope scope = new TestJvmScope(tempDir, tempDir);
      scope.close();

      try
      {
        scope.getClaudeProjectDir();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("closed");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects null claudeProjectDir.
   */
  @Test
  public void constructorRejectsNullClaudeProjectDir()
  {
    Path validPath = Path.of("/tmp");
    try
    {
      new TestJvmScope(null, validPath);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("claudeProjectDir");
    }
  }

  /**
   * Verifies that constructor rejects null claudePluginRoot.
   */
  @Test
  public void constructorRejectsNullClaudePluginRoot()
  {
    Path validPath = Path.of("/tmp");
    try
    {
      new TestJvmScope(validPath, null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("claudePluginRoot");
    }
  }
}
