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
 * Metadata for supported agent runtimes.
 */
public enum AgentRuntime
{
  /**
   * Claude Code runtime.
   */
  CLAUDE("claude", Path.of(".claude-plugin/plugin.json"), null),
  /**
   * Codex runtime.
   */
  CODEX("codex", Path.of(".codex-plugin/plugin.json"), Path.of(".codex-plugin/plugin.json"));

  private final String id;
  private final Path pluginDescriptor;
  private final Path pluginCacheDescriptor;

  AgentRuntime(String id, Path pluginDescriptor, Path pluginCacheDescriptor)
  {
    this.id = id;
    this.pluginDescriptor = pluginDescriptor;
    this.pluginCacheDescriptor = pluginCacheDescriptor;
  }

  /**
   * Returns the runtime identifier.
   *
   * @return the runtime identifier
   */
  public String id()
  {
    return id;
  }

  /**
   * Resolves a runtime identifier.
   *
   * @param value the runtime identifier
   * @return the runtime
   * @throws IllegalArgumentException if the runtime is unsupported
   */
  public static AgentRuntime fromId(String value)
  {
    requireThat(value, "value").isNotBlank();
    for (AgentRuntime runtime: values())
    {
      if (runtime.id.equalsIgnoreCase(value))
        return runtime;
    }
    throw new IllegalArgumentException("Unsupported CAT_RUNTIME: " + value +
      ". CAT_RUNTIME must be one of: claude, codex. Matching is case-insensitive.");
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
   * @return the plugin cache descriptor path, or {@code null} if the runtime does not use one
   */
  public Path pluginCacheDescriptor()
  {
    return pluginCacheDescriptor;
  }

  /**
   * Returns the ordered rule directories for this runtime.
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
