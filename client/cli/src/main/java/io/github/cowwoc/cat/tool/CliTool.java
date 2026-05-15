/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import io.github.cowwoc.cat.agent.AgentPluginScope;

import java.nio.file.Path;

/**
 * Runtime-neutral CLI scope for shared CAT command-line utilities.
 */
public interface CliTool extends AgentPluginScope, JvmScope
{
  /**
   * Returns the CAT session ID.
   *
   * @return the session ID
   * @throws IllegalStateException if this scope is closed
   */
  String getSessionId();

  /**
   * Returns the active runtime config directory.
   *
   * @return the config directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getConfigPath();

  /**
   * Returns the active runtime sessions directory.
   *
   * @return the sessions directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getSessionsPath();

  /**
   * Returns the active runtime session directory.
   *
   * @param sessionId the session ID
   * @return the session directory path
   * @throws NullPointerException if {@code sessionId} is null
   * @throws IllegalStateException if this scope is closed
   */
  Path getSessionPath(String sessionId);

  /**
   * Returns the display utilities singleton.
   *
   * @return the display utilities
   * @throws IllegalStateException if this scope is closed
   */
  DisplayUtils getDisplayUtils();

  /**
   * Returns the plugin.json URL used by the update checker.
   *
   * @return the plugin.json URL, or empty string if not set
   * @throws IllegalStateException if this scope is closed
   */
  String getPluginJsonUrl();
}
