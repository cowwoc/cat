/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool;

import io.github.cowwoc.cat.agent.AgentRuntime;
import io.github.cowwoc.cat.claude.hook.prompt.UserIssues;
import io.github.cowwoc.cat.tool.AbstractCliTool;
import io.github.cowwoc.cat.tool.CliToolConfig;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

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
public abstract class AbstractClaudeTool extends AbstractCliTool implements ClaudeTool
{
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<UserIssues> userIssues =
    ConcurrentLazyReference.create(() -> new UserIssues(this));

  /**
   * Creates a new abstract Claude tool scope with the given environment values.
   *
   * @param sessionId the Claude session ID
   * @param projectPath the project's root directory path
   * @param pluginRoot the Claude plugin root directory path
   * @param pluginData the Claude plugin data directory path
   * @param claudeConfigPath the Claude config directory path
   * @throws IllegalArgumentException if {@code sessionId} is blank
   * @throws NullPointerException if {@code projectPath}, {@code pluginRoot}, {@code pluginData}, or
   *   {@code claudeConfigPath} are null
   */
  protected AbstractClaudeTool(String sessionId, Path projectPath, Path pluginRoot,
    Path pluginData, Path claudeConfigPath)
  {
    super(new CliToolConfig(sessionId, projectPath, pluginRoot, pluginData, claudeConfigPath,
      AgentRuntime.CLAUDE.pluginDescriptor(), AgentRuntime.CLAUDE.ruleDirectories(projectPath, pluginRoot),
      AgentRuntime.CLAUDE.pluginCacheDescriptor(), Path.of(System.getProperty("user.dir")), "UTC", ""));
  }

  @Override
  public UserIssues getUserIssues()
  {
    ensureOpen();
    return userIssues.getValue();
  }
}
