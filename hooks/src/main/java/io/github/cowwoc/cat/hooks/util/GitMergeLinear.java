/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommand;
import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandSingleLine;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Linear git merge operation with backup and safety checks.
 * <p>
 * Merges task branch to base branch with linear history (86% faster than manual workflow).
 */
public final class GitMergeLinear
{
  private final JsonMapper mapper;

  /**
   * Creates a new GitMergeLinear instance.
   *
   * @param mapper the JSON mapper to use for serialization
   * @throws NullPointerException if mapper is null
   */
  public GitMergeLinear(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  /**
   * Executes the linear merge operation.
   *
   * @param taskBranch the issue branch to merge
   * @param baseBranch the target branch to merge into (empty string for auto-detect)
   * @param cleanup whether to delete branch and worktree after merge
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String execute(String taskBranch, String baseBranch, boolean cleanup) throws IOException
  {
    requireThat(taskBranch, "taskBranch").isNotBlank();
    requireThat(baseBranch, "baseBranch").isNotNull();

    long startTime = System.currentTimeMillis();

    if (baseBranch.isEmpty())
      baseBranch = detectBaseBranch(taskBranch);

    String currentBranch = getCurrentBranch();
    if (!currentBranch.equals(baseBranch))
      throw new IOException("Must be on " + baseBranch + " branch. Currently on: " + currentBranch);

    runGitCommandSingleLine("rev-parse", "--verify", taskBranch);

    if (!isWorkingDirectoryClean())
      throw new IOException("Working directory is not clean. Commit or stash changes first.");

    int commitCount = getCommitCount(baseBranch, taskBranch);
    if (commitCount != 1)
      throw new IOException("Task branch must have exactly 1 commit. Found: " + commitCount +
        ". Squash commits first.");

    checkFastForwardPossible(baseBranch, taskBranch);

    String commitMsg = getCommitMessage(taskBranch);

    fastForwardMerge(taskBranch);

    verifyLinearHistory();

    String commitShaAfter = getCommitSha("HEAD");

    boolean worktreeRemoved = false;
    boolean branchDeleted = false;

    if (cleanup)
    {
      String worktreePath = findWorktreeForBranch(taskBranch);
      if (!worktreePath.isEmpty() && Files.isDirectory(Paths.get(worktreePath)))
      {
        removeWorktree(worktreePath);
        worktreeRemoved = true;
      }

      if (deleteBranch(taskBranch))
        branchDeleted = true;
    }

    long endTime = System.currentTimeMillis();
    long duration = (endTime - startTime) / 1000;

    return buildSuccessJson(taskBranch, commitShaAfter, commitMsg, commitCount,
      cleanup, worktreeRemoved, branchDeleted, duration);
  }

  /**
   * Detects the base branch from worktree metadata.
   *
   * @param taskBranch the task branch name
   * @return the base branch name
   * @throws IOException if detection fails
   */
  private String detectBaseBranch(String taskBranch) throws IOException
  {
    String gitDir = runGitCommandSingleLine("rev-parse", "--git-dir");
    Path catBasePath = Paths.get(gitDir, "worktrees", taskBranch, "cat-base");

    if (!Files.exists(catBasePath))
    {
      throw new IOException("cat-base file not found: " + catBasePath +
        ". Recreate worktree with /cat:work.");
    }

    return Files.readString(catBasePath, StandardCharsets.UTF_8).trim();
  }

  /**
   * Gets the current branch name.
   *
   * @return the branch name
   * @throws IOException if the operation fails
   */
  private String getCurrentBranch() throws IOException
  {
    return runGitCommandSingleLine("branch", "--show-current");
  }


  /**
   * Checks if the working directory is clean.
   *
   * @return true if clean
   * @throws IOException if the operation fails
   */
  private boolean isWorkingDirectoryClean() throws IOException
  {
    String status = runGitCommand("status", "--porcelain");
    return status.isEmpty();
  }

  /**
   * Gets the commit count between two branches.
   *
   * @param base the base branch
   * @param task the task branch
   * @return the commit count
   * @throws IOException if the operation fails
   */
  private int getCommitCount(String base, String task) throws IOException
  {
    String count = runGitCommandSingleLine("rev-list", "--count", base + ".." + task);
    return Integer.parseInt(count);
  }

  /**
   * Checks if fast-forward merge is possible.
   *
   * @param base the base branch
   * @param task the task branch
   * @throws IOException if fast-forward is not possible
   */
  private void checkFastForwardPossible(String base, String task) throws IOException
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "merge-base", "--is-ancestor", base, task);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      int exitCode = process.waitFor();

      if (exitCode != 0)
      {
        int behindCount = getCommitCount(task, base);
        if (behindCount > 0)
        {
          throw new IOException("Task branch is behind " + base + " by " + behindCount +
            " commits. Rebase required: git checkout " + task + " && git rebase " + base);
        }
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while checking merge-base", e);
    }
  }

  /**
   * Gets the commit message for a branch.
   *
   * @param branch the branch name
   * @return the commit message
   * @throws IOException if the operation fails
   */
  private String getCommitMessage(String branch) throws IOException
  {
    return runGitCommandSingleLine("log", "-1", "--format=%s", branch);
  }

  /**
   * Gets the short SHA for a commit reference.
   *
   * @param ref the reference
   * @return the short SHA
   * @throws IOException if the operation fails
   */
  private String getCommitSha(String ref) throws IOException
  {
    return runGitCommandSingleLine("rev-parse", "--short", ref);
  }

  /**
   * Performs a fast-forward merge.
   *
   * @param branch the branch to merge
   * @throws IOException if the merge fails
   */
  private void fastForwardMerge(String branch) throws IOException
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "merge", "--ff-only", branch);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          if (output.length() > 0)
            output.append('\n');
          output.append(line);
          line = reader.readLine();
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new IOException("Fast-forward merge failed. Rebase task branch onto base first.");
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted during merge", e);
    }
  }

  /**
   * Verifies that the history is linear (no merge commits).
   *
   * @throws IOException if merge commits are detected
   */
  private void verifyLinearHistory() throws IOException
  {
    String parents = runGitCommandSingleLine("log", "-1", "--format=%p", "HEAD");
    String[] parentArray = parents.trim().split("\\s+");
    if (parentArray.length > 1)
      throw new IOException("Merge commit detected! History is not linear.");
  }

  /**
   * Finds the worktree path for a branch.
   *
   * @param branch the branch name
   * @return the worktree path, or empty string if not found
   * @throws IOException if the operation fails
   */
  private String findWorktreeForBranch(String branch) throws IOException
  {
    String output = runGitCommand("worktree", "list", "--porcelain");
    String[] lines = output.split("\n");

    String currentWorktree = "";
    for (String line : lines)
    {
      if (line.startsWith("worktree "))
        currentWorktree = line.substring("worktree ".length());
      else if (line.equals("branch refs/heads/" + branch))
        return currentWorktree;
    }

    return "";
  }

  /**
   * Removes a worktree.
   *
   * @param path the worktree path
   * @throws IOException if the operation fails
   */
  private void removeWorktree(String path) throws IOException
  {
    runGitCommand("worktree", "remove", path);
  }

  /**
   * Deletes a branch.
   *
   * @param branch the branch name
   * @return true if deleted successfully
   */
  private boolean deleteBranch(String branch)
  {
    try
    {
      runGitCommand("branch", "-d", branch);
      return true;
    }
    catch (IOException _)
    {
      return false;
    }
  }

  /**
   * Builds the success JSON response.
   *
   * @param taskBranch the task branch name
   * @param commitSha the commit SHA
   * @param commitMessage the commit message
   * @param commitCount the number of commits merged
   * @param cleanup whether cleanup was requested
   * @param worktreeRemoved whether the worktree was removed
   * @param branchDeleted whether the branch was deleted
   * @param duration the operation duration in seconds
   * @return JSON string
   * @throws IOException if JSON creation fails
   */
  private String buildSuccessJson(String taskBranch, String commitSha, String commitMessage,
    int commitCount, boolean cleanup, boolean worktreeRemoved, boolean branchDeleted, long duration)
    throws IOException
  {
    ObjectNode json = mapper.createObjectNode();
    json.put("status", "success");
    json.put("message", "Linear merge completed successfully");
    json.put("duration_seconds", duration);
    json.put("task_branch", taskBranch);
    json.put("commit_sha", commitSha);
    json.put("commit_message", commitMessage);
    json.put("commit_count", commitCount);
    json.put("cleanup", cleanup);
    json.put("worktree_removed", worktreeRemoved);
    json.put("branch_deleted", branchDeleted);
    json.put("timestamp", Instant.now().toString());

    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
  }

  /**
   * Main method for command-line execution.
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length == 0)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: git-merge-linear <issue-branch> [--base <branch>] [--cleanup|--no-cleanup]"
        }""");
      System.exit(1);
    }

    String taskBranch = args[0];
    String baseBranch = "";
    boolean cleanup = false;

    for (int i = 1; i < args.length; ++i)
    {
      if (args[i].equals("--base") && i + 1 < args.length)
      {
        baseBranch = args[i + 1];
        ++i;
      }
      else if (args[i].equals("--cleanup"))
      {
        cleanup = true;
      }
      else if (args[i].equals("--no-cleanup"))
      {
        cleanup = false;
      }
      else
      {
        System.err.println("""
          {
            "status": "error",
            "message": "Unknown argument: %s"
          }""".formatted(args[i]));
        System.exit(1);
      }
    }

    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      GitMergeLinear cmd = new GitMergeLinear(mapper);
      try
      {
        String result = cmd.execute(taskBranch, baseBranch, cleanup);
        System.out.println(result);
      }
      catch (IOException e)
      {
        System.err.println("""
          {
            "status": "error",
            "message": "%s"
          }""".formatted(e.getMessage().replace("\"", "\\\"")));
        System.exit(1);
      }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GitMergeLinear.class);
      log.error("Unexpected error", e);
      throw e;
    }
    }
  }
}
