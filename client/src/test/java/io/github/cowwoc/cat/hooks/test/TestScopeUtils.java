/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.DisplayUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared test utilities for scope setup.
 */
final class TestScopeUtils
{
  /**
   * Prevents instantiation.
   */
  private TestScopeUtils()
  {
  }

  /**
   * Copies emoji-widths.json from the real plugin directory to the given temporary plugin root.
   * <p>
   * {@link DisplayUtils} requires emoji-widths.json at initialization time. This method ensures that
   * test-scope plugin directories contain the file by copying it from the real workspace.
   * <p>
   * Uses the {@code user.dir} system property to locate the workspace root, then traverses upward
   * until a directory containing both {@code plugin/} and {@code client/} subdirectories is found.
   * This traversal accommodates different Maven working directories (e.g., running tests from the
   * {@code client/} subdirectory vs. the repository root).
   * <p>
   * The {@link java.nio.file.FileAlreadyExistsException} catch is intentional: concurrent test
   * threads may race to copy the same file into a shared temporary plugin directory. The first writer
   * wins; subsequent writers treat the already-existing file as success.
   *
   * @param claudePluginRoot the plugin root directory to copy the file to
   * @throws IOException if the emoji-widths.json file cannot be found or copied
   */
  static void copyEmojiWidthsIfNeeded(Path claudePluginRoot) throws IOException
  {
    if (!Files.isDirectory(claudePluginRoot))
      return;
    Path targetEmojiFile = claudePluginRoot.resolve("emoji-widths.json");
    if (Files.notExists(targetEmojiFile))
    {
      String userDir = System.getProperty("user.dir");
      Path basePath = Path.of(userDir);
      Path workspaceRoot = findWorkspaceRoot(basePath);
      Path[] possiblePaths = {
        basePath.resolve("plugin/emoji-widths.json"),
        basePath.resolve("../plugin/emoji-widths.json").normalize(),
        workspaceRoot.resolve("plugin/emoji-widths.json")
      };

      Path sourceEmojiFile = null;
      for (Path path : possiblePaths)
      {
        if (Files.exists(path))
        {
          sourceEmojiFile = path;
          break;
        }
      }

      if (sourceEmojiFile == null)
        throw new IOException("emoji-widths.json not found in any of the expected locations");

      try
      {
        Files.copy(sourceEmojiFile, targetEmojiFile);
      }
      catch (java.nio.file.FileAlreadyExistsException _)
      {
        // Another thread already copied the file
      }
    }
  }

  /**
   * Finds the workspace root by traversing up from the current directory.
   * <p>
   * Looks for a directory containing both "plugin/" and "client/" subdirectories.
   *
   * @param startPath the path to start searching from
   * @return the workspace root path
   * @throws IOException if the workspace root cannot be found
   */
  private static Path findWorkspaceRoot(Path startPath) throws IOException
  {
    Path current = startPath.toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.exists(current.resolve("plugin")) && Files.exists(current.resolve("client")))
        return current;
      current = current.getParent();
    }
    throw new IOException("Could not find workspace root from: " + startPath);
  }
}
