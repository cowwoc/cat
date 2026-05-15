/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.tool.DisplayUtils;
import io.github.cowwoc.cat.agent.AbstractAgentPluginScope;
import io.github.cowwoc.cat.claude.hook.prompt.UserIssues;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Abstract base class that consolidates the plugin-context fields and methods shared by both
 * {@link AbstractClaudeHook} and {@link AbstractClaudeTool}.
 * <p>
 * Stores the plugin root directory, plugin prefix, Claude config directory, and display utilities.
 * Subclasses add hook-specific or tool-specific behavior.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudePluginScope extends AbstractAgentPluginScope
  implements ClaudePluginScope
{
  private final Path claudeConfigPath;
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<UserIssues> userIssues =
    ConcurrentLazyReference.create(() -> new UserIssues(this));
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<DisplayUtils> displayUtils = ConcurrentLazyReference.create(() ->
  {
    try
    {
      return new DisplayUtils(this);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  });

  /**
   * Creates a new abstract plugin scope with the given infrastructure paths.
   *
   * @param projectPath the project's root directory
   * @param pluginRoot the Claude plugin root directory
   * @param pluginData the Claude plugin data directory
   * @param claudeConfigPath the Claude config directory
   * @throws NullPointerException if any parameter is null
   */
  protected AbstractClaudePluginScope(Path projectPath, Path pluginRoot, Path pluginData,
    Path claudeConfigPath)
  {
    super(projectPath, pluginRoot, pluginData,
      Path.of(".claude-plugin/plugin.json"),
      List.of(
        pluginRoot.resolve("rules/common"),
        pluginRoot.resolve("rules/claude"),
        projectPath.resolve(".cat/rules/common"),
        projectPath.resolve(".cat/rules/claude"),
        projectPath.resolve(".claude/rules")),
      null);
    requireThat(claudeConfigPath, "claudeConfigPath").isNotNull();
    this.claudeConfigPath = claudeConfigPath;
  }

  @Override
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

  @Override
  public DisplayUtils getDisplayUtils()
  {
    ensureOpen();
    return displayUtils.getValue();
  }

  @Override
  public UserIssues getUserIssues()
  {
    ensureOpen();
    return userIssues.getValue();
  }
}
