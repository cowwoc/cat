package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block git rebase on main branch and checkout changes in main worktree.
 * <p>
 * M205: Block ANY checkout in main worktree.
 */
public final class BlockMainRebase implements BashHandler
{
  private static final Pattern CHECKOUT_PATTERN =
    Pattern.compile("(^|[;&|])\\s*git\\s+(checkout|switch)\\s+", Pattern.CASE_INSENSITIVE);
  private static final Pattern CD_WORKSPACE_PATTERN =
    Pattern.compile("cd\\s+(/workspace|['\"]*/workspace['\"]*)([\\s]|&&|;|$)");
  private static final Pattern CHECKOUT_TARGET_PATTERN =
    Pattern.compile("git\\s+(?:checkout|switch)\\s+([^\\s;&|]+)");
  private static final Pattern REBASE_PATTERN =
    Pattern.compile("(^|[;&|])\\s*git\\s+rebase", Pattern.CASE_INSENSITIVE);
  private static final Pattern CD_TARGET_PATTERN =
    Pattern.compile("^cd\\s+['\"]?([^'\";&|]+)['\"]?");

  /**
   * Creates a new handler for blocking main branch rebase.
   */
  public BlockMainRebase()
  {
    // Handler class
  }

  @Override
  public Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    String commandLower = GitCommands.toLowerCase(command);

    // Check for git checkout/switch in main worktree
    if (CHECKOUT_PATTERN.matcher(commandLower).find())
    {
      Result checkoutResult = checkCheckoutInMainWorktree(command);
      if (checkoutResult != null)
        return checkoutResult;
    }

    // Check for git rebase command
    if (!REBASE_PATTERN.matcher(commandLower).find())
      return Result.allow();

    // Check if rebasing on main
    String currentBranch = getCurrentBranch(command);
    if (currentBranch == null)
    {
      return Result.warn(
        "⚠️ Branch detection failed while checking rebase safety.\n" +
        "Cannot determine if rebasing on a protected branch.\n" +
        "Proceeding without rebase branch check.");
    }
    if (currentBranch.equals("main"))
    {
      return Result.block("""
        REBASE ON MAIN BLOCKED

        Attempted: git rebase on main branch
        Correct:   Main branch should never be rebased

        WHY THIS IS BLOCKED:
        - Rebasing main rewrites commit history
        - Merged commits get recreated as direct commits
        - This breaks the audit trail

        TO REBASE AN ISSUE BRANCH ONTO MAIN:
        Run from your issue's worktree, not main:

          cd /workspace/.claude/cat/worktrees/<issue-branch>
          git rebase main""");
    }

    return Result.allow();
  }

  /**
   * Checks if a checkout command is targeting the main worktree.
   *
   * @param command the bash command
   * @return a block result if checkout in main worktree detected, null otherwise
   */
  private Result checkCheckoutInMainWorktree(String command)
  {
    // Check if command cd's to /workspace
    if (CD_WORKSPACE_PATTERN.matcher(command).find())
    {
      String target = extractCheckoutTarget(command);
      if (!isCheckoutFlag(target))
      {
        return Result.block(String.format("""
          GIT CHECKOUT IN MAIN WORKTREE BLOCKED (M205)

          Attempted: git checkout %s in main worktree
          Correct:   Use task worktrees - never change main worktree's branch

          WHY THIS IS BLOCKED:
          - The main worktree (/workspace) should keep its current branch
          - Issue worktrees exist precisely to avoid touching main workspace state
          - Changing main worktree's branch disrupts operations

          WHAT TO DO INSTEAD:
          - For issue work: Use the issue worktree at /workspace/.claude/cat/worktrees/<branch>
          - For cleanup: Delete the worktree directory, don't checkout in main""", target));
      }
    }

    // Check if currently in /workspace (main worktree)
    String cwd = System.getProperty("user.dir");
    if ("/workspace".equals(cwd))
    {
      boolean mainWorktree;
      try
      {
        mainWorktree = GitCommands.isMainWorktree();
      }
      catch (IOException _)
      {
        return Result.warn(
          "Failed to determine if this is the main worktree while checking checkout safety.\n" +
          "Proceeding without main worktree check.");
      }
      if (mainWorktree)
      {
        String target = extractCheckoutTarget(command);
        if (!isCheckoutFlag(target))
        {
          return Result.block(String.format(
            "Blocked (M205): Cannot checkout '%s' in main worktree. Use issue worktrees instead.",
            target));
        }
      }
    }

    return null;
  }

  /**
   * Checks if the target is a checkout flag rather than a branch name.
   *
   * @param target the checkout target
   * @return true if target is a flag like -- or -b
   */
  private boolean isCheckoutFlag(String target)
  {
    return "--".equals(target) || "-b".equals(target) || "-B".equals(target);
  }

  /**
   * Extracts the checkout target from a git checkout/switch command.
   *
   * @param command the bash command
   * @return the checkout target, or "unknown" if not found
   */
  private String extractCheckoutTarget(String command)
  {
    Matcher matcher = CHECKOUT_TARGET_PATTERN.matcher(command);
    if (matcher.find())
      return matcher.group(1);
    return "unknown";
  }

  /**
   * Determines the current branch for the command's target directory.
   *
   * @param command the bash command (may contain cd to another directory)
   * @return the branch name, or {@code null} if branch detection failed
   */
  private String getCurrentBranch(String command)
  {
    // Check if command cd's to /workspace
    if (CD_WORKSPACE_PATTERN.matcher(command).find())
      return "main";

    // Check if command cd's elsewhere
    Matcher cdMatcher = CD_TARGET_PATTERN.matcher(command);
    if (cdMatcher.find())
    {
      String targetDir = cdMatcher.group(1).trim();
      try
      {
        return GitCommands.getCurrentBranch(targetDir);
      }
      catch (IllegalArgumentException | IOException _)
      {
        return null;
      }
    }

    // Fallback to current directory
    try
    {
      return GitCommands.getCurrentBranch();
    }
    catch (IOException _)
    {
      return null;
    }
  }
}
