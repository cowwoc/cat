/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AbstractAgentPluginScope;
import io.github.cowwoc.cat.agent.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Shared base implementation for CLI scopes.
 * <p>
 * Runtime-specific subclasses provide resolved plugin descriptor and rule directory values.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractCliTool extends AbstractAgentPluginScope implements CliTool
{
  private final ConcurrentLazyReference<TerminalType> terminalTypeRef =
    ConcurrentLazyReference.create(TerminalType::detect);
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
  private final String sessionId;
  private final Path configPath;
  private final Path workDir;
  private final String timezone;
  private final String pluginJsonUrl;

  /**
   * Creates a new CLI scope from resolved values.
   *
   * @param config the resolved CLI scope configuration
   */
  protected AbstractCliTool(CliToolConfig config)
  {
    super(requireConfig(config).projectPath(), config.pluginRoot(), config.pluginData(),
      config.pluginDescriptor(), config.ruleDirectories(), config.pluginCacheDescriptor());
    this.sessionId = config.sessionId();
    this.configPath = config.configPath();
    this.workDir = config.workDir().toAbsolutePath();
    this.timezone = config.timezone();
    this.pluginJsonUrl = config.pluginJsonUrl();
  }

  /**
   * Validates a CLI tool configuration before superclass construction.
   *
   * @param config the configuration to validate
   * @return {@code config}
   */
  private static CliToolConfig requireConfig(CliToolConfig config)
  {
    requireThat(config, "config").isNotNull();
    return config;
  }

  @Override
  public String getSessionId()
  {
    ensureOpen();
    return sessionId;
  }

  @Override
  public Path getConfigPath()
  {
    ensureOpen();
    return configPath;
  }

  @Override
  public Path getSessionsPath()
  {
    ensureOpen();
    return configPath.resolve("projects").resolve(encodeProjectPath(getProjectPath().toString()));
  }

  @Override
  public Path getSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return getSessionsPath().resolve(sessionId);
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    ensureOpen();
    return displayUtils.getValue();
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
    return terminalTypeRef.getValue();
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return timezone;
  }

  @Override
  public String getPluginJsonUrl()
  {
    ensureOpen();
    return pluginJsonUrl;
  }
}
