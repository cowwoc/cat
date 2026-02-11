package io.github.cowwoc.cat.hooks.util;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Static utility for checking if a task branch has existing commits.
 * <p>
 * This is a deterministic check that does not require LLM decision-making.
 * It should be called after worktree creation to detect if previous work exists on the branch.
 * <p>
 * Compares worktree HEAD against base branch using git rev-list to count commits ahead.
 * <p>
 * This class is static-only because it is a stateless utility - all state is provided via method parameters.
 * The nested {@link CheckResult} record is the only result type and is therefore appropriately nested.
 */
public final class ExistingWorkChecker
{
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  /**
   * Result of checking for existing work.
   *
   * @param hasExistingWork whether the worktree has commits ahead of the base branch
   * @param existingCommits the number of commits ahead
   * @param commitSummary the summary of commits (up to 5 commits, pipe-separated)
   */
  public record CheckResult(boolean hasExistingWork, int existingCommits, String commitSummary)
  {
    /**
     * Creates a new check result.
     *
     * @param hasExistingWork whether the worktree has commits ahead of the base branch
     * @param existingCommits the number of commits ahead
     * @param commitSummary the summary of commits
     * @throws NullPointerException if commitSummary is null
     */
    public CheckResult
    {
      requireThat(commitSummary, "commitSummary").isNotNull();
    }

    /**
     * Converts this result to JSON format matching the bash script output.
     *
     * @return JSON string representation
     * @throws IOException if JSON serialization fails
     */
    public String toJson() throws IOException
    {
      return MAPPER.writeValueAsString(Map.of(
        "has_existing_work", hasExistingWork,
        "existing_commits", existingCommits,
        "commit_summary", commitSummary));
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private ExistingWorkChecker()
  {
  }

  /**
   * Checks if a worktree has existing commits compared to the base branch.
   *
   * @param worktreePath the path to the worktree
   * @param baseBranch the base branch name
   * @return the check result
   * @throws IllegalArgumentException if worktreePath does not exist or is not a directory
   * @throws IOException if git operations fail
   */
  public static CheckResult check(String worktreePath, String baseBranch) throws IOException
  {
    requireThat(worktreePath, "worktreePath").isNotBlank();
    requireThat(baseBranch, "baseBranch").isNotBlank();

    Path worktree = Path.of(worktreePath);
    if (!Files.isDirectory(worktree))
      throw new IllegalArgumentException("Cannot access worktree: " + worktreePath);

    String countOutput = GitCommands.runGitCommandInDirectory(worktreePath,
      "rev-list", "--count", baseBranch + "..HEAD");
    int commitCount;
    try
    {
      commitCount = Integer.parseInt(countOutput.trim());
    }
    catch (NumberFormatException _)
    {
      commitCount = 0;
    }

    if (commitCount > 0)
    {
      String logOutput = GitCommands.runGitCommandInDirectory(worktreePath,
        "log", "--oneline", baseBranch + "..HEAD", "-5");

      String[] lines = logOutput.split("\n");
      int lineCount = Math.min(lines.length, 5);
      StringJoiner summary = new StringJoiner("|");

      for (int i = 0; i < lineCount; ++i)
        summary.add(lines[i].trim());

      return new CheckResult(true, commitCount, summary.toString());
    }
    return new CheckResult(false, 0, "");
  }
}
