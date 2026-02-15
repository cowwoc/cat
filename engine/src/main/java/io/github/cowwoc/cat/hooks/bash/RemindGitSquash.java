/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Remind about git squash skill when interactive rebase is detected.
 */
public final class RemindGitSquash implements BashHandler
{
  private static final Pattern INTERACTIVE_REBASE_PATTERN =
    Pattern.compile("git\\s+rebase\\s+.*-i");

  /**
   * Creates a new handler for reminding about git squash skill.
   */
  public RemindGitSquash()
  {
    // Handler class
  }

  @Override
  public Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    // Check for git rebase -i (interactive)
    if (INTERACTIVE_REBASE_PATTERN.matcher(command).find())
    {
      return Result.warn("""
        SUGGESTION: Use /cat:git-squash instead of git rebase -i

        The /cat:git-squash skill provides:
        - Automatic backup before squashing
        - Conflict recovery guidance
        - Proper commit message formatting

        To squash commits: /cat:git-squash""");
    }

    return Result.allow();
  }
}
