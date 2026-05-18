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
import java.util.List;

/**
 * Shared base implementation for engine-specific plugin scopes.
 * <p>
 * Engine modules provide their plugin descriptor and rule directories, while this class owns
 * common process-scoped values such as work directory, terminal detection, and timezone.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractEngineScope extends AbstractAgentPluginScope
{
  private final ConcurrentLazyReference<TerminalType> terminalTypeRef =
    ConcurrentLazyReference.create(TerminalType::detect);
  private final Path workDir;
  private final String timezone;

  /**
   * Creates a new engine-aware plugin scope.
   *
   * @param projectPath the project directory
   * @param pluginRoot the plugin root directory
   * @param pluginData the plugin data directory
   * @param pluginDescriptor the plugin descriptor path relative to the plugin root
   * @param ruleDirectories the ordered rule directories
   * @param pluginCacheDescriptor the plugin cache descriptor path relative to the plugin root, or {@code null}
   * @param workDir the process working directory
   * @param timezone the timezone
   */
  protected AbstractEngineScope(Path projectPath, Path pluginRoot, Path pluginData,
    Path pluginDescriptor, List<Path> ruleDirectories, Path pluginCacheDescriptor, Path workDir,
    String timezone)
  {
    super(projectPath, pluginRoot, pluginData, pluginDescriptor, ruleDirectories, pluginCacheDescriptor);
    requireThat(workDir, "workDir").isNotNull();
    requireThat(timezone, "timezone").isNotBlank();
    this.workDir = workDir;
    this.timezone = timezone;
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
}
