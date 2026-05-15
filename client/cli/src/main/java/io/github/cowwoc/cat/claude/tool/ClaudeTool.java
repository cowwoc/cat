/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool;

import io.github.cowwoc.cat.claude.hook.ClaudePluginScope;
import io.github.cowwoc.cat.tool.CliTool;

import java.nio.file.Path;

/**
 * A {@link ClaudePluginScope} that additionally exposes the Claude session environment values a tool process
 * receives at startup.
 * <p>
 * Implementations read these values from environment variables set by Claude Code when
 * spawning CLI tool processes.
 */
public interface ClaudeTool extends ClaudePluginScope, CliTool
{
  @Override
  default Path getClaudeConfigPath()
  {
    return getConfigPath();
  }

  @Override
  default Path getClaudeSessionsPath()
  {
    return getSessionsPath();
  }

  @Override
  default Path getClaudeSessionPath(String sessionId)
  {
    return getSessionPath(sessionId);
  }
}
