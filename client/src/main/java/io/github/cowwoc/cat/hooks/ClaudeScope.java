/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.nio.file.Path;

/**
 * A {@link JvmScope} that additionally provides Claude-specific path configuration.
 * <p>
 * Adds the Claude config directory and session path resolution to the base JVM scope.
 */
public interface ClaudeScope extends JvmScope
{
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
}
