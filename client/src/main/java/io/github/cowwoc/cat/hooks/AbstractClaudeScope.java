/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.nio.file.Path;

/**
 * Abstract base class for scopes that operate within a Claude environment, adding the
 * Claude config directory path to the base JVM scope paths.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudeScope extends AbstractJvmScope
{
  private final Path claudeConfigPath;

  /**
   * Creates a new Claude scope with the given paths.
   *
   * @param projectPath the project's root directory
   * @param pluginRoot the Claude plugin root directory
   * @param claudeConfigPath the Claude config directory
   * @throws NullPointerException if any parameter is null
   */
  protected AbstractClaudeScope(Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    super(projectPath, pluginRoot);
    requireThat(claudeConfigPath, "claudeConfigPath").isNotNull();
    this.claudeConfigPath = claudeConfigPath;
  }

  /**
   * Returns the Claude config directory.
   *
   * @return the config directory path
   * @throws IllegalStateException if this scope is closed
   */
  public Path getClaudeConfigPath()
  {
    ensureOpen();
    return claudeConfigPath;
  }

  @Override
  public Path getClaudeSessionsPath()
  {
    ensureOpen();
    return claudeConfigPath.resolve("projects").resolve(encodeProjectPath(getProjectPath().toString()));
  }

  @Override
  public Path getClaudeSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return getClaudeSessionsPath().resolve(sessionId);
  }
}
