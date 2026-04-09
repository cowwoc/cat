/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.util.PathUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PathUtils}.
 */
public class PathUtilsTest
{
  /**
   * Verifies that normalize allows a valid path within the base directory.
   */
  @Test
  public void normalizeAllowsValidPath() throws IOException
  {
    Path baseDir = Files.createTempDirectory("test-base").toAbsolutePath().normalize();
    try
    {
      Path result = PathUtils.normalize(baseDir, "subdir/file", "test");
      requireThat(result.startsWith(baseDir), "result.startsWith(baseDir)").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(baseDir);
    }
  }

  /**
   * Verifies that normalize throws for a path traversal attempt.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*path traversal.*")
  public void normalizeRejectsPathTraversal() throws IOException
  {
    Path baseDir = Files.createTempDirectory("test-base").toAbsolutePath().normalize();
    try
    {
      PathUtils.normalize(baseDir, "../../etc/passwd", "testParam");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(baseDir);
    }
  }

  /**
   * Verifies that normalize throws NullPointerException for a null baseDir.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*baseDir.*")
  public void normalizeRejectsNullBaseDir()
  {
    PathUtils.normalize(null, "subdir", "param");
  }

  /**
   * Verifies that normalize throws NullPointerException for a null relativePath.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*relativePath.*")
  public void normalizeRejectsNullRelativePath() throws IOException
  {
    Path baseDir = Files.createTempDirectory("test-base").toAbsolutePath().normalize();
    try
    {
      PathUtils.normalize(baseDir, null, "param");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(baseDir);
    }
  }
}
