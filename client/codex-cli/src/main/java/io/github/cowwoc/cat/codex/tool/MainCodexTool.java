/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.tool;

import io.github.cowwoc.cat.agent.AbstractRuntimeScope;
import io.github.cowwoc.cat.agent.AgentRuntime;
import io.github.cowwoc.cat.agent.RuntimeScopeConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Production implementation of a Codex CLI scope.
 * <p>
 * Reads Codex infrastructure environment values from {@code System.getenv()} at construction time.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainCodexTool extends AbstractRuntimeScope
{
  private final Path codexHome;
  private final Map<String, String> commandEnvironment;

  /**
   * Creates a new production Codex tool scope.
   */
  public MainCodexTool()
  {
    this(Path.of(System.getProperty("user.dir")).toAbsolutePath(), getCodexHomeFromEnvironment(),
      getTimezoneFromEnvironment(), getCommandEnvironmentFromEnvironment());
  }

  /**
   * Creates a new production Codex tool scope.
   *
   * @param projectPath the project path
   * @param codexHome the Codex home directory
   * @param timezone           the timezone
   * @param commandEnvironment environment values used for command policy decisions
   */
  private MainCodexTool(Path projectPath, Path codexHome, String timezone,
    Map<String, String> commandEnvironment)
  {
    super(new RuntimeScopeConfig(projectPath, projectPath, projectPath,
      AgentRuntime.CODEX.pluginDescriptor(), List.of(), AgentRuntime.CODEX.pluginCacheDescriptor(),
      projectPath, timezone));
    this.codexHome = codexHome;
    this.commandEnvironment = Map.copyOf(commandEnvironment);
  }

  /**
   * Returns the Codex home directory.
   *
   * @return the Codex home directory
   */
  public Path getCodexHome()
  {
    ensureOpen();
    return codexHome;
  }

  /**
   * Returns environment values used for command policy decisions.
   *
   * @return environment values used for command policy decisions
   */
  public Map<String, String> getCommandEnvironment()
  {
    ensureOpen();
    return commandEnvironment;
  }

  /**
   * Reads the Codex home directory from the environment or defaults to {@code ~/.codex}.
   *
   * @return the Codex home directory
   */
  private static Path getCodexHomeFromEnvironment()
  {
    String codexHome = System.getenv("CODEX_HOME");
    if (codexHome != null && !codexHome.isBlank())
      return Path.of(codexHome);
    return Path.of(System.getProperty("user.home"), ".codex");
  }

  /**
   * Reads the timezone from the environment.
   *
   * @return the timezone
   */
  private static String getTimezoneFromEnvironment()
  {
    String timezone = System.getenv("TZ");
    if (timezone == null || timezone.isBlank())
      return "UTC";
    return timezone;
  }

  /**
   * Reads the environment values that affect Codex command construction.
   *
   * @return environment values used for command policy decisions
   */
  private static Map<String, String> getCommandEnvironmentFromEnvironment()
  {
    return Map.of("CODEX_TOOL", System.getenv().getOrDefault("CODEX_TOOL", ""),
      "CODEX_CI", System.getenv().getOrDefault("CODEX_CI", ""));
  }
}
