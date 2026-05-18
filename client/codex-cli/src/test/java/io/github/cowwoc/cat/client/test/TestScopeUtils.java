/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared test utilities for Codex scope setup.
 */
final class TestScopeUtils
{
  private TestScopeUtils()
  {
  }

  static void copyEmojiWidthsIfNeeded(Path pluginRoot) throws IOException
  {
    if (!Files.isDirectory(pluginRoot))
      return;
    Path targetEmojiFile = pluginRoot.resolve("emoji-widths.json");
    if (Files.notExists(targetEmojiFile))
    {
      String userDir = System.getProperty("user.dir");
      Path basePath = Path.of(userDir);
      Path workspaceRoot = findWorkspaceRoot(basePath);
      Path[] possiblePaths = {
        basePath.resolve("../plugin/emoji-widths.json").normalize(),
        workspaceRoot.resolve("client/plugin/emoji-widths.json")
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
        throw new IOException("emoji-widths.json not found in expected locations");

      try
      {
        Files.copy(sourceEmojiFile, targetEmojiFile);
      }
      catch (java.nio.file.FileAlreadyExistsException _)
      {
        // Another thread already copied the file.
      }
    }
  }

  private static Path findWorkspaceRoot(Path startPath) throws IOException
  {
    Path current = startPath.toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.exists(current.resolve("client/plugin")) && Files.exists(current.resolve("client/pom.xml")))
        return current;
      current = current.getParent();
    }
    throw new IOException("Could not find workspace root from: " + startPath);
  }
}
