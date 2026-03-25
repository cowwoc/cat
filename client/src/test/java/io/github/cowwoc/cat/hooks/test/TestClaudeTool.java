/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AbstractClaudeTool;
import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

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

  private final Path claudeConfigPath;
  private final TerminalType terminalType;
  private final AtomicBoolean closed = new AtomicBoolean();
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
    super(SESSION_ID, bundle.projectPath(), bundle.pluginRoot());
    this.claudeConfigPath = bundle.claudeConfigPath();
    this.terminalType = TerminalType.WINDOWS_TERMINAL;
    this.workDir = bundle.projectPath();
    try
    {
      copyEmojiWidthsIfNeeded(bundle.pluginRoot());
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
    super(SESSION_ID, claudeProjectPath, claudePluginRoot);
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
    this.claudeConfigPath = claudeProjectPath;
    this.workDir = workDir;
    this.terminalType = TerminalType.WINDOWS_TERMINAL;
    try
    {
      copyEmojiWidthsIfNeeded(claudePluginRoot);
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
    super(SESSION_ID, claudeProjectPath, claudePluginRoot);
    requireThat(terminalType, "terminalType").isNotNull();
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
    this.claudeConfigPath = claudeProjectPath;
    this.terminalType = terminalType;
    this.workDir = workDir;
    try
    {
      copyEmojiWidthsIfNeeded(claudePluginRoot);
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
  public Path getClaudeConfigPath()
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

  /**
   * Copies emoji-widths.json from the real plugin directory to the given temporary plugin root.
   * <p>
   * {@link DisplayUtils} requires emoji-widths.json at
   * initialization time. This method ensures that test-scope plugin directories contain the file
   * by copying it from the real workspace.
   * <p>
   * Uses the {@code user.dir} system property to locate the workspace root, then traverses
   * upward until a directory containing both {@code plugin/} and {@code client/} subdirectories
   * is found. This traversal accommodates different Maven working directories (e.g., running
   * tests from the {@code client/} subdirectory vs. the repository root).
   * <p>
   * The {@link java.nio.file.FileAlreadyExistsException} catch is intentional: concurrent test
   * threads may race to copy the same file into a shared temporary plugin directory. The first
   * writer wins; subsequent writers treat the already-existing file as success.
   *
   * @param claudePluginRoot the plugin root directory to copy the file to
   * @throws IOException if the emoji-widths.json file cannot be found or copied
   */
  private static void copyEmojiWidthsIfNeeded(Path claudePluginRoot) throws IOException
  {
    // Skip copying if the plugin root doesn't exist (e.g., tests that exercise non-existent paths)
    if (!Files.isDirectory(claudePluginRoot))
      return;

    // Check if emoji-widths.json already exists in the target directory
    Path targetEmojiFile = claudePluginRoot.resolve("emoji-widths.json");
    if (Files.notExists(targetEmojiFile))
    {
      // Use user.dir system property to find the current working directory at test execution time
      String userDir = System.getProperty("user.dir");
      Path basePath = Path.of(userDir);

      // Dynamically resolve workspace paths by traversing up from user.dir
      Path workspaceRoot = findWorkspaceRoot(basePath);
      Path[] possiblePaths = {
        basePath.resolve("plugin/emoji-widths.json"),
        basePath.resolve("../plugin/emoji-widths.json").normalize(),
        workspaceRoot.resolve("plugin/emoji-widths.json")
      };

      Path sourceEmojiFile = null;
      for (Path path : possiblePaths)
      {
        if (Files.exists(path))
        {
          sourceEmojiFile = path;
          break;
        }
      }

      if (sourceEmojiFile == null)
        throw new IOException("emoji-widths.json not found in any of the expected locations");

      try
      {
        Files.copy(sourceEmojiFile, targetEmojiFile);
      }
      catch (java.nio.file.FileAlreadyExistsException _)
      {
        // Another thread already copied the file — the goal is achieved
      }
    }
  }

  /**
   * Finds the workspace root by traversing up from the current directory.
   * <p>
   * Looks for a directory containing both "plugin/" and "client/" subdirectories.
   *
   * @param startPath the path to start searching from
   * @return the workspace root path
   * @throws IOException if the workspace root cannot be found
   */
  private static Path findWorkspaceRoot(Path startPath) throws IOException
  {
    Path current = startPath.toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.exists(current.resolve("plugin")) && Files.exists(current.resolve("client")))
      {
        return current;
      }
      current = current.getParent();
    }
    throw new IOException("Could not find workspace root from: " + startPath);
  }
}
