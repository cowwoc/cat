/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.TerminalType;
import io.github.cowwoc.cat.codex.hook.AbstractCodexHook;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Test implementation of a Codex hook scope with injectable environment paths.
 */
public final class TestCodexHook extends AbstractCodexHook
{
  private final Path workDir;

  /**
   * Creates a new test Codex hook scope.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param pluginData the plugin data directory path
   * @throws NullPointerException if any argument is null
   */
  public TestCodexHook(Path projectPath, Path pluginRoot, Path pluginData)
  {
    super(projectPath, pluginRoot, pluginData);
    requireThat(projectPath, "projectPath").isNotNull().isAbsolute();
    requireThat(pluginRoot, "pluginRoot").isNotNull().isAbsolute();
    requireThat(pluginData, "pluginData").isNotNull().isAbsolute();
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

  /**
   * Returns the working directory used by hook code under test.
   *
   * @return the working directory path
   */
  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return workDir;
  }

  /**
   * Returns a deterministic terminal type for tests.
   *
   * @return the terminal type
   */
  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return TerminalType.WINDOWS_TERMINAL;
  }

  /**
   * Returns the deterministic timezone used by tests.
   *
   * @return the timezone identifier
   */
  @Override
  public String getTimezone()
  {
    ensureOpen();
    return "UTC";
  }
}
