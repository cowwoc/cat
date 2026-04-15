/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import io.github.cowwoc.cat.claude.hook.skills.DisplayUtils;

import java.nio.file.Path;

/**
 * A {@link JvmScope} that provides plugin-context methods shared by both {@link ClaudeHook} and
 * {@link ClaudeTool}, but not required by {@link ClaudeStatusline}.
 * <p>
 * Exposes the plugin root directory, plugin prefix, Claude config directory, session paths,
 * and display utilities.
 */
public interface ClaudePluginScope extends JvmScope
{
  /**
   * Returns the Claude plugin root directory.
   *
   * @return the plugin root directory path
   * @throws AssertionError if {@code CLAUDE_PLUGIN_ROOT} is not set in the environment
   * @throws IllegalStateException if this scope is closed
   */
  Path getPluginRoot();

  /**
   * Returns the plugin marketplace prefix ({@code "cat"}).
   *
   * @return the plugin prefix, never blank
   * @throws IllegalStateException if this scope is closed
   */
  String getPluginPrefix();

  /**
   * Returns the Claude config directory (typically {@code ~/.claude}).
   *
   * @return the config directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeConfigPath();

  /**
   * Returns the base directory for session JSONL files.
   * <p>
   * Session files are stored at {@code {claudeSessionsPath}/{sessionId}.jsonl}.
   *
   * @return the session base directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeSessionsPath();

  /**
   * Returns the directory for a session's tracking files.
   * <p>
   * Located at {@code {claudeConfigPath}/projects/{encodedProjectRoot}/{sessionId}/}.
   *
   * @param sessionId the session ID
   * @return the session directory path
   * @throws NullPointerException if {@code sessionId} is null
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeSessionPath(String sessionId);

  /**
   * Returns the display utilities singleton.
   *
   * @return the display utilities
   * @throws IllegalStateException if this scope is closed
   */
  DisplayUtils getDisplayUtils();

  /**
   * Returns the Anthropic API base URL from the {@code ANTHROPIC_BASE_URL} environment variable.
   * <p>
   * Returns empty string if the variable is not set.
   *
   * @return the base URL, or empty string if not set
   * @throws IllegalStateException if this scope is closed
   */
  String getAnthropicBaseUrl();
}
