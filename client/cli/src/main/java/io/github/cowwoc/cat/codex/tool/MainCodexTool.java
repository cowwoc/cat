/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.tool;

import io.github.cowwoc.cat.agent.AbstractAgentPluginScope;
import io.github.cowwoc.cat.agent.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.nio.file.Path;
import java.util.List;

/**
 * Production implementation of a Codex CLI scope.
 * <p>
 * Reads Codex infrastructure environment values from {@code System.getenv()} at construction time.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainCodexTool extends AbstractAgentPluginScope
{
  private final ConcurrentLazyReference<TerminalType> terminalTypeRef =
    ConcurrentLazyReference.create(TerminalType::detect);
  private final Path workDir;
  private final Path codexHome;
  private final String timezone;

  /**
   * Creates a new production Codex tool scope.
   */
  public MainCodexTool()
  {
    this(Path.of(System.getProperty("user.dir")).toAbsolutePath(), getCodexHomeFromEnvironment(),
      getTimezoneFromEnvironment());
  }

  /**
   * Creates a new production Codex tool scope.
   *
   * @param projectPath the project path
   * @param codexHome the Codex home directory
   * @param timezone the timezone
   */
  private MainCodexTool(Path projectPath, Path codexHome, String timezone)
  {
    super(projectPath, projectPath, projectPath, Path.of(".codex-plugin").resolve("plugin.json"),
      List.of(), Path.of(".codex-plugin").resolve("plugin.json"));
    this.workDir = projectPath;
    this.codexHome = codexHome;
    this.timezone = timezone;
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

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return workDir;
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
    return timezone;
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
}
