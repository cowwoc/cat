/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Shared test utilities for directory and file operations.
 */
public final class TestUtils
{
  private TestUtils()
  {
    // Utility class
  }

  /**
   * Deletes a directory and all its contents recursively using {@code Files.walkFileTree}.
   * <p>
   * Prints to stderr on failure so issues are visible in test output.
   *
   * @param directory the directory to delete
   */
  public static void deleteDirectoryRecursively(Path directory)
  {
    if (!Files.exists(directory))
      return;
    try
    {
      Files.walkFileTree(directory, new SimpleFileVisitor<>()
      {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
        {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc)
        {
          System.err.println("Failed to visit file during cleanup: " + file + " - " + exc.getMessage());
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
        {
          if (exc != null)
            System.err.println("Error traversing directory during cleanup: " + dir + " - " + exc.getMessage());
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }
    catch (IOException e)
    {
      System.err.println("Failed to delete directory: " + directory + " - " + e.getMessage());
    }
  }
}
