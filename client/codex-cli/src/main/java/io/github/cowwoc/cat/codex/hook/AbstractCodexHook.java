/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.agent.AbstractEngineScope;
import io.github.cowwoc.cat.agent.AgentEngine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Abstract base class for Codex hook scopes.
 * <p>
 * Centralizes Codex hook plugin paths shared by production and test hook scopes.
 */
public abstract class AbstractCodexHook extends AbstractEngineScope implements CodexHookScope
{
  private final InputStream hookInput;

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
   * Creates a new Codex hook scope with explicit process-scoped engine values.
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
    this(projectPath, pluginRoot, pluginData, workDir, timezone,
      new ByteArrayInputStream("{}".getBytes(UTF_8)));
  }

  /**
   * Creates a new Codex hook scope with explicit process-scoped engine values and hook input.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param pluginData the plugin data directory path
   * @param workDir the process working directory
   * @param timezone the timezone
   * @param hookInput standard input for this hook invocation
   * @throws NullPointerException if any argument is null
   * @throws IllegalArgumentException if any path argument is relative
   */
  protected AbstractCodexHook(Path projectPath, Path pluginRoot, Path pluginData, Path workDir,
    String timezone, InputStream hookInput)
  {
    super(projectPath, pluginRoot, pluginData, AgentEngine.CODEX.pluginDescriptor(),
      AgentEngine.CODEX.ruleDirectories(projectPath, pluginRoot),
      AgentEngine.CODEX.pluginCacheDescriptor(), workDir, timezone);
    requireThat(projectPath, "projectPath").isNotNull().isAbsolute();
    requireThat(pluginRoot, "pluginRoot").isNotNull().isAbsolute();
    requireThat(pluginData, "pluginData").isNotNull().isAbsolute();
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
    requireThat(hookInput, "hookInput").isNotNull();
    this.hookInput = hookInput;
  }

  @Override
  public InputStream getHookInput()
  {
    ensureOpen();
    return hookInput;
  }
}
