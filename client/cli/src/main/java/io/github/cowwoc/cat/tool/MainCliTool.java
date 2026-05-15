/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AgentRuntime;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Production implementation of a runtime-neutral CLI scope for shared CAT utilities.
 * <p>
 * Reads runtime-neutral variables ({@code CAT_SESSION_ID}, {@code CAT_PROJECT_DIR},
 * {@code CAT_PLUGIN_ROOT}, {@code CAT_PLUGIN_DATA}, {@code CAT_CONFIG_DIR},
 * {@code CAT_RUNTIME}) and fails fast if any are unset or blank.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public class MainCliTool extends AbstractCliTool
{
  /**
   * Creates a new runtime-neutral CLI scope.
   */
  public MainCliTool()
  {
    this(System::getenv, Path.of(System.getProperty("user.dir")));
  }

  /**
   * Creates a new runtime-neutral CLI scope for tests.
   *
   * @param environment resolves environment variable names to values
   * @param workDir the process working directory
   */
  public MainCliTool(Function<String, String> environment, Path workDir)
  {
    this(createConfig(environment, workDir));
  }

  /**
   * Creates a new CLI scope from resolved values.
   *
   * @param config the resolved CLI scope configuration
   */
  protected MainCliTool(CliToolConfig config)
  {
    super(config);
  }

  /**
   * Creates a resolved CLI configuration from environment variables.
   *
   * @param environment resolves environment variable names to values
   * @param workDir the process working directory
   * @return the resolved CLI configuration
   */
  private static CliToolConfig createConfig(Function<String, String> environment, Path workDir)
  {
    String sessionId = CliEnvironment.required(environment, "CAT_SESSION_ID");
    Path projectPath = Path.of(CliEnvironment.required(environment, "CAT_PROJECT_DIR"));
    Path pluginRoot = Path.of(CliEnvironment.required(environment, "CAT_PLUGIN_ROOT"));
    Path pluginData = Path.of(CliEnvironment.required(environment, "CAT_PLUGIN_DATA"));
    Path configPath = Path.of(CliEnvironment.required(environment, "CAT_CONFIG_DIR"));
    AgentRuntime runtime = AgentRuntime.fromId(CliEnvironment.required(environment, "CAT_RUNTIME"));
    requireThat(runtime, "runtime").isNotNull();
    return new CliToolConfig(sessionId, projectPath, pluginRoot, pluginData, configPath,
      runtime.pluginDescriptor(), runtime.ruleDirectories(projectPath, pluginRoot),
      runtime.pluginCacheDescriptor(), workDir, CliEnvironment.optional(environment, "TZ", "UTC"),
      CliEnvironment.optional(environment, "CAT_PLUGIN_JSON_URL", ""));
  }
}
