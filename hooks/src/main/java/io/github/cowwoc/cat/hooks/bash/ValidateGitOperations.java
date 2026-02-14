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
 * Validate dangerous git operations.
 * <p>
 * Warns or blocks commands like git push --force, reset --hard, etc.
 */
public final class ValidateGitOperations implements BashHandler
{
  private static final Pattern FORCE_PUSH_MAIN_PATTERN =
    Pattern.compile("git\\s+push\\s+.*--force.*\\s+(main|master)(\\s|$)", Pattern.CASE_INSENSITIVE);
  private static final Pattern RESET_HARD_PATTERN =
    Pattern.compile("git\\s+reset\\s+--hard");

  /**
   * Creates a new handler for validating dangerous git operations.
   */
  public ValidateGitOperations()
  {
    // Handler class
  }

  @Override
  public Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    // Block: git push --force to main/master
    if (FORCE_PUSH_MAIN_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: Force push to main/master**

        Force pushing to main/master rewrites shared history and can cause:
        - Lost commits from other contributors
        - Broken references
        - Confused collaborators

        Use --force-with-lease instead, or ask the user if they really want this.""");
    }

    // Block: git reset --hard without explicit acknowledgment
    if (RESET_HARD_PATTERN.matcher(command).find())
    {
      // Allow if in a worktree or has acknowledgment
      if (command.contains("# ACKNOWLEDGED") || command.contains("worktrees"))
        return Result.allow();
      return Result.block("""
        **BLOCKED: git reset --hard can lose uncommitted work**

        This command discards all uncommitted changes permanently.

        If you're sure:
        - In a worktree: Use /cat:git-rebase skill
        - Main worktree: Add # ACKNOWLEDGED comment

        Consider: git stash to save work before reset.""");
    }

    return Result.allow();
  }
}
