/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AbstractAgentPluginScope;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstract base class for Codex hook scopes.
 * <p>
 * Centralizes Codex hook plugin paths shared by production and test hook scopes.
 */
public abstract class AbstractCodexHook extends AbstractAgentPluginScope
{
  private static final Path PLUGIN_DESCRIPTOR = Path.of(".codex-plugin/plugin.json");

  /**
   * Creates a new Codex hook scope.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param pluginData the plugin data directory path
   * @throws NullPointerException if any argument is null
   * @throws IllegalArgumentException if any argument is relative
   */
  protected AbstractCodexHook(Path projectPath, Path pluginRoot, Path pluginData)
  {
    super(projectPath, pluginRoot, pluginData, PLUGIN_DESCRIPTOR, getRuleDirectories(projectPath,
      pluginRoot), PLUGIN_DESCRIPTOR);
    requireThat(projectPath, "projectPath").isNotNull().isAbsolute();
    requireThat(pluginRoot, "pluginRoot").isNotNull().isAbsolute();
    requireThat(pluginData, "pluginData").isNotNull().isAbsolute();
  }

  /**
   * Returns the ordered rule directories for Codex hooks.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @return the ordered Codex hook rule directories
   */
  private static List<Path> getRuleDirectories(Path projectPath, Path pluginRoot)
  {
    return List.of(
      pluginRoot.resolve("rules/common"),
      pluginRoot.resolve("rules/codex"),
      projectPath.resolve(".cat/rules/common"),
      projectPath.resolve(".cat/rules/codex"));
  }
}
