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
 * Block premature destruction of git reflog (recovery safety net).
 * <p>
 * ADDED: 2026-01-05 after agent ran "git reflog expire --expire=now --all && git gc --prune=now"
 * immediately after git filter-branch, permanently destroying recovery options.
 */
public final class BlockReflogDestruction implements BashHandler
{
  private static final Pattern ACKNOWLEDGMENT_PATTERN =
    Pattern.compile("# ACKNOWLEDGED:.*([Rr]eflog|gc|prune)");
  private static final Pattern REFLOG_EXPIRE_PATTERN =
    Pattern.compile("git\\s+reflog\\s+expire.*--expire=(now|all|0)");
  private static final Pattern GC_PRUNE_PATTERN =
    Pattern.compile("git\\s+gc\\s+.*--prune=(now|all)");

  /**
   * Creates a new handler for blocking reflog destruction.
   */
  public BlockReflogDestruction()
  {
    // Handler class
  }

  @Override
  public Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    // Check for acknowledgment bypass
    if (ACKNOWLEDGMENT_PATTERN.matcher(command).find())
      return Result.allow();

    // Check for reflog expire with --expire=now (dangerous)
    if (REFLOG_EXPIRE_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: Premature reflog destruction detected**

        This command PERMANENTLY DESTROYS the git reflog, which is your PRIMARY RECOVERY
        MECHANISM after history-rewriting operations like:
        - git filter-branch
        - git rebase
        - git reset --hard
        - git commit --amend

        **Why this is dangerous:**
        The reflog keeps references to ALL previous HEAD positions for ~90 days by default.
        If something went wrong with filter-branch or rebase, you can recover using:
          git reflog
          git reset --hard HEAD@{N}

        Once you run 'git reflog expire --expire=now', this recovery option is GONE FOREVER.

        **RECOMMENDED APPROACH:**
        1. Wait 24-48 hours after major operations
        2. Verify everything works correctly
        3. THEN (and only then) clean up if needed

        To bypass (if user explicitly requests): Add comment # ACKNOWLEDGED: reflog""");
    }

    // Check for git gc --prune=now (also dangerous)
    if (GC_PRUNE_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: Aggressive garbage collection detected**

        This command with --prune=now permanently removes unreachable objects.
        Combined with reflog expire, this destroys ALL recovery options.

        **RECOMMENDED:** Let git gc run naturally with default 2-week prune period.

        To bypass (if user explicitly requests): Add comment # ACKNOWLEDGED: gc prune""");
    }

    return Result.allow();
  }
}
