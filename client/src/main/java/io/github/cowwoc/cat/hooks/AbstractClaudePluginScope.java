/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstract base class that consolidates the plugin-context fields and methods shared by both
 * {@link AbstractClaudeHook} and {@link AbstractClaudeTool}.
 * <p>
 * Stores the plugin root directory, plugin prefix, Claude config directory, and display utilities.
 * Subclasses add hook-specific or tool-specific behavior.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudePluginScope extends AbstractJvmScope implements ClaudePluginScope
{
  private final Path pluginRoot;
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<String> pluginPrefix = ConcurrentLazyReference.create(
    this::derivePluginPrefix);
  private final Path claudeConfigPath;
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
   * @param claudeConfigPath the Claude config directory
   * @throws NullPointerException if any parameter is null
   */
  protected AbstractClaudePluginScope(Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    super(projectPath);
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(claudeConfigPath, "claudeConfigPath").isNotNull();
    this.pluginRoot = pluginRoot;
    this.claudeConfigPath = claudeConfigPath;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return pluginRoot;
  }

  @Override
  public String getPluginPrefix()
  {
    ensureOpen();
    return pluginPrefix.getValue();
  }

  /**
   * Derives the plugin prefix from the plugin root path structure.
   *
   * @return the plugin prefix, never blank
   * @throws AssertionError if the prefix cannot be derived from the plugin root path
   */
  private String derivePluginPrefix()
  {
    Path absolutePluginRoot = this.pluginRoot.toAbsolutePath().normalize();
    Path slugDir = absolutePluginRoot.getParent();
    if (slugDir == null)
      throw new AssertionError("Plugin root has no parent directory: " + absolutePluginRoot);
    Path prefixDir = slugDir.getParent();
    if (prefixDir == null)
      throw new AssertionError("Plugin slug directory has no parent: " + slugDir);
    Path prefixName = prefixDir.getFileName();
    if (prefixName == null)
    {
      throw new AssertionError("Cannot determine plugin prefix from path: " + absolutePluginRoot +
        ". Expected structure: .../{prefix}/{slug}/{version}/");
    }
    return prefixName.toString();
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
}
