/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.nio.file.Path;

/**
 * Shared base implementation for runtime-specific plugin scopes.
 * <p>
 * Runtime modules provide their plugin descriptor and rule directories, while this class owns
 * common process-scoped values such as work directory, terminal detection, and timezone.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractRuntimeScope extends AbstractAgentPluginScope
{
  private final ConcurrentLazyReference<TerminalType> terminalTypeRef =
    ConcurrentLazyReference.create(TerminalType::detect);
  private final RuntimeScopeConfig config;

  /**
   * Creates a new runtime-aware plugin scope.
   *
   * @param config the resolved runtime scope configuration
   */
  protected AbstractRuntimeScope(RuntimeScopeConfig config)
  {
    super(requireConfig(config).projectPath(), config.pluginRoot(), config.pluginData(),
      config.pluginDescriptor(), config.ruleDirectories(), config.pluginCacheDescriptor());
    this.config = config;
  }

  /**
   * Validates the configuration before superclass construction.
   *
   * @param config the configuration to validate
   * @return {@code config}
   */
  private static RuntimeScopeConfig requireConfig(RuntimeScopeConfig config)
  {
    requireThat(config, "config").isNotNull();
    return config;
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return config.workDir();
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
    return config.timezone();
  }
}
