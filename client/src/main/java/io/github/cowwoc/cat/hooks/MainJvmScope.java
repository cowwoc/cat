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
 * Production implementation of JvmScope.
 * <p>
 * Provides lazy-loaded DisplayUtils and environment paths via ConcurrentLazyReference
 * for thread-safe initialization. Reads {@code CLAUDE_PROJECT_DIR} and
 * {@code CLAUDE_PLUGIN_ROOT} from the process environment.
 */
public final class MainJvmScope extends AbstractJvmScope
{
  private final ConcurrentLazyReference<Path> claudeProjectDir = ConcurrentLazyReference.create(() ->
  {
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isEmpty())
      throw new AssertionError("CLAUDE_PROJECT_DIR is not set");
    return Path.of(projectDir);
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
  private final ConcurrentLazyReference<String> claudeSessionId = ConcurrentLazyReference.create(() ->
  {
    String sessionId = System.getenv("CLAUDE_SESSION_ID");
    if (sessionId == null || sessionId.isEmpty())
      throw new AssertionError("CLAUDE_SESSION_ID is not set");
    return sessionId;
  });
  private final ConcurrentLazyReference<Path> claudeEnvFile = ConcurrentLazyReference.create(() ->
  {
    String envFile = System.getenv("CLAUDE_ENV_FILE");
    if (envFile == null || envFile.isEmpty())
      throw new AssertionError("CLAUDE_ENV_FILE is not set");
    return Path.of(envFile);
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
  public Path getClaudeProjectDir()
  {
    ensureOpen();
    return claudeProjectDir.getValue();
  }

  @Override
  public Path getClaudePluginRoot()
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
  public String getClaudeSessionId()
  {
    ensureOpen();
    return claudeSessionId.getValue();
  }

  @Override
  public Path getClaudeEnvFile()
  {
    ensureOpen();
    return claudeEnvFile.getValue();
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
  public String getEnvironmentVariable(String name)
  {
    ensureOpen();
    return System.getenv(name);
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
