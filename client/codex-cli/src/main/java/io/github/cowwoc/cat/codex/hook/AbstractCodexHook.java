/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AbstractRuntimeScope;
import io.github.cowwoc.cat.agent.AgentRuntime;
import io.github.cowwoc.cat.agent.RuntimeScopeConfig;

import java.nio.file.Path;

/**
 * Abstract base class for Codex hook scopes.
 * <p>
 * Centralizes Codex hook plugin paths shared by production and test hook scopes.
 */
public abstract class AbstractCodexHook extends AbstractRuntimeScope
{
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
    this(projectPath, pluginRoot, pluginData, projectPath, "UTC");
  }

  /**
   * Creates a new Codex hook scope with explicit process-scoped runtime values.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param pluginData the plugin data directory path
   * @param workDir the process working directory
   * @param timezone the timezone
   * @throws NullPointerException if any argument is null
   * @throws IllegalArgumentException if any path argument is relative
   */
  protected AbstractCodexHook(Path projectPath, Path pluginRoot, Path pluginData, Path workDir,
    String timezone)
  {
    super(new RuntimeScopeConfig(projectPath, pluginRoot, pluginData,
      AgentRuntime.CODEX.pluginDescriptor(), AgentRuntime.CODEX.ruleDirectories(projectPath, pluginRoot),
      AgentRuntime.CODEX.pluginCacheDescriptor(), workDir, timezone));
    requireThat(projectPath, "projectPath").isNotNull().isAbsolute();
    requireThat(pluginRoot, "pluginRoot").isNotNull().isAbsolute();
    requireThat(pluginData, "pluginData").isNotNull().isAbsolute();
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
  }
}
