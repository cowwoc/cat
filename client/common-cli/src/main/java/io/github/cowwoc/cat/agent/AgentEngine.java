/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for supported agent engines.
 */
public enum AgentEngine
{
  /**
   * Claude Code engine.
   */
  CLAUDE("claude", Path.of(".claude-plugin/plugin.json"), null),
  /**
   * Codex engine.
   */
  CODEX("codex", Path.of(".codex-plugin/plugin.json"), Path.of(".codex-plugin/plugin.json"));

  private final String id;
  private final Path pluginDescriptor;
  private final Path pluginCacheDescriptor;

  AgentEngine(String id, Path pluginDescriptor, Path pluginCacheDescriptor)
  {
    this.id = id;
    this.pluginDescriptor = pluginDescriptor;
    this.pluginCacheDescriptor = pluginCacheDescriptor;
  }

  /**
   * Returns the engine identifier.
   *
   * @return the engine identifier
   */
  public String id()
  {
    return id;
  }

  /**
   * Resolves a engine identifier.
   *
   * @param value the engine identifier
   * @return the engine
   * @throws IllegalArgumentException if the engine is unsupported
   */
  public static AgentEngine fromId(String value)
  {
    requireThat(value, "value").isNotBlank();
    for (AgentEngine engine: values())
    {
      if (engine.id.equalsIgnoreCase(value))
        return engine;
    }
    throw new IllegalArgumentException("Unsupported CAT_ENGINE: " + value +
      ". CAT_ENGINE must be one of: claude, codex. Matching is case-insensitive.");
  }

  /**
   * Returns the plugin descriptor path relative to the plugin root.
   *
   * @return the plugin descriptor path
   */
  public Path pluginDescriptor()
  {
    return pluginDescriptor;
  }

  /**
   * Returns the plugin cache descriptor path relative to the plugin root.
   *
   * @return the plugin cache descriptor path, or {@code null} if the engine does not use one
   */
  public Path pluginCacheDescriptor()
  {
    return pluginCacheDescriptor;
  }

  /**
   * Returns the ordered rule directories for this engine.
   *
   * @param projectPath the project directory
   * @param pluginRoot the plugin root directory
   * @return the ordered rule directories
   */
  public List<Path> ruleDirectories(Path projectPath, Path pluginRoot)
  {
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    List<Path> result = new ArrayList<>();
    result.add(pluginRoot.resolve("rules/common"));
    result.add(pluginRoot.resolve("rules").resolve(id));
    result.add(projectPath.resolve(".cat/rules/common"));
    result.add(projectPath.resolve(".cat/rules").resolve(id));
    if (this == CLAUDE)
      result.add(projectPath.resolve(".claude/rules"));
    return result;
  }
}
