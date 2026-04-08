/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.ClaudeHook;

import java.util.regex.Pattern;

/**
 * Remind about git squash skill when interactive rebase is detected.
 * Block manual squash operations (git reset --soft) that bypass validation.
 */
public final class RemindGitSquash implements BashHandler
{
  private static final Pattern INTERACTIVE_REBASE_PATTERN =
    Pattern.compile("git\\s+rebase\\s+.*-i");
  private static final Pattern GIT_RESET_SOFT_PATTERN =
    Pattern.compile("reset\\s+--soft");

  /**
   * Creates a new handler for reminding about git squash skill.
   */
  public RemindGitSquash()
  {
    // Handler class
  }

  @Override
  public Result check(ClaudeHook scope)
  {
    String command = scope.getCommand();

    // Block git reset --soft (manual squash bypass)
    if (GIT_RESET_SOFT_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: Manual git reset --soft is prohibited.**

        Use /cat:git-squash-agent instead. Manual reset --soft:
        - Captures working directory state (can include stale files)
        - Bypasses commit message validation
        - Bypasses commit-tree safety mechanism

        The git-squash-agent skill:
        - Uses commit-tree to avoid working directory state capture
        - Validates commit message format
        - Creates automatic backups before squashing

        To squash commits, use: /cat:git-squash-agent""");
    }

    // Check for git rebase -i (interactive)
    if (INTERACTIVE_REBASE_PATTERN.matcher(command).find())
    {
      return Result.warn("""
        SUGGESTION: Use /cat:git-squash-agent instead of git rebase -i

        The /cat:git-squash-agent skill provides:
        - Automatic backup before squashing
        - Conflict recovery guidance
        - Proper commit message formatting

        To squash commits: /cat:git-squash-agent""");
    }

    return Result.allow();
  }
}
