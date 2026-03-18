/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AbstractJvmScope;
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
public final class TestJvmScope extends AbstractJvmScope
{
  private final Path claudeProjectPath;
  private final Path claudePluginRoot;
  private final Path claudeConfigDir;
  private final TerminalType terminalType;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Path workDir;

  /**
   * Creates a new test JVM scope with auto-generated temporary directories.
   */
  public TestJvmScope()
  {
    try
    {
      this.claudeProjectPath = Files.createTempDirectory("test-project");
      this.claudePluginRoot = Files.createTempDirectory("test-plugin");
      this.claudeConfigDir = Files.createTempDirectory("test-config");
      this.terminalType = TerminalType.WINDOWS_TERMINAL;
      this.workDir = claudeProjectPath;

      // Copy emoji-widths.json from plugin directory to temporary plugin root
      copyEmojiWidthsIfNeeded(claudePluginRoot);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a new test JVM scope.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @throws NullPointerException if {@code claudeProjectPath} or {@code claudePluginRoot} are null
   */
  public TestJvmScope(Path claudeProjectPath, Path claudePluginRoot)
  {
    this(claudeProjectPath, claudePluginRoot, claudeProjectPath);
  }

  /**
   * Creates a new test JVM scope with independent workDir.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param workDir the work directory path (can differ from claudeProjectPath)
   * @throws NullPointerException if {@code claudeProjectPath}, {@code claudePluginRoot}, or {@code workDir} are null
   * @throws IllegalArgumentException if {@code workDir} is not an absolute path
   */
  public TestJvmScope(Path claudeProjectPath, Path claudePluginRoot, Path workDir)
  {
    requireThat(claudeProjectPath, "claudeProjectPath").isNotNull();
    requireThat(claudePluginRoot, "claudePluginRoot").isNotNull();
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
    this.claudeProjectPath = claudeProjectPath;
    this.claudePluginRoot = claudePluginRoot;
    this.claudeConfigDir = claudeProjectPath;
    this.workDir = workDir;
    try
    {
      // Copy emoji-widths.json to the plugin root if it's a temporary directory
      copyEmojiWidthsIfNeeded(claudePluginRoot);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    this.terminalType = TerminalType.WINDOWS_TERMINAL;
  }

  /**
   * Creates a new test JVM scope with a specified terminal type.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param terminalType the terminal type to use
   * @throws NullPointerException if {@code claudeProjectPath}, {@code claudePluginRoot},
   *   or {@code terminalType} are null
   */
  public TestJvmScope(Path claudeProjectPath, Path claudePluginRoot, TerminalType terminalType)
  {
    this(claudeProjectPath, claudePluginRoot, terminalType, claudeProjectPath);
  }

  /**
   * Creates a new test JVM scope with a specified terminal type and independent workDir.
   *
   * @param claudeProjectPath the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param terminalType the terminal type to use
   * @param workDir the work directory path (can differ from claudeProjectPath)
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if {@code workDir} is not an absolute path
   */
  public TestJvmScope(Path claudeProjectPath, Path claudePluginRoot, TerminalType terminalType, Path workDir)
  {
    requireThat(claudeProjectPath, "claudeProjectPath").isNotNull();
    requireThat(claudePluginRoot, "claudePluginRoot").isNotNull();
    requireThat(terminalType, "terminalType").isNotNull();
    requireThat(workDir, "workDir").isNotNull().isAbsolute();
    this.claudeProjectPath = claudeProjectPath;
    this.claudePluginRoot = claudePluginRoot;
    this.claudeConfigDir = claudeProjectPath;
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

  @Override
  public String getPluginPrefix()
  {
    ensureOpen();
    return "cat";
  }

  @Override
  public Path getProjectPath()
  {
    ensureOpen();
    return claudeProjectPath;
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return workDir;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return claudePluginRoot;
  }

  @Override
  public Path getClaudeConfigDir()
  {
    ensureOpen();
    return claudeConfigDir;
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
   * Copies emoji-widths.json from the plugin directory to the given plugin root directory.
   * <p>
   * This ensures that temporary directories used for testing have the required emoji widths file.
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
    if (Files.exists(targetEmojiFile))
    {
      return;  // Already exists, no need to copy
    }

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
    {
      throw new IOException("emoji-widths.json not found in any of the expected locations");
    }

    try
    {
      Files.copy(sourceEmojiFile, targetEmojiFile);
    }
    catch (java.nio.file.FileAlreadyExistsException _)
    {
      // Another thread already copied the file — the goal is achieved
    }
  }

  /**
   * Finds the workspace root by traversing up from the current directory.
   * <p>
   * Looks for a directory containing both "plugin/" and "hooks/" subdirectories.
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
