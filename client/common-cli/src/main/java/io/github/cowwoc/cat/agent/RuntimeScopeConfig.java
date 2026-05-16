/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolved configuration for a runtime-aware plugin scope.
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
public record RuntimeScopeConfig(Path projectPath, Path pluginRoot, Path pluginData,
                                 Path pluginDescriptor, List<Path> ruleDirectories,
                                 Path pluginCacheDescriptor, Path workDir, String timezone)
{
  /**
   * Creates a validated runtime scope configuration.
   */
  public RuntimeScopeConfig
  {
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(pluginData, "pluginData").isNotNull();
    requireThat(pluginDescriptor, "pluginDescriptor").isNotNull();
    requireThat(ruleDirectories, "ruleDirectories").isNotNull();
    requireThat(workDir, "workDir").isNotNull();
    requireThat(timezone, "timezone").isNotBlank();
    ruleDirectories = List.copyOf(ruleDirectories);
  }
}
