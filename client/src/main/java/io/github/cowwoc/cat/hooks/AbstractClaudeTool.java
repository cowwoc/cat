/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Abstract base class for Claude tool processes that reads session environment values
 * at construction time and exposes them via the {@link ClaudeTool} interface.
 * <p>
 * Subclasses that run in production (e.g., {@link MainClaudeTool}) pass values read from
 * environment variables to the protected constructor. Test subclasses pass injected values
 * to avoid host-environment dependencies.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudeTool extends AbstractJvmScope implements ClaudeTool
{
  private final String sessionId;
  private final Path projectPath;
  private final Path pluginRoot;

  /**
   * Creates a new abstract Claude tool scope with the given environment values.
   *
   * @param sessionId the Claude session ID
   * @param projectPath the project's root directory path (must be absolute)
   * @param pluginRoot the Claude plugin root directory path (must be absolute)
   * @throws IllegalArgumentException if {@code sessionId} is blank, or if {@code projectPath} or
   *   {@code pluginRoot} are not absolute paths
   * @throws NullPointerException if {@code projectPath} or {@code pluginRoot} are null
   */
  protected AbstractClaudeTool(String sessionId, Path projectPath, Path pluginRoot)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(projectPath, "projectPath").isNotNull().isAbsolute();
    requireThat(pluginRoot, "pluginRoot").isNotNull().isAbsolute();
    this.sessionId = sessionId;
    this.projectPath = projectPath;
    this.pluginRoot = pluginRoot;
  }

  @Override
  public String getSessionId()
  {
    ensureOpen();
    return sessionId;
  }

  @Override
  public Path getProjectPath()
  {
    ensureOpen();
    return projectPath;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return pluginRoot;
  }
}
