package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block git rebase on main branch and checkout changes in main worktree.
 *
 * <p>M205: Block ANY checkout in main worktree.</p>
 */
public final class BlockMainRebase implements BashHandler
{
  /**
   * Creates a new handler for blocking main branch rebase.
   */
  public BlockMainRebase()
  {
    // Handler class
  }

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

  @Override
  @SuppressWarnings("UnusedVariable")
  public Result check(String command, JsonNode _toolInput, JsonNode _toolResult, String _sessionId)
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
    if ("main".equals(currentBranch))
    {
      return Result.block("""
        REBASE ON MAIN BLOCKED

        Attempted: git rebase on main branch
        Correct:   Main branch should never be rebased

        WHY THIS IS BLOCKED:
        - Rebasing main rewrites commit history
        - Merged commits get recreated as direct commits
        - This breaks the audit trail

        TO REBASE A TASK BRANCH ONTO MAIN:
        Run from your task's worktree, not main:

          cd /workspace/.worktrees/<task-branch>
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
          - Task worktrees exist precisely to avoid touching main workspace state
          - Changing main worktree's branch disrupts operations

          WHAT TO DO INSTEAD:
          - For task work: Use the task worktree at /workspace/.worktrees/<branch>
          - For cleanup: Delete the worktree directory, don't checkout in main""", target));
      }
    }

    // Check if currently in /workspace (main worktree)
    String cwd = System.getProperty("user.dir");
    if ("/workspace".equals(cwd) && GitCommands.isMainWorktree())
    {
      String target = extractCheckoutTarget(command);
      if (!isCheckoutFlag(target))
      {
        return Result.block(String.format(
          "Blocked (M205): Cannot checkout '%s' in main worktree. Use task worktrees instead.",
          target));
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
   * @return the branch name, or empty string if unavailable
   */
  private String getCurrentBranch(String command)
  {
    // Check if command cd's to /workspace
    if (CD_WORKSPACE_PATTERN.matcher(command).find())
    {
      return "main";
    }

    // Check if command cd's elsewhere
    Matcher cdMatcher = CD_TARGET_PATTERN.matcher(command);
    if (cdMatcher.find())
    {
      String targetDir = cdMatcher.group(1).trim();
      String branch = GitCommands.getCurrentBranch(targetDir);
      if (!branch.isEmpty())
      {
        return branch;
      }
    }

    // Fallback to current directory
    return GitCommands.getCurrentBranch();
  }
}
