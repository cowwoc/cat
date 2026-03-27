/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Production implementation of {@link ClaudeHook} for hook handler processes.
 * <p>
 * Reads infrastructure path variables ({@code CLAUDE_PROJECT_DIR}, {@code CLAUDE_PLUGIN_ROOT},
 * {@code CLAUDE_CONFIG_DIR}, {@code TZ}) from {@code System.getenv()} and reads hook JSON from
 * stdin at construction time.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainClaudeHook extends AbstractClaudeHook
{
  private final ConcurrentLazyReference<TerminalType> terminalType =
    ConcurrentLazyReference.create(TerminalType::detect);
  private final ConcurrentLazyReference<String> tz = ConcurrentLazyReference.create(() ->
  {
    String tzValue = System.getenv("TZ");
    if (tzValue == null || tzValue.isBlank())
      return "UTC";
    return tzValue;
  });

  /**
   * Creates a new production hook scope.
   * <p>
   * Reads infrastructure path variables from {@code System.getenv()} and parses hook JSON from
   * stdin. Fails immediately with {@link AssertionError} if any required path variable is absent.
   *
   * @throws AssertionError if {@code CLAUDE_PROJECT_DIR} or {@code CLAUDE_PLUGIN_ROOT} are not set
   * @throws IllegalStateException if stdin has no piped input, contains blank or malformed JSON, or
   *   is missing a session_id
   */
  public MainClaudeHook()
  {
    super(readStdin(), readEnvPath("CLAUDE_PROJECT_DIR"), readEnvPath("CLAUDE_PLUGIN_ROOT"),
      readConfigPath());
  }

  /**
   * Reads the hook JSON from stdin.
   *
   * @return the parsed JSON node
   * @throws IllegalStateException if stdin has no piped input or contains blank/malformed JSON
   */
  private static JsonNode readStdin()
  {
    JsonMapper mapper = createStdinMapper();
    try
    {
      if (System.console() != null && System.in.available() == 0)
        throw new IllegalStateException("No piped input available on stdin.");
    }
    catch (IOException e)
    {
      throw new IllegalStateException("Failed to check stdin availability.", e);
    }
    return readFrom(mapper, System.in);
  }

  /**
   * Reads a required environment variable as a Path, failing fast if absent or blank.
   *
   * @param name the environment variable name
   * @return the Path value
   * @throws AssertionError if the variable is not set or is blank
   */
  private static Path readEnvPath(String name)
  {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new AssertionError(name + " is not set");
    return Path.of(value);
  }

  /**
   * Reads the Claude config directory from {@code CLAUDE_CONFIG_DIR}, defaulting to
   * {@code ~/.claude} if unset.
   *
   * @return the config directory path
   */
  private static Path readConfigPath()
  {
    String configDir = System.getenv("CLAUDE_CONFIG_DIR");
    if (configDir != null && !configDir.isBlank())
      return Path.of(configDir);
    return Path.of(System.getProperty("user.home"), ".claude");
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return Path.of(System.getProperty("user.dir"));
  }

  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return terminalType.getValue();
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return tz.getValue();
  }
}
