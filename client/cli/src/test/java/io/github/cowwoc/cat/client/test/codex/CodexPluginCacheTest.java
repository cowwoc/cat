/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.client.test.TestUtils;
import io.github.cowwoc.cat.codex.hook.CodexPluginCache;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests Codex plugin cache path resolution.
 */
public final class CodexPluginCacheTest
{
  /**
   * Verifies that Codex plugin cache resolution follows the documented cache layout.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void resolvesDocumentedCachePath() throws IOException
  {
    Path codexHome = Files.createTempDirectory("cat-codex-home-");
    try
    {
      Path pluginRoot = codexHome.resolve("plugins/cache/local-cat/cat/2.1");
      Files.createDirectories(pluginRoot.resolve(".codex-plugin"));
      Files.writeString(pluginRoot.resolve(".codex-plugin/plugin.json"), "{\"name\":\"cat\"}");

      Path resolved = CodexPluginCache.resolvePluginRoot(codexHome, "local-cat", "cat", "2.1");

      requireThat(resolved, "resolved").isEqualTo(pluginRoot.toAbsolutePath().normalize());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(codexHome);
    }
  }

  /**
   * Verifies that path traversal cannot escape the Codex plugin cache root.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void rejectsMultiSegmentCoordinates() throws IOException
  {
    Path codexHome = Files.createTempDirectory("cat-codex-home-");
    try
    {
      try
      {
        CodexPluginCache.resolvePluginRoot(codexHome, "local-cat/../other", "cat", "2.1");
        throw new AssertionError("Expected path traversal to be rejected");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("marketplaceName");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(codexHome);
    }
  }
}
