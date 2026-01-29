package io.github.cowwoc.cat.hooks.bash.post;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validate rebase target - warn when using origin/X instead of local X.
 *
 * <p>Trigger: PostToolUse for Bash (git rebase commands)</p>
 */
public final class ValidateRebaseTarget implements BashHandler
{
  private static final Pattern REBASE_ORIGIN_PATTERN =
    Pattern.compile("(^|[;&|])\\s*git\\s+rebase\\s+(-[a-z]+\\s+|--[a-z-]+\\s+)*origin/",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ORIGIN_BRANCH_PATTERN =
    Pattern.compile("origin/([a-zA-Z0-9_-]+)");

  /**
   * Creates a new handler for validating rebase targets.
   */
  public ValidateRebaseTarget()
  {
    // Handler class
  }

  @Override
  @SuppressWarnings("UnusedVariable")
  public Result check(String command, JsonNode _toolInput, JsonNode _toolResult, String _sessionId)
  {
    // Check for git rebase with origin/ prefix
    if (!REBASE_ORIGIN_PATTERN.matcher(command).find())
      return Result.allow();

    // Extract branch name after origin/
    Matcher branchMatcher = ORIGIN_BRANCH_PATTERN.matcher(command);
    if (!branchMatcher.find())
      return Result.allow();

    String remoteBranch = branchMatcher.group(1);

    // Check if local branch exists
    if (!localBranchExists(remoteBranch))
      return Result.allow();

    // Get local and remote commits
    String localCommit = GitCommands.getCommitHash(remoteBranch);
    String remoteCommit = GitCommands.getCommitHash("origin/" + remoteBranch);

    if (!localCommit.isEmpty() && !remoteCommit.isEmpty() && !localCommit.equals(remoteCommit))
    {
      return Result.warn(String.format("""

        REBASE TARGET WARNING
        ===============================================================

        You used: git rebase origin/%s
        Local branch exists: %s

        LOCAL vs REMOTE DIFFER:
          Local  %s: %s
          Remote origin/%s: %s

        Per git-workflow.md - Branch Reference Resolution:
          PREFER: git rebase %s  (uses local branch)
          USED: git rebase origin/%s  (uses remote)

        WHEN TO USE WHICH:
          - Use local %s: Default when user says "rebase on %s"
          - Use origin/%s: Only if user explicitly requests remote

        If user said "rebase on %s", next time use:
          git rebase %s

        ===============================================================""",
        remoteBranch, remoteBranch,
        remoteBranch, truncateHash(localCommit),
        remoteBranch, truncateHash(remoteCommit),
        remoteBranch, remoteBranch,
        remoteBranch, remoteBranch, remoteBranch,
        remoteBranch, remoteBranch));
    }

    return Result.allow();
  }

  /**
   * Checks if a local branch exists.
   *
   * @param branchName the branch name to check
   * @return true if the branch exists locally
   */
  private boolean localBranchExists(String branchName)
  {
    // If we can get its commit hash, it exists
    String hash = GitCommands.getCommitHash(branchName);
    return !hash.isEmpty();
  }

  /**
   * Truncates a commit hash to 7 characters.
   *
   * @param hash the full hash
   * @return the truncated hash
   */
  private String truncateHash(String hash)
  {
    return hash.substring(0, Math.min(7, hash.length()));
  }
}
