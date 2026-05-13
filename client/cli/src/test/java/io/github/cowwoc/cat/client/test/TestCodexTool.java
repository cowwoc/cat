/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AbstractAgentPluginScope;
import io.github.cowwoc.cat.agent.TerminalType;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Test implementation of a Codex plugin scope with injectable environment paths.
 */
public final class TestCodexTool extends AbstractAgentPluginScope
{
  private final Path workDir;

  /**
   * Creates a new test Codex tool scope.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @throws NullPointerException if any argument is null
   */
  public TestCodexTool(Path projectPath, Path pluginRoot)
  {
    super(projectPath, pluginRoot, pluginRoot,
      Path.of(".codex-plugin/plugin.json"),
      List.of(
        pluginRoot.resolve("rules/common"),
        pluginRoot.resolve("rules/codex"),
        projectPath.resolve(".cat/rules/common"),
        projectPath.resolve(".cat/rules/codex")),
      Path.of(".codex-plugin/plugin.json"));
    requireThat(projectPath, "projectPath").isNotNull().isAbsolute();
    requireThat(pluginRoot, "pluginRoot").isNotNull().isAbsolute();
    this.workDir = projectPath;
    try
    {
      TestScopeUtils.copyEmojiWidthsIfNeeded(pluginRoot);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return workDir;
  }

  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return TerminalType.WINDOWS_TERMINAL;
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return "UTC";
  }
}
