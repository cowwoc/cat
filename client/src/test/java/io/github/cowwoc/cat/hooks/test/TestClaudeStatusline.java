/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AbstractClaudeStatusline;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test implementation of {@link io.github.cowwoc.cat.hooks.ClaudeStatusline} with injectable
 * environment paths.
 * <p>
 * Accepts {@code claudeProjectPath} and {@code claudePluginRoot} as constructor parameters so tests
 * can point to temporary directories populated with test data. Optionally accepts a JSON string to
 * parse statusline data at construction time via the superclass
 * {@link AbstractClaudeStatusline#AbstractClaudeStatusline(java.io.InputStream)} constructor.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class TestClaudeStatusline extends AbstractClaudeStatusline
{
  private final Path claudeConfigPath;
  private final Path projectPath;
  private final Path pluginRoot;
  private final Path workDir;
  private final TerminalType terminalType;
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a new test Claude statusline scope with default field values.
   * <p>
   * Use this constructor for tests that validate argument handling or other behavior that does not
   * require parsed statusline data.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @throws NullPointerException if {@code claudeProjectPath} or {@code claudePluginRoot} are null
   */
  public TestClaudeStatusline(Path claudeProjectPath, Path claudePluginRoot)
  {
    requireThat(claudeProjectPath, "claudeProjectPath").isNotNull();
    requireThat(claudePluginRoot, "claudePluginRoot").isNotNull();
    this.projectPath = claudeProjectPath;
    this.pluginRoot = claudePluginRoot;
    this.claudeConfigPath = claudeProjectPath;
    this.workDir = claudeProjectPath;
    this.terminalType = TerminalType.WINDOWS_TERMINAL;
    try
    {
      TestScopeUtils.copyEmojiWidthsIfNeeded(claudePluginRoot);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a new test Claude statusline scope, parsing the given JSON string to populate statusline
   * fields.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param json the Claude Code hook JSON string to parse
   * @throws NullPointerException if {@code claudeProjectPath}, {@code claudePluginRoot}, or
   *   {@code json} are null
   * @throws IOException if an I/O error occurs
   */
  public TestClaudeStatusline(Path claudeProjectPath, Path claudePluginRoot, String json)
    throws IOException
  {
    requireThat(claudeProjectPath, "claudeProjectPath").isNotNull();
    requireThat(claudePluginRoot, "claudePluginRoot").isNotNull();
    requireThat(json, "json").isNotNull();
    this.projectPath = claudeProjectPath;
    this.pluginRoot = claudePluginRoot;
    this.claudeConfigPath = claudeProjectPath;
    this.workDir = claudeProjectPath;
    this.terminalType = TerminalType.WINDOWS_TERMINAL;
    TestScopeUtils.copyEmojiWidthsIfNeeded(claudePluginRoot);
    // Parse after all fields are initialized to avoid this-escape issues with ensureOpen()
    parseStatuslineJson(json);
  }

  @Override
  public Path getProjectPath()
  {
    ensureOpen();
    return projectPath;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return pluginRoot;
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return workDir;
  }

  @Override
  protected Path getClaudeConfigPath()
  {
    ensureOpen();
    return claudeConfigPath;
  }

  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return terminalType;
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return "UTC";
  }

  @Override
  public boolean isClosed()
  {
    return closed.get();
  }

  @Override
  public void close()
  {
    closed.set(true);
  }
}
