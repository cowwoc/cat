/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
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
     * Converts this result to JSON format matching the bash skill output.
     *
     * @param mapper the JSON mapper to use for serialization
     * @return JSON string representation
     * @throws NullPointerException if mapper is null
     * @throws IOException if JSON serialization fails
     */
    public String toJson(JsonMapper mapper) throws IOException
    {
      requireThat(mapper, "mapper").isNotNull();
      return mapper.writeValueAsString(Map.of(
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
   * Main method for command-line execution.
   * <p>
   * Arguments:
   * <ul>
   *   <li>{@code --worktree PATH} — path to the worktree</li>
   *   <li>{@code --base-branch BRANCH} — base branch to compare against</li>
   * </ul>
   * <p>
   * Output is JSON to stdout.
   *
   * @param args command-line arguments
   * @throws IOException if git operations fail
   */
  public static void main(String[] args) throws IOException
  {
    try (MainJvmScope scope = new MainJvmScope())
    {
      boolean success = run(args, scope, System.out, System.err);
      if (!success)
        System.exit(1);
    }
  }

  /**
   * Executes the check command with an injectable scope for testability.
   * <p>
   * Unlike {@link #main(String[])}, this method does not call {@link System#exit(int)}.
   * Error output is written to {@code err} and {@code false} is returned.
   *
   * @param args the command-line arguments
   * @param scope the JVM scope to use for JSON serialization
   * @param out the output stream for successful results
   * @param err the error stream for error messages
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if git operations fail
   */
  public static boolean run(String[] args, JvmScope scope, PrintStream out, PrintStream err) throws IOException
  {
    String worktreePath = "";
    String baseBranch = "";
    JsonMapper mapper = scope.getJsonMapper();

    for (int i = 0; i < args.length - 1; ++i)
    {
      switch (args[i])
      {
        case "--worktree" ->
        {
          ++i;
          worktreePath = args[i];
        }
        case "--base-branch" ->
        {
          ++i;
          baseBranch = args[i];
        }
        default ->
        {
          err.println(mapper.writeValueAsString(Map.of(
            "error", "Unknown argument: " + args[i])));
          return false;
        }
      }
    }

    if (worktreePath.isEmpty())
    {
      err.println(mapper.writeValueAsString(Map.of("error", "--worktree is required")));
      return false;
    }

    if (baseBranch.isEmpty())
    {
      err.println(mapper.writeValueAsString(Map.of("error", "--base-branch is required")));
      return false;
    }

    try
    {
      CheckResult result = check(worktreePath, baseBranch);
      out.println(result.toJson(mapper));
      return true;
    }
    catch (IllegalArgumentException e)
    {
      err.println(mapper.writeValueAsString(Map.of("error", e.getMessage())));
      return false;
    }
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

    String countOutput = GitCommands.runGit(Path.of(worktreePath),
      "rev-list", "--count", baseBranch + "..HEAD");
    int commitCount;
    try
    {
      commitCount = Integer.parseInt(countOutput.strip());
    }
    catch (NumberFormatException _)
    {
      commitCount = 0;
    }

    if (commitCount > 0)
    {
      String logOutput = GitCommands.runGit(Path.of(worktreePath),
        "log", "--oneline", baseBranch + "..HEAD", "-5");

      String[] lines = logOutput.split("\n");
      int lineCount = Math.min(lines.length, 5);
      StringJoiner summary = new StringJoiner("|");

      for (int i = 0; i < lineCount; ++i)
        summary.add(lines[i].strip());

      return new CheckResult(true, commitCount, summary.toString());
    }
    return new CheckResult(false, 0, "");
  }
}
