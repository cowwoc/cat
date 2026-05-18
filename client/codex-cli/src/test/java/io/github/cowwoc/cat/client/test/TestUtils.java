/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Shared Codex test utilities.
 */
public final class TestUtils
{
  private TestUtils()
  {
  }

  /**
   * Recursively deletes a directory if it exists.
   *
   * @param directory the directory to delete
   */
  public static void deleteDirectoryRecursively(Path directory)
  {
    requireThat(directory, "directory").isNotNull();
    if (Files.notExists(directory))
      return;
    try (Stream<Path> paths = Files.walk(directory))
    {
      paths.sorted(Comparator.reverseOrder()).forEach(path ->
      {
        try
        {
          Files.deleteIfExists(path);
        }
        catch (IOException e)
        {
          throw WrappedCheckedException.wrap(e);
        }
      });
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}
