/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import java.nio.file.Path;
import java.util.List;

/**
 * Engine-neutral plugin scope shared by all agent engines.
 */
public interface AgentPluginScope extends AgentScope
{
  /**
   * Returns the plugin root directory.
   *
   * @return the plugin root directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getPluginRoot();

  /**
   * Returns the ordered rule directories for the active engine.
   *
   * @return the ordered rule directories
   * @throws IllegalStateException if this scope is closed
   */
  List<Path> getRuleDirectories();

  /**
   * Returns the plugin data directory.
   *
   * @return the plugin data directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getPluginData();

  /**
   * Returns the plugin marketplace prefix ({@code "cat"}).
   *
   * @return the plugin prefix, never blank
   * @throws IllegalStateException if this scope is closed
   */
  String getPluginPrefix();

  /**
   * Returns the plugin descriptor path relative to the plugin root.
   *
   * @return the descriptor path
   * @throws IllegalStateException if this scope is closed
   */
  Path getPluginDescriptor();

  /**
   * Returns the plugin cache descriptor path relative to the plugin root.
   *
   * @return the descriptor path, or {@code null} if this engine does not use a cache descriptor
   * @throws IllegalStateException if this scope is closed
   */
  Path getPluginCacheDescriptor();
}
