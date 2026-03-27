/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.nio.file.Path;

/**
 * A {@link JvmScope} that additionally exposes the Claude session environment values a tool process
 * receives at startup.
 * <p>
 * Implementations read these values from environment variables set by Claude Code when
 * spawning CLI tool processes.
 */
public interface ClaudeTool extends ClaudeScope
{
  /**
   * Returns the Claude session ID.
   *
   * @return the session ID
   * @throws AssertionError if {@code CLAUDE_SESSION_ID} is not set in the environment
   * @throws IllegalStateException if this scope is closed
   */
  String getSessionId();

  /**
   * Returns the project's root directory.
   *
   * @return the project directory path
   * @throws AssertionError if {@code CLAUDE_PROJECT_DIR} is not set in the environment
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  Path getProjectPath();

  /**
   * Returns the Claude plugin root directory.
   *
   * @return the plugin root directory path
   * @throws AssertionError if {@code CLAUDE_PLUGIN_ROOT} is not set in the environment
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  Path getPluginRoot();
}
