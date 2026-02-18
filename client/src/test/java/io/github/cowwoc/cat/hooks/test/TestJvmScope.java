/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.prompt.UserIssues;
import io.github.cowwoc.cat.hooks.read.post.DetectSequentialTools;
import io.github.cowwoc.cat.hooks.read.pre.PredictBatchOpportunity;
import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test implementation of JvmScope with injectable environment paths.
 * <p>
 * Accepts {@code claudeProjectDir} and {@code claudePluginRoot} as constructor parameters
 * so tests can point to temporary directories populated with test data.
 */
public final class TestJvmScope implements JvmScope
{
  private final ConcurrentLazyReference<JsonMapper> jsonMapper = ConcurrentLazyReference.create(() ->
    JsonMapper.builder().
      enable(SerializationFeature.INDENT_OUTPUT).
      build());
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
  private final ConcurrentLazyReference<DetectSequentialTools> detectSequentialTools =
    ConcurrentLazyReference.create(() -> new DetectSequentialTools(this));
  private final ConcurrentLazyReference<PredictBatchOpportunity> predictBatchOpportunity =
    ConcurrentLazyReference.create(() -> new PredictBatchOpportunity(this));
  private final ConcurrentLazyReference<UserIssues> userIssues =
    ConcurrentLazyReference.create(() -> new UserIssues(this));
  private final Path claudeProjectDir;
  private final Path claudePluginRoot;
  private final Path claudeConfigDir;
  private final String claudeSessionId;
  private final Path claudeEnvFile;
  private final TerminalType terminalType;
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a new test JVM scope with auto-generated temporary directories.
   */
  public TestJvmScope()
  {
    try
    {
      this.claudeProjectDir = Files.createTempDirectory("test-project");
      this.claudePluginRoot = Files.createTempDirectory("test-plugin");
      this.claudeConfigDir = Files.createTempDirectory("test-config");
      this.claudeSessionId = "test-session";
      this.claudeEnvFile = Files.createTempFile("test-env", ".sh");
      this.terminalType = TerminalType.WINDOWS_TERMINAL;

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
   * @param claudeProjectDir the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @throws NullPointerException if {@code claudeProjectDir} or {@code claudePluginRoot} are null
   */
  public TestJvmScope(Path claudeProjectDir, Path claudePluginRoot)
  {
    requireThat(claudeProjectDir, "claudeProjectDir").isNotNull();
    requireThat(claudePluginRoot, "claudePluginRoot").isNotNull();
    this.claudeProjectDir = claudeProjectDir;
    this.claudePluginRoot = claudePluginRoot;
    this.claudeConfigDir = claudeProjectDir;
    this.claudeSessionId = "test-session";
    try
    {
      this.claudeEnvFile = Files.createTempFile("test-env", ".sh");
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
   * Creates a new test JVM scope with full configuration.
   *
   * @param claudeProjectDir the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @param claudeSessionId the session ID
   * @param claudeEnvFile the environment file path
   * @param terminalType the terminal type
   * @throws NullPointerException if {@code claudeProjectDir}, {@code claudePluginRoot},
   *   {@code claudeSessionId}, {@code claudeEnvFile}, or {@code terminalType} are null
   */
  public TestJvmScope(Path claudeProjectDir, Path claudePluginRoot, String claudeSessionId,
    Path claudeEnvFile, TerminalType terminalType)
  {
    requireThat(claudeProjectDir, "claudeProjectDir").isNotNull();
    requireThat(claudePluginRoot, "claudePluginRoot").isNotNull();
    requireThat(claudeSessionId, "claudeSessionId").isNotNull();
    requireThat(claudeEnvFile, "claudeEnvFile").isNotNull();
    requireThat(terminalType, "terminalType").isNotNull();
    this.claudeProjectDir = claudeProjectDir;
    this.claudePluginRoot = claudePluginRoot;
    this.claudeConfigDir = claudeProjectDir;
    this.claudeSessionId = claudeSessionId;
    this.claudeEnvFile = claudeEnvFile;
    this.terminalType = terminalType;
  }

  @Override
  public JsonMapper getJsonMapper()
  {
    ensureOpen();
    return jsonMapper.getValue();
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    ensureOpen();
    return displayUtils.getValue();
  }

  @Override
  public Path getClaudeProjectDir()
  {
    ensureOpen();
    return claudeProjectDir;
  }

  @Override
  public Path getClaudePluginRoot()
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
  public String getClaudeSessionId()
  {
    ensureOpen();
    return claudeSessionId;
  }

  @Override
  public Path getClaudeEnvFile()
  {
    ensureOpen();
    return claudeEnvFile;
  }

  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return terminalType;
  }

  @Override
  public DetectSequentialTools getDetectSequentialTools()
  {
    ensureOpen();
    return detectSequentialTools.getValue();
  }

  @Override
  public PredictBatchOpportunity getPredictBatchOpportunity()
  {
    ensureOpen();
    return predictBatchOpportunity.getValue();
  }

  @Override
  public UserIssues getUserIssues()
  {
    ensureOpen();
    return userIssues.getValue();
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
    // Check if emoji-widths.json already exists in the target directory
    Path targetEmojiFile = claudePluginRoot.resolve("emoji-widths.json");
    if (Files.exists(targetEmojiFile))
    {
      return;  // Already exists, no need to copy
    }

    // Use user.dir system property to find the current working directory at test execution time
    String userDir = System.getProperty("user.dir");
    Path basePath = Path.of(userDir);

    // Try to find emoji-widths.json in the plugin directory
    // It should be at basePath/plugin/emoji-widths.json when running from hooks directory
    // or basePath/../plugin/emoji-widths.json when running from another location
    //
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

    Files.copy(sourceEmojiFile, targetEmojiFile);
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
