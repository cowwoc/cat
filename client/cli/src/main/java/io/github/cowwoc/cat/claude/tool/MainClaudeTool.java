/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool;

import io.github.cowwoc.cat.agent.AgentRuntime;
import io.github.cowwoc.cat.claude.hook.prompt.UserIssues;
import io.github.cowwoc.cat.tool.CliToolConfig;
import io.github.cowwoc.cat.tool.CliEnvironment;
import io.github.cowwoc.cat.tool.MainCliTool;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Production implementation of {@link JvmScope} for CLI tool processes.
 * <p>
 * Reads session environment values ({@code CLAUDE_SESSION_ID}, {@code CLAUDE_PROJECT_DIR},
 * {@code CLAUDE_PLUGIN_ROOT}, {@code CLAUDE_PLUGIN_DATA}) from {@code System.getenv()} at
 * construction time and passes them to {@link AbstractClaudeTool}.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainClaudeTool extends MainCliTool implements ClaudeTool
{
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<UserIssues> userIssues =
    ConcurrentLazyReference.create(() -> new UserIssues(this));
  private final String anthropicBaseUrl;

  /**
   * Creates a new production Claude tool scope.
   * <p>
   * Reads the required environment variables from {@code System.getenv()} and fails
   * immediately with {@link AssertionError} if any required variable is unset or blank.
   *
   * @throws AssertionError if any required environment variable is not set
   */
  public MainClaudeTool()
  {
    super(createConfig(System::getenv));
    this.anthropicBaseUrl = CliEnvironment.optional(System::getenv, "ANTHROPIC_BASE_URL", "");
  }

  /**
   * Creates a resolved Claude CLI configuration from environment variables.
   *
   * @param environment resolves environment variable names to values
   * @return the resolved Claude CLI configuration
   */
  private static CliToolConfig createConfig(Function<String, String> environment)
  {
    Path projectPath = Path.of(CliEnvironment.required(environment, "CLAUDE_PROJECT_DIR"));
    Path pluginRoot = Path.of(CliEnvironment.required(environment, "CLAUDE_PLUGIN_ROOT"));
    return new CliToolConfig(CliEnvironment.required(environment, "CLAUDE_SESSION_ID"), projectPath,
      pluginRoot, Path.of(CliEnvironment.required(environment, "CLAUDE_PLUGIN_DATA")),
      getClaudeConfigPath(environment), AgentRuntime.CLAUDE.pluginDescriptor(),
      AgentRuntime.CLAUDE.ruleDirectories(projectPath, pluginRoot),
      AgentRuntime.CLAUDE.pluginCacheDescriptor(), Path.of(System.getProperty("user.dir")),
      CliEnvironment.optional(environment, "TZ", "UTC"),
      CliEnvironment.optional(environment, "CAT_PLUGIN_JSON_URL", ""));
  }

  /**
   * Resolves the Claude config path.
   *
   * @param environment resolves environment variable names to values
   * @return the Claude config path
   */
  private static Path getClaudeConfigPath(Function<String, String> environment)
  {
    String claudeConfigDir = environment.apply("CLAUDE_CONFIG_DIR");
    if (claudeConfigDir != null && !claudeConfigDir.isBlank())
      return Path.of(claudeConfigDir);
    return Path.of(System.getProperty("user.home"), ".claude");
  }

  @Override
  public String getAnthropicBaseUrl()
  {
    ensureOpen();
    return anthropicBaseUrl;
  }

  @Override
  public UserIssues getUserIssues()
  {
    ensureOpen();
    return userIssues.getValue();
  }
}
