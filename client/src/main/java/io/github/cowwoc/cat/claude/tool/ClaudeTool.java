/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool;

import io.github.cowwoc.cat.claude.hook.ClaudePluginScope;

/**
 * A {@link ClaudePluginScope} that additionally exposes the Claude session environment values a tool process
 * receives at startup.
 * <p>
 * Implementations read these values from environment variables set by Claude Code when
 * spawning CLI tool processes.
 */
public interface ClaudeTool extends ClaudePluginScope
{
  /**
   * Returns the Claude session ID.
   *
   * @return the session ID
   * @throws AssertionError if {@code CLAUDE_SESSION_ID} is not set in the environment
   * @throws IllegalStateException if this scope is closed
   */
  String getSessionId();
}
