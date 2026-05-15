/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolved configuration for a CLI tool scope.
 *
 * @param sessionId the session ID
 * @param projectPath the project directory
 * @param pluginRoot the plugin root directory
 * @param pluginData the plugin data directory
 * @param configPath the active runtime config directory
 * @param pluginDescriptor the plugin descriptor path relative to the plugin root
 * @param ruleDirectories the ordered rule directories
 * @param pluginCacheDescriptor the plugin cache descriptor path relative to the plugin root, or {@code null}
 * @param workDir the process working directory
 * @param timezone the timezone
 * @param pluginJsonUrl the plugin.json URL
 */
public record CliToolConfig(String sessionId, Path projectPath, Path pluginRoot, Path pluginData,
                            Path configPath, Path pluginDescriptor, List<Path> ruleDirectories,
                            Path pluginCacheDescriptor, Path workDir, String timezone,
                            String pluginJsonUrl)
{
  /**
   * Creates a validated CLI tool configuration.
   */
  public CliToolConfig
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(pluginData, "pluginData").isNotNull();
    requireThat(configPath, "configPath").isNotNull();
    requireThat(pluginDescriptor, "pluginDescriptor").isNotNull();
    requireThat(ruleDirectories, "ruleDirectories").isNotNull();
    requireThat(workDir, "workDir").isNotNull();
    requireThat(timezone, "timezone").isNotBlank();
    requireThat(pluginJsonUrl, "pluginJsonUrl").isNotNull();
    ruleDirectories = List.copyOf(ruleDirectories);
  }
}
