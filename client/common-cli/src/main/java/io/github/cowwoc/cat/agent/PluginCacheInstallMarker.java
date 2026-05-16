/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tracks one-time install-local migration work for cached plugin installations.
 */
public final class PluginCacheInstallMarker
{
  private PluginCacheInstallMarker()
  {
  }

  /**
   * Returns the install marker path for cache-installed plugins.
   *
   * @param pluginVersion the plugin version
   * @param scope the plugin scope
   * @return marker path, or {@code null} if this plugin root is not an installed plugin cache
   * @throws NullPointerException if any parameter is null
   */
  public static Path getMarker(String pluginVersion, AgentPluginScope scope)
  {
    requireThat(pluginVersion, "pluginVersion").isNotNull();
    requireThat(scope, "scope").isNotNull();
    Path pluginCacheDescriptor = scope.getPluginCacheDescriptor();
    if (pluginCacheDescriptor == null || !isPluginCache(scope.getPluginRoot(), pluginCacheDescriptor))
      return null;
    return scope.getPluginRoot().resolve(".cat/migrations").resolve(pluginVersion + ".done");
  }

  /**
   * Writes the install marker when the plugin is running from a plugin cache.
   *
   * @param pluginVersion the plugin version
   * @param scope the plugin scope
   * @throws IOException if writing the marker fails
   */
  public static void write(String pluginVersion, AgentPluginScope scope) throws IOException
  {
    Path marker = getMarker(pluginVersion, scope);
    if (marker == null)
      return;
    Files.createDirectories(marker.getParent());
    Files.writeString(marker, pluginVersion + "\n");
  }

  /**
   * Returns {@code true} if a plugin root looks like a cached plugin entry.
   *
   * @param pluginRoot the plugin root directory
   * @param pluginCacheDescriptor the plugin cache descriptor path relative to the plugin root
   * @return {@code true} if the plugin is installed in a plugin cache
   */
  private static boolean isPluginCache(Path pluginRoot, Path pluginCacheDescriptor)
  {
    if (!Files.isRegularFile(pluginRoot.resolve(pluginCacheDescriptor)))
      return false;

    for (int i = 0; i < pluginRoot.getNameCount() - 1; ++i)
    {
      if (pluginRoot.getName(i).toString().equals("plugins") &&
        pluginRoot.getName(i + 1).toString().equals("cache"))
        return true;
    }
    return false;
  }
}
