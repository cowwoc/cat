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
 * Production implementation of {@link JvmScope} for infrastructure CLI tool processes that run
 * outside a Claude session.
 * <p>
 * Reads only infrastructure path variables ({@code CLAUDE_PROJECT_DIR}, {@code CLAUDE_PLUGIN_ROOT},
 * {@code TZ}) from {@code System.getenv()} at construction time.
 * {@code CLAUDE_PROJECT_DIR} and {@code CLAUDE_PLUGIN_ROOT} are required and fail fast if absent.
 * {@code TZ} defaults to {@code "UTC"}.
 * <p>
 * This scope is appropriate for CLI tools like {@code GetSkill} that are invoked by the skill
 * preprocessor before a Claude session is established and therefore do not have access to
 * session-specific variables ({@code CLAUDE_SESSION_ID}).
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainJvmScope extends AbstractClaudeScope
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
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a new infrastructure JVM scope.
   * <p>
   * Reads {@code CLAUDE_PROJECT_DIR} and {@code CLAUDE_PLUGIN_ROOT} from {@code System.getenv()}
   * and fails immediately with {@link AssertionError} if either is unset or blank. {@code TZ} is
   * optional and loaded lazily with a default of {@code "UTC"}.
   *
   * @throws AssertionError if {@code CLAUDE_PROJECT_DIR} or {@code CLAUDE_PLUGIN_ROOT} is not set
   */
  public MainJvmScope()
  {
    super(Path.of(readEnvVar("CLAUDE_PROJECT_DIR")),
      Path.of(readEnvVar("CLAUDE_PLUGIN_ROOT")),
      readClaudeConfigPath());
  }

  /**
   * Reads a required environment variable, failing fast if it is absent or blank.
   *
   * @param name the environment variable name
   * @return the non-blank value
   * @throws AssertionError if the variable is not set or is blank
   */
  private static String readEnvVar(String name)
  {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new AssertionError(name + " is not set");
    return value;
  }

  /**
   * Reads the Claude config directory from the environment or defaults to {@code ~/.claude}.
   *
   * @return the Claude config directory path
   */
  private static Path readClaudeConfigPath()
  {
    String configDir = System.getenv("CLAUDE_CONFIG_DIR");
    if (configDir == null || configDir.isBlank())
      return Path.of(System.getProperty("user.home"), ".claude");
    return Path.of(configDir);
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
