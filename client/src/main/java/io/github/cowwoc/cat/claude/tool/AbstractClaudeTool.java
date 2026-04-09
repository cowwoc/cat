/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.AbstractClaudePluginScope;

import java.nio.file.Path;

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
public abstract class AbstractClaudeTool extends AbstractClaudePluginScope implements ClaudeTool
{
  private final String sessionId;

  /**
   * Creates a new abstract Claude tool scope with the given environment values.
   *
   * @param sessionId the Claude session ID
   * @param projectPath the project's root directory path
   * @param pluginRoot the Claude plugin root directory path
   * @param claudeConfigPath the Claude config directory path
   * @throws IllegalArgumentException if {@code sessionId} is blank
   * @throws NullPointerException if {@code projectPath}, {@code pluginRoot}, or
   *   {@code claudeConfigPath} are null
   */
  protected AbstractClaudeTool(String sessionId, Path projectPath, Path pluginRoot,
    Path claudeConfigPath)
  {
    super(projectPath, pluginRoot, claudeConfigPath);
    requireThat(sessionId, "sessionId").isNotBlank();
    this.sessionId = sessionId;
  }

  @Override
  public String getSessionId()
  {
    ensureOpen();
    return sessionId;
  }
}
