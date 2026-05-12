/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstract base class for plugin scopes shared by supported agent runtimes.
 * <p>
 * Stores runtime-neutral plugin paths and resolves runtime-specific rule directories internally.
 */
public abstract class AbstractAgentPluginScope extends AbstractAgentScope implements AgentPluginScope
{
  /**
   * The plugin marketplace prefix.
   */
  private static final String PLUGIN_PREFIX = "cat";
  private final Path pluginRoot;
  private final Path pluginData;
  private final Path pluginDescriptor;
  private final List<Path> ruleDirectories;
  private final Path pluginCacheDescriptor;

  /**
   * Creates a new abstract plugin scope with the given infrastructure paths.
   *
   * @param projectPath the project's root directory
   * @param pluginRoot the plugin root directory
   * @param pluginData the plugin data directory
   * @param pluginDescriptor the plugin descriptor path relative to the plugin root
   * @param ruleDirectories the ordered rule directories for the active runtime
   * @param pluginCacheDescriptor the runtime-specific plugin cache descriptor path relative to the plugin
   *   root, or {@code null} if the runtime does not install plugins into a cache
   * @throws NullPointerException if any parameter is null
   */
  protected AbstractAgentPluginScope(Path projectPath, Path pluginRoot, Path pluginData,
    Path pluginDescriptor, List<Path> ruleDirectories, Path pluginCacheDescriptor)
  {
    super(projectPath);
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(pluginData, "pluginData").isNotNull();
    requireThat(pluginDescriptor, "pluginDescriptor").isNotNull();
    requireThat(ruleDirectories, "ruleDirectories").isNotNull();
    this.pluginRoot = pluginRoot;
    this.pluginData = pluginData;
    this.pluginDescriptor = pluginDescriptor;
    this.ruleDirectories = List.copyOf(ruleDirectories);
    this.pluginCacheDescriptor = pluginCacheDescriptor;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return pluginRoot;
  }

  @Override
  public List<Path> getRuleDirectories()
  {
    ensureOpen();
    return ruleDirectories;
  }

  @Override
  public Path getPluginData()
  {
    ensureOpen();
    return pluginData;
  }

  @Override
  public String getPluginPrefix()
  {
    ensureOpen();
    return PLUGIN_PREFIX;
  }

  @Override
  public Path getPluginDescriptor()
  {
    ensureOpen();
    return pluginDescriptor;
  }

  @Override
  public Path getPluginCacheDescriptor()
  {
    ensureOpen();
    return pluginCacheDescriptor;
  }
}
