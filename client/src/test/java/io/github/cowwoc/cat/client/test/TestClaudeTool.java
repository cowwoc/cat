/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.tool.AbstractClaudeTool;
import io.github.cowwoc.cat.claude.hook.skills.TerminalType;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test implementation of JvmScope with injectable environment paths.
 * <p>
 * Accepts {@code claudeProjectPath} and {@code claudePluginRoot} as constructor parameters
 * so tests can point to temporary directories populated with test data.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class TestClaudeTool extends AbstractClaudeTool
{
  private static final String SESSION_ID = "00000000-0000-0000-0000-000000000000";

  private final TerminalType terminalType;
  private final Path workDir;

  /**
   * Creates a new test Claude tool scope with auto-generated temporary directories.
   * <p>
   * The caller is responsible for deleting the temporary directories when the scope is no
   * longer needed (e.g., in a {@code finally} block or try-with-resources pattern).
   */
  protected TestClaudeTool()
  {
    this(createTempDirs());
  }

  /**
   * Creates a new test Claude tool scope from a pre-built environment bundle.
   *
   * @param bundle the environment paths bundle
   */
  private TestClaudeTool(TempDirBundle bundle)
  {
    super(SESSION_ID, bundle.projectPath(), bundle.pluginRoot(), bundle.claudeConfigPath());
    this.terminalType = TerminalType.WINDOWS_TERMINAL;
    this.workDir = bundle.projectPath();
    try
    {
      TestScopeUtils.copyEmojiWidthsIfNeeded(bundle.pluginRoot());
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a new test Claude tool scope.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @throws NullPointerException if {@code claudeProjectPath} or {@code claudePluginRoot} are null
   */
  protected TestClaudeTool(Path claudeProjectPath, Path claudePluginRoot)
  {
    this(claudeProjectPath, claudePluginRoot, claudeProjectPath);
  }

  /**
   * Creates a new test Claude tool scope with independent workDir.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param workDir the work directory path (can differ from claudeProjectPath)
   * @throws NullPointerException if {@code claudeProjectPath}, {@code claudePluginRoot}, or {@code workDir} are null
   * @throws IllegalArgumentException if {@code workDir} is not an absolute path
   */
  protected TestClaudeTool(Path claudeProjectPath, Path claudePluginRoot, Path workDir)
  {
    super(SESSION_ID, claudeProjectPath, claudePluginRoot, claudeProjectPath);
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
    this.workDir = workDir;
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
   * Creates a new test Claude tool scope with a specified terminal type.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param terminalType the terminal type to use
   * @throws NullPointerException if {@code claudeProjectPath}, {@code claudePluginRoot},
   *   or {@code terminalType} are null
   */
  protected TestClaudeTool(Path claudeProjectPath, Path claudePluginRoot, TerminalType terminalType)
  {
    this(claudeProjectPath, claudePluginRoot, terminalType, claudeProjectPath);
  }

  /**
   * Creates a new test Claude tool scope with a specified terminal type and independent workDir.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param terminalType the terminal type to use
   * @param workDir the work directory path (can differ from claudeProjectPath)
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if {@code workDir} is not an absolute path
   */
  protected TestClaudeTool(Path claudeProjectPath, Path claudePluginRoot, TerminalType terminalType,
    Path workDir)
  {
    super(SESSION_ID, claudeProjectPath, claudePluginRoot, claudeProjectPath);
    requireThat(terminalType, "terminalType").isNotNull();
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
    this.terminalType = terminalType;
    this.workDir = workDir;
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
   * Creates temporary directories for the no-arg constructor.
   *
   * @return a bundle containing the created paths
   */
  private static TempDirBundle createTempDirs()
  {
    try
    {
      Path projectPath = Files.createTempDirectory("test-project");
      Path pluginRoot = Files.createTempDirectory("test-plugin");
      Path configDir = Files.createTempDirectory("test-config");
      return new TempDirBundle(projectPath, pluginRoot, configDir);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Holds the temporary directory paths for the no-arg constructor.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the config directory path
   */
  private record TempDirBundle(Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    /**
     * Creates a new bundle.
     *
     * @param projectPath the project directory path
     * @param pluginRoot the plugin root directory path
     * @param claudeConfigPath the config directory path
     * @throws NullPointerException if {@code projectPath}, {@code pluginRoot}, or {@code claudeConfigPath} are null
     */
    TempDirBundle
    {
      requireThat(projectPath, "projectPath").isNotNull();
      requireThat(pluginRoot, "pluginRoot").isNotNull();
      requireThat(claudeConfigPath, "claudeConfigPath").isNotNull();
    }
  }

  @Override
  public String getPluginPrefix()
  {
    ensureOpen();
    return "cat";
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
