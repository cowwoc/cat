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
 * Block merge commits to enforce linear git history.
 * <p>
 * See: Learning M047 - use ff-merge to maintain linear history
 */
public final class BlockMergeCommits implements BashHandler
{
  private static final Pattern MERGE_PATTERN =
    Pattern.compile("(^|;|&&|\\|)\\s*git\\s+merge(?!-)");
  private static final Pattern NO_FF_PATTERN =
    Pattern.compile("git\\s+merge(?!-)\\s+.*--no-ff|git\\s+merge(?!-)\\s+--no-ff");
  private static final Pattern FF_ONLY_OR_SQUASH_PATTERN =
    Pattern.compile("(?:^|\\s)(--ff-only|--squash)(?:\\s|$)");

  /**
   * Creates a new handler for blocking merge commits.
   */
  public BlockMergeCommits()
  {
    // Handler class
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    // Skip if not a git merge command
    if (!MERGE_PATTERN.matcher(command).find())
    {
      return Result.allow();
    }

    // BLOCK: git merge --no-ff (explicitly creates merge commit)
    if (NO_FF_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: git merge --no-ff creates merge commits**

        Linear history is required. Use one of:
        - `git merge --ff-only <branch>` - Fast-forward only, fails if not possible
        - `git rebase <branch>` - Rebase for linear history

        Or use the `/cat:git-merge-linear` skill which handles this correctly.

        **See**: Learning M047 - merge commits break linear history""");
    }

    // BLOCK: git merge without --ff-only or --squash
    if (!FF_ONLY_OR_SQUASH_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: git merge without --ff-only may create merge commits**

        Linear history is required. Use one of:
        - `git merge --ff-only <branch>` - Fast-forward only, fails if not possible
        - `git merge --squash <branch>` - Squash commits into one
        - `git rebase <branch>` - Rebase for linear history

        Or use the `/cat:git-merge-linear` skill which handles this correctly.

        **See**: Learning M047 - merge commits break linear history""");
    }

    return Result.allow();
  }
}
