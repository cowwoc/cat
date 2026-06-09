/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves Codex plugin cache paths using Codex's documented installation layout.
 */
public final class CodexPluginCache
{
  private static final Path CODEX_DESCRIPTOR = Path.of(".codex-plugin/plugin.json");

  private CodexPluginCache()
  {
  }

  /**
   * Resolves the installed plugin root under {@code ~/.codex/plugins/cache}.
   *
   * @param codexHome the Codex home directory
   * @param marketplaceName the marketplace directory name
   * @param pluginName the plugin directory name
   * @param version the installed plugin version directory name
   * @return the installed plugin root
   * @throws IllegalArgumentException if the resolved path is not an installed Codex plugin cache
   * @throws NullPointerException if any parameter is null
   */
  public static Path resolvePluginRoot(Path codexHome, String marketplaceName, String pluginName,
    String version)
  {
    requireThat(codexHome, "codexHome").isNotNull();
    String validMarketplaceName = validateSegment(marketplaceName, "marketplaceName");
    String validPluginName = validateSegment(pluginName, "pluginName");
    String validVersion = validateSegment(version, "version");

    Path cacheRoot = codexHome.resolve("plugins").resolve("cache").toAbsolutePath().normalize();
    Path pluginRoot = cacheRoot.resolve(validMarketplaceName).resolve(validPluginName).
      resolve(validVersion).normalize();
    if (!pluginRoot.startsWith(cacheRoot))
      throw new IllegalArgumentException("Codex plugin cache path escapes cache root: " + pluginRoot);
    if (!Files.isRegularFile(pluginRoot.resolve(CODEX_DESCRIPTOR)))
    {
      throw new IllegalArgumentException("Codex plugin cache descriptor not found: " +
        pluginRoot.resolve(CODEX_DESCRIPTOR));
    }
    return pluginRoot;
  }

  /**
   * Validates path segment used inside Codex plugin cache layout.
   *
   * @param value raw segment value
   * @param name logical parameter name
   * @return stripped segment value
   */
  private static String validateSegment(String value, String name)
  {
    requireThat(value, name).isNotBlank();
    String stripped = value.strip();
    Path path = Path.of(stripped);
    if (path.isAbsolute() || path.getNameCount() != 1 || stripped.equals(".") ||
      stripped.equals(".."))
      throw new IllegalArgumentException(name + " must be a single path segment: " + stripped);
    return stripped;
  }
}
