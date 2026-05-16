/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import io.github.cowwoc.cat.agent.AbstractAgentScope;
import io.github.cowwoc.cat.claude.hook.prompt.UserIssues;
import io.github.cowwoc.cat.tool.JvmScope;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import java.nio.file.Path;

/**
 * Claude-facing JVM scope base class.
 * <p>
 * Runtime-neutral path and mapper behavior lives in {@link AbstractAgentScope}. This class only adds
 * Claude-specific prompt handlers used by Claude-only scopes.
 */
public abstract class AbstractJvmScope extends AbstractAgentScope implements JvmScope
{
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<UserIssues> userIssues =
    ConcurrentLazyReference.create(() -> new UserIssues(this));

  /**
   * Creates a new abstract JVM scope with the given base path.
   *
   * @param projectPath the project's root directory
   * @throws NullPointerException if {@code projectPath} is null
   */
  protected AbstractJvmScope(Path projectPath)
  {
    super(projectPath);
  }

  /**
   * Encodes a project directory path using Claude Code's encoding algorithm.
   * <p>
   * Replaces {@code /}, {@code .}, and spaces with {@code -}. For example,
   * {@code /workspace} encodes to {@code -workspace}, {@code /home/user/my.project}
   * encodes to {@code -home-user-my-project}, and {@code /home/user/my project}
   * encodes to {@code -home-user-my-project}.
   *
   * @param projectPath the project directory path to encode
   * @return the encoded project path
   * @throws NullPointerException if {@code projectPath} is null
   */
  public static String encodeProjectPath(String projectPath)
  {
    return AbstractAgentScope.encodeProjectPath(projectPath);
  }

  public UserIssues getUserIssues()
  {
    ensureOpen();
    return userIssues.getValue();
  }
}
