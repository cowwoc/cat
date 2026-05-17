/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool;

import io.github.cowwoc.cat.agent.AgentRuntime;
import io.github.cowwoc.cat.claude.hook.prompt.UserIssues;
import io.github.cowwoc.cat.tool.CliEnvironment;
import io.github.cowwoc.cat.tool.MainCliTool;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.nio.file.Path;

/**
 * Production implementation of {@link AgentScope} for CLI tool processes.
 * <p>
 * Derives session values from {@code System.getenv()}, JVM properties, and the process working
 * directory at construction time.
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
    super(AgentRuntime.CLAUDE, System::getenv, System::getProperty,
      Path.of(System.getProperty("user.dir")));
    this.anthropicBaseUrl = CliEnvironment.optional(System::getenv, "ANTHROPIC_BASE_URL", "");
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
