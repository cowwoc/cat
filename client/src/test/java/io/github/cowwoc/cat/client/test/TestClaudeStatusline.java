/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.AbstractClaudeStatusline;
import io.github.cowwoc.cat.claude.hook.skills.TerminalType;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Test implementation of {@link io.github.cowwoc.cat.claude.hook.ClaudeStatusline} with injectable
 * environment paths.
 * <p>
 * Accepts {@code claudeProjectPath} and {@code claudePluginRoot} as constructor parameters so tests
 * can point to temporary directories populated with test data. Optionally accepts a JSON string to
 * parse statusline data at construction time via the superclass
 * {@link AbstractClaudeStatusline#AbstractClaudeStatusline(Path, java.io.InputStream)} constructor.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class TestClaudeStatusline extends AbstractClaudeStatusline
{
  private final Path workDir;
  private final TerminalType terminalType;

  /**
   * Creates a new test Claude statusline scope with default field values.
   * <p>
   * Use this constructor for tests that validate argument handling or other behavior that does not
   * require parsed statusline data.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path (used only for emoji-widths.json)
   * @throws NullPointerException if {@code claudeProjectPath} or {@code claudePluginRoot} are null
   */
  public TestClaudeStatusline(Path claudeProjectPath, Path claudePluginRoot)
  {
    super(claudeProjectPath);
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
   * @param claudePluginRoot the plugin root directory path (used only for emoji-widths.json)
   * @param json the Claude Code hook JSON string to parse
   * @throws NullPointerException if {@code claudeProjectPath}, {@code claudePluginRoot}, or
   *   {@code json} are null
   * @throws IOException if an I/O error occurs
   */
  public TestClaudeStatusline(Path claudeProjectPath, Path claudePluginRoot, String json)
    throws IOException
  {
    super(claudeProjectPath);
    requireThat(json, "json").isNotNull();
    this.workDir = claudeProjectPath;
    this.terminalType = TerminalType.WINDOWS_TERMINAL;
    TestScopeUtils.copyEmojiWidthsIfNeeded(claudePluginRoot);
    // Parse after all fields are initialized to avoid this-escape issues with ensureOpen()
    parseStatuslineJson(json);
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
    return terminalType;
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return "UTC";
  }
}
