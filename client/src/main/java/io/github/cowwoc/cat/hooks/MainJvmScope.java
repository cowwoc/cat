/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production implementation of {@link JvmScope} that reads environment configuration from process
 * environment variables via {@link ConcurrentLazyReference}.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainJvmScope extends AbstractJvmScope
{
  private final ConcurrentLazyReference<Path> claudeProjectPath = ConcurrentLazyReference.create(() ->
  {
    String projectPath = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectPath == null || projectPath.isEmpty())
      throw new AssertionError("CLAUDE_PROJECT_DIR is not set");
    return Path.of(projectPath);
  });
  private final ConcurrentLazyReference<Path> claudePluginRoot = ConcurrentLazyReference.create(() ->
  {
    String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
    if (pluginRoot == null || pluginRoot.isEmpty())
      throw new AssertionError("CLAUDE_PLUGIN_ROOT is not set");
    return Path.of(pluginRoot);
  });
  private final ConcurrentLazyReference<Path> claudeConfigDir = ConcurrentLazyReference.create(() ->
  {
    String configDir = System.getenv("CLAUDE_CONFIG_DIR");
    if (configDir != null && !configDir.isEmpty())
      return Path.of(configDir);
    return Path.of(System.getProperty("user.home"), ".claude");
  });
  private final ConcurrentLazyReference<TerminalType> terminalType =
    ConcurrentLazyReference.create(TerminalType::detect);
  private final ConcurrentLazyReference<String> tz = ConcurrentLazyReference.create(() ->
  {
    String tzValue = System.getenv("TZ");
    if (tzValue == null || tzValue.isEmpty())
      return "UTC";
    return tzValue;
  });
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a new production JVM scope.
   */
  public MainJvmScope()
  {
  }

  @Override
  public Path getProjectPath()
  {
    ensureOpen();
    return claudeProjectPath.getValue();
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return Path.of(System.getProperty("user.dir"));
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return claudePluginRoot.getValue();
  }

  @Override
  public Path getClaudeConfigDir()
  {
    ensureOpen();
    return claudeConfigDir.getValue();
  }

  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return terminalType.getValue();
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return tz.getValue();
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
