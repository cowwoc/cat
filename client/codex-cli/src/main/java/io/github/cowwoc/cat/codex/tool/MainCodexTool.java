/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.tool;

import io.github.cowwoc.cat.agent.AgentEngine;
import io.github.cowwoc.cat.tool.MainCliTool;

import java.nio.file.Path;
import java.util.Map;

/**
 * Production implementation of a Codex CLI scope.
 * <p>
 * Derives engine-specific scope values from the active Codex harness and exposes
 * Codex-only environment values used by command policy decisions.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainCodexTool extends MainCliTool
{
  private final Path codexHome;
  private final Map<String, String> commandEnvironment;

  /**
   * Creates a new production Codex tool scope.
   */
  public MainCodexTool()
  {
    super(AgentEngine.CODEX, System::getenv, System::getProperty,
      Path.of(System.getProperty("user.dir")));
    this.codexHome = getCodexHomeFromEnvironment();
    this.commandEnvironment = getCommandEnvironmentFromEnvironment();
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
   * Reads the environment values that affect Codex command construction.
   *
   * @return environment values used for command policy decisions
   */
  private static Map<String, String> getCommandEnvironmentFromEnvironment()
  {
    return Map.of("CODEX_TOOL", System.getenv().getOrDefault("CODEX_TOOL", ""),
      "CODEX_CI", System.getenv().getOrDefault("CODEX_CI", ""),
      "CODEX_APPROVAL_POLICY", System.getenv().getOrDefault("CODEX_APPROVAL_POLICY", ""));
  }
}
