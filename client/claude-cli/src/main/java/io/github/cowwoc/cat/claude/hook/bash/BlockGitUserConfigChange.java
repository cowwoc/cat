/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.hook.bash.GitUserConfigGuard;

/**
 * Block modifications to git commit identity (user.name / user.email) without explicit user request.
 * <p>
 * Agents must never silently overwrite the git commit identity. Reads (--get, bare read) are allowed;
 * writes, unsets, section removal, and inline {@code -c} overrides are blocked unless the user has
 * explicitly requested the change.
 */
public final class BlockGitUserConfigChange implements BashHandler
{
  private final ClaudeHook scope;

  /**
   * Creates a new handler for blocking git user identity changes.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public BlockGitUserConfigChange(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Checks whether the command changes git identity.
   *
   * @return the hook decision
   */
  @Override
  public Result check()
  {
    String command = scope.getCommand();
    String blockReason = GitUserConfigGuard.getBlockReason(command);
    if (!blockReason.isEmpty())
      return Result.block(blockReason);
    return Result.allow();
  }
}
