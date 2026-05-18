/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import io.github.cowwoc.cat.agent.AgentEngine;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Production implementation of a engine-neutral CLI scope for shared CAT utilities.
 * <p>
 * Reads explicit {@code CAT_*} overrides when present, otherwise derives scope values from engine
 * harness variables and launcher context. Skills may keep {@code CAT_*} variables for LLM-visible
 * workflow decisions without exporting those aliases through to Java CLI processes.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public class MainCliTool extends AbstractCliTool
{
  /**
   * Creates a new engine-neutral CLI scope.
   */
  public MainCliTool()
  {
    this(System::getenv, System::getProperty, Path.of(System.getProperty("user.dir")));
  }

  /**
   * Creates a new engine-neutral CLI scope for tests.
   *
   * @param environment resolves environment variable names to values
   * @param workDir the process working directory
   */
  public MainCliTool(Function<String, String> environment, Path workDir)
  {
    this(environment, System::getProperty, workDir);
  }

  /**
   * Creates a new engine-neutral CLI scope for tests.
   *
   * @param environment resolves environment variable names to values
   * @param systemProperty resolves system property names to values
   * @param workDir the process working directory
   */
  public MainCliTool(Function<String, String> environment, Function<String, String> systemProperty,
    Path workDir)
  {
    super(environment, systemProperty, workDir);
  }

  /**
   * Creates a new CLI scope by deriving values for a specific engine.
   *
   * @param engine the engine to derive values for
   * @param environment resolves environment variable names to values
   * @param systemProperty resolves system property names to values
   * @param workDir the process working directory
   */
  protected MainCliTool(AgentEngine engine, Function<String, String> environment,
    Function<String, String> systemProperty, Path workDir)
  {
    super(engine, environment, systemProperty, workDir);
  }

  /**
   * Creates a new CLI scope from resolved values.
   *
   * @param sessionId the session ID
   * @param projectPath the project directory
   * @param pluginRoot the plugin root directory
   * @param pluginData the plugin data directory
   * @param configPath the active engine config directory
   * @param pluginDescriptor the plugin descriptor path relative to the plugin root
   * @param ruleDirectories the ordered rule directories
   * @param pluginCacheDescriptor the plugin cache descriptor path relative to the plugin root, or {@code null}
   * @param workDir the process working directory
   * @param timezone the timezone
   * @param pluginJsonUrl the plugin.json URL
   */
  protected MainCliTool(String sessionId, Path projectPath, Path pluginRoot, Path pluginData,
    Path configPath, Path pluginDescriptor, List<Path> ruleDirectories, Path pluginCacheDescriptor,
    Path workDir, String timezone, String pluginJsonUrl)
  {
    super(sessionId, projectPath, pluginRoot, pluginData, configPath, pluginDescriptor,
      ruleDirectories, pluginCacheDescriptor, workDir, timezone, pluginJsonUrl);
  }
}
