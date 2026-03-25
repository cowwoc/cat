/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production implementation of {@link JvmScope} for CLI tool processes.
 * <p>
 * Reads session environment values ({@code CLAUDE_SESSION_ID}, {@code CLAUDE_PROJECT_DIR},
 * {@code CLAUDE_PLUGIN_ROOT}) from {@code System.getenv()} at construction time and passes
 * them to {@link AbstractClaudeTool}.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainClaudeTool extends AbstractClaudeTool
{
  private final ConcurrentLazyReference<Path> claudeConfigDirRef =
    ConcurrentLazyReference.create(this::claudeConfigDir);
  private final ConcurrentLazyReference<TerminalType> terminalTypeRef =
    ConcurrentLazyReference.create(this::terminalType);
  private final ConcurrentLazyReference<String> tzRef =
    ConcurrentLazyReference.create(this::tz);
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a new production Claude tool scope.
   * <p>
   * Reads the three required environment variables from {@code System.getenv()} and fails
   * immediately with {@link AssertionError} if any are unset or blank.
   *
   * @throws AssertionError if any required environment variable is not set
   */
  public MainClaudeTool()
  {
    super(getEnvVar("CLAUDE_SESSION_ID"),
      Path.of(getEnvVar("CLAUDE_PROJECT_DIR")),
      Path.of(getEnvVar("CLAUDE_PLUGIN_ROOT")));
  }

  /**
   * Reads a required environment variable, failing fast if it is absent or blank.
   *
   * @param name the environment variable name
   * @return the non-blank value
   * @throws AssertionError if the variable is not set or is blank
   */
  private static String getEnvVar(String name)
  {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new AssertionError(name + " is not set");
    return value;
  }

  /**
   * Reads the Claude config directory from the environment or defaults to ~/.claude.
   *
   * @return the Claude config directory path
   */
  private Path claudeConfigDir()
  {
    String configDir = System.getenv("CLAUDE_CONFIG_DIR");
    if (configDir != null && !configDir.isBlank())
      return Path.of(configDir);
    return Path.of(System.getProperty("user.home"), ".claude");
  }

  /**
   * Detects the terminal type.
   *
   * @return the detected terminal type
   */
  private TerminalType terminalType()
  {
    return TerminalType.detect();
  }

  /**
   * Reads the timezone from the environment or defaults to UTC.
   *
   * @return the timezone string
   */
  private String tz()
  {
    String tzValue = System.getenv("TZ");
    if (tzValue == null || tzValue.isBlank())
      return "UTC";
    return tzValue;
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return Path.of(System.getProperty("user.dir"));
  }

  @Override
  public Path getClaudeConfigDir()
  {
    ensureOpen();
    return claudeConfigDirRef.getValue();
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
    return tzRef.getValue();
  }

  @Override
  public boolean isClosed()
  {
    return closed.get();
  }

  @Override
  public void close()
  {
    closed.set(true);
  }
}
