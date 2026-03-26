/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main implementation of {@link ClaudeStatusline} for production use.
 * <p>
 * Reads session environment values ({@code CLAUDE_PROJECT_DIR}, {@code CLAUDE_PLUGIN_ROOT}) from
 * {@code System.getenv()} at construction time, then reads and parses the statusline JSON from the
 * provided input stream.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainClaudeStatusline extends AbstractClaudeStatusline
{
  private final Path projectPath;
  private final Path pluginRoot;
  private final ConcurrentLazyReference<Path> claudeConfigPathRef =
    ConcurrentLazyReference.create(this::claudeConfigPath);
  private final ConcurrentLazyReference<TerminalType> terminalTypeRef =
    ConcurrentLazyReference.create(this::terminalType);
  private final ConcurrentLazyReference<String> tzRef =
    ConcurrentLazyReference.create(this::tz);
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a new production Claude statusline scope, reading statusline JSON from stdin.
   * <p>
   * Reads all bytes from {@code stdin} and parses them as statusline JSON via the superclass
   * constructor, then reads the required environment variables from {@code System.getenv()},
   * failing immediately with {@link AssertionError} if any are unset or blank.
   *
   * @param stdin the input stream providing Claude Code hook JSON (typically {@code System.in})
   * @throws AssertionError if any required environment variable is not set
   * @throws NullPointerException if {@code stdin} is null
   * @throws IOException if an I/O error occurs while reading the stream
   */
  public MainClaudeStatusline(InputStream stdin) throws IOException
  {
    super(stdin);
    this.projectPath = Path.of(getEnvVar("CLAUDE_PROJECT_DIR"));
    this.pluginRoot = Path.of(getEnvVar("CLAUDE_PLUGIN_ROOT"));
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
  private Path claudeConfigPath()
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
  public Path getProjectPath()
  {
    ensureOpen();
    return projectPath;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return pluginRoot;
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return Path.of(System.getProperty("user.dir"));
  }

  @Override
  protected Path getClaudeConfigPath()
  {
    ensureOpen();
    return claudeConfigPathRef.getValue();
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
