/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.nio.file.Path;
import java.util.Map;

/**
 * Reads Claude environment variables for non-hook CLI commands.
 * <p>
 * CLI commands (invoked directly by the user or by skills) have access to environment variables
 * like {@code CLAUDE_SESSION_ID}, {@code CLAUDE_PROJECT_DIR}, {@code CLAUDE_PLUGIN_ROOT}, and
 * {@code CLAUDE_ENV_FILE}. Hook processes do NOT have these variables — hooks must read
 * session-specific values from their HookInput JSON (passed via stdin).
 * <p>
 * Use this class in CLI command entry points (e.g., {@code main()} methods for skill launchers)
 * where environment variables are guaranteed to be set.
 */
public final class ClaudeEnv
{
  static
  {
    SharedSecrets.setClaudeEnvAccess(ClaudeEnv::new);
  }

  private final Map<String, String> env;

  /**
   * Creates a new ClaudeEnv reading from the process environment.
   */
  public ClaudeEnv()
  {
    this.env = System.getenv();
  }

  /**
   * Creates a new ClaudeEnv reading from the supplied environment map.
   * <p>
   * This constructor is intended for testing, where injecting a controlled environment map
   * avoids dependencies on the host environment.
   *
   * @param env the environment variable map to use
   * @throws NullPointerException if {@code env} is null
   */
  private ClaudeEnv(Map<String, String> env)
  {
    if (env == null)
      throw new NullPointerException("env");
    this.env = env;
  }

  /**
   * Returns the Claude session ID.
   *
   * @return the session ID
   * @throws AssertionError if {@code CLAUDE_SESSION_ID} is not set in the environment
   */
  public String getClaudeSessionId()
  {
    String value = env.get("CLAUDE_SESSION_ID");
    if (value == null || value.isEmpty())
      throw new AssertionError("CLAUDE_SESSION_ID is not set");
    return value;
  }

  /**
   * Returns the Claude project directory.
   *
   * @return the project directory path
   * @throws AssertionError if {@code CLAUDE_PROJECT_DIR} is not set in the environment
   */
  public Path getClaudeProjectDir()
  {
    String value = env.get("CLAUDE_PROJECT_DIR");
    if (value == null || value.isEmpty())
      throw new AssertionError("CLAUDE_PROJECT_DIR is not set");
    return Path.of(value);
  }

  /**
   * Returns the Claude plugin root directory.
   *
   * @return the plugin root directory path
   * @throws AssertionError if {@code CLAUDE_PLUGIN_ROOT} is not set in the environment
   */
  public Path getClaudePluginRoot()
  {
    String value = env.get("CLAUDE_PLUGIN_ROOT");
    if (value == null || value.isEmpty())
      throw new AssertionError("CLAUDE_PLUGIN_ROOT is not set");
    return Path.of(value);
  }

  /**
   * Returns the path to the Claude environment file.
   *
   * @return the environment file path
   * @throws AssertionError if {@code CLAUDE_ENV_FILE} is not set in the environment
   */
  public Path getClaudeEnvFile()
  {
    String value = env.get("CLAUDE_ENV_FILE");
    if (value == null || value.isEmpty())
      throw new AssertionError("CLAUDE_ENV_FILE is not set");
    return Path.of(value);
  }
}
