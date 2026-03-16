/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

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
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getJsonMapperThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();

      scope.getJsonMapper();
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
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getDisplayUtilsThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();

      scope.getDisplayUtils();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getProjectPath() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getProjectPathThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();

      scope.getProjectPath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects null claudeProjectPath.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*claudeProjectPath.*")
  public void constructorRejectsNullClaudeProjectDir()
  {
    Path validPath = Path.of("/tmp");
    new TestJvmScope(null, validPath);
  }

  /**
   * Verifies that constructor rejects null claudePluginRoot.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*claudePluginRoot.*")
  public void constructorRejectsNullClaudePluginRoot()
  {
    Path validPath = Path.of("/tmp");
    new TestJvmScope(validPath, null);
  }
}
