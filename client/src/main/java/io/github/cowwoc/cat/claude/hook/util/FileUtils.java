/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * General-purpose file system utility methods.
 */
public final class FileUtils
{
  /**
   * Private constructor to prevent instantiation.
   */
  private FileUtils()
  {
    // Utility class
  }

  /**
   * Recursively deletes a directory and all its contents.
   * <p>
   * Files are deleted before their parent directories (deepest paths first). If any individual
   * deletion fails, the error is recorded in {@code failures} and the method continues.
   * If walking the directory tree itself fails (e.g., due to concurrent deletion), the error is
   * added to {@code failures} and the method returns without deleting anything further.
   *
   * @param directory the directory to delete
   * @param failures  a mutable list that receives the {@link IOException} for every path that could
   *   not be deleted
   * @throws NullPointerException if {@code directory} or {@code failures} are null
   */
  public static void deleteDirectoryRecursively(Path directory, List<IOException> failures)
  {
    try (Stream<Path> walk = Files.walk(directory))
    {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path path : paths)
      {
        try
        {
          Files.delete(path);
        }
        catch (IOException e)
        {
          failures.add(e);
        }
      }
    }
    catch (IOException e)
    {
      failures.add(e);
    }
  }

  /**
   * Recursively deletes a directory and all its contents, throwing on the first failure.
   * <p>
   * Files are deleted before their parent directories (deepest paths first).
   *
   * @param directory the directory to delete
   * @throws NullPointerException if {@code directory} is null
   * @throws IOException          if walking the tree or deleting any path fails
   */
  public static void deleteDirectoryRecursively(Path directory) throws IOException
  {
    try (Stream<Path> walk = Files.walk(directory))
    {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path path : paths)
        Files.delete(path);
    }
  }

  /**
   * Recursively copies a directory and all its contents to a destination.
   * <p>
   * Directories are created before their contents (shallowest paths first). If the destination
   * already exists, its contents are preserved and only new files are added (no overwrite).
   *
   * @param source      the source directory to copy from
   * @param destination the destination directory to copy to
   * @throws NullPointerException if {@code source} or {@code destination} are null
   * @throws IOException          if walking the tree or copying any path fails
   */
  public static void copyDirectoryRecursively(Path source, Path destination) throws IOException
  {
    try (Stream<Path> walk = Files.walk(source))
    {
      List<Path> paths = walk.toList();
      for (Path sourcePath : paths)
      {
        Path relativePath = source.relativize(sourcePath);
        Path destPath = destination.resolve(relativePath);
        if (Files.isDirectory(sourcePath))
        {
          if (!Files.exists(destPath))
            Files.createDirectory(destPath);
        }
        else
          Files.copy(sourcePath, destPath);
      }
    }
  }
}
