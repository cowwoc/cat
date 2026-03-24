/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGit;
import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandSingleLineInDirectory;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import static io.github.cowwoc.cat.hooks.Strings.block;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import tools.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Linear git merge operation with backup and safety checks.
 * <p>
 * Merges source branch to target branch with linear history. After the merge, the source branch
 * still exists as a git ref but its commits are reachable from the target branch. Callers are
 * responsible for deleting the source branch and removing any associated worktree.
 */
public final class GitMergeLinear
{
  private final JvmScope scope;
  private final String directory;

  /**
   * Creates a new GitMergeLinear instance.
   *
   * @param scope the JVM scope providing JSON mapper
   * @param directory the working directory for git operations
   * @throws NullPointerException if {@code scope} is null
   * @throws IllegalArgumentException if {@code directory} is blank
   */
  public GitMergeLinear(JvmScope scope, String directory)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(directory, "directory").isNotBlank();
    this.scope = scope;
    this.directory = directory;
  }

  /**
   * Executes the linear merge operation.
   *
   * @param sourceBranch the issue branch to merge
   * @param targetBranch the branch to merge into
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String execute(String sourceBranch, String targetBranch) throws IOException
  {
    requireThat(sourceBranch, "sourceBranch").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();

    long startTime = System.currentTimeMillis();

    String currentBranch = getCurrentBranch();
    if (!currentBranch.equals(targetBranch))
      throw new IOException("Must be on " + targetBranch + " branch. Currently on: " + currentBranch);

    runGitCommandSingleLineInDirectory(directory,"rev-parse", "--verify", sourceBranch);

    if (!isWorkingDirectoryClean())
      throw new IOException("Working directory is not clean. Commit or stash changes first.");

    int commitCount = getCommitCount(targetBranch, sourceBranch);
    if (commitCount != 1)
      throw new IOException("Source branch must have exactly 1 commit. Found: " + commitCount +
        ". Squash commits first.");

    checkFastForwardPossible(targetBranch, sourceBranch);

    String commitMsg = getCommitMessage(sourceBranch);

    fastForwardMerge(sourceBranch);

    verifyLinearHistory();

    String commitShaAfter = getCommitSha("HEAD");

    long endTime = System.currentTimeMillis();
    long duration = (endTime - startTime) / 1000;

    return buildSuccessJson(sourceBranch, commitShaAfter, commitMsg, commitCount, duration);
  }

  /**
   * Gets the current branch name.
   *
   * @return the branch name
   * @throws IOException if the operation fails
   */
  private String getCurrentBranch() throws IOException
  {
    return runGitCommandSingleLineInDirectory(directory,"branch", "--show-current");
  }


  /**
   * Checks if the working directory is clean.
   *
   * @return true if clean
   * @throws IOException if the operation fails
   */
  private boolean isWorkingDirectoryClean() throws IOException
  {
    String status = runGit(Path.of(directory),"status", "--porcelain");
    return status.isEmpty();
  }

  /**
   * Gets the commit count between two branches.
   *
   * @param targetBranch the target branch
   * @param sourceBranch the source branch
   * @return the commit count
   * @throws IOException if the operation fails
   */
  private int getCommitCount(String targetBranch, String sourceBranch) throws IOException
  {
    String count = runGitCommandSingleLineInDirectory(directory, "rev-list", "--count",
      targetBranch + ".." + sourceBranch);
    return Integer.parseInt(count);
  }

  /**
   * Checks if fast-forward merge is possible.
   *
   * @param targetBranch the target branch
   * @param sourceBranch the source branch
   * @throws IOException if fast-forward is not possible
   */
  private void checkFastForwardPossible(String targetBranch, String sourceBranch) throws IOException
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "merge-base", "--is-ancestor", targetBranch, sourceBranch);
      pb.directory(Path.of(directory).toFile());
      pb.redirectErrorStream(true);
      try (Process process = pb.start())
      {
        int exitCode = process.waitFor();

        if (exitCode != 0)
        {
          int behindCount = getCommitCount(sourceBranch, targetBranch);
          if (behindCount > 0)
          {
            throw new IOException("Source branch is behind " + targetBranch + " by " + behindCount +
              " commits. Rebase required: git checkout " + sourceBranch + " && git rebase " + targetBranch);
          }
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
    return runGitCommandSingleLineInDirectory(directory,"log", "-1", "--format=%s", branch);
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
    return runGitCommandSingleLineInDirectory(directory,"rev-parse", "--short", ref);
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
      pb.directory(Path.of(directory).toFile());
      pb.redirectErrorStream(true);
      try (Process process = pb.start())
      {
        StringJoiner output = new StringJoiner("\n");
        try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
          String line = reader.readLine();
          while (line != null)
          {
            output.add(line);
            line = reader.readLine();
          }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0)
          throw new IOException("Fast-forward merge failed. Rebase task branch onto base first.");
      }
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
    String parents = runGitCommandSingleLineInDirectory(directory,"log", "-1", "--format=%p", "HEAD");
    String[] parentArray = parents.strip().split("\\s+");
    if (parentArray.length > 1)
      throw new IOException("Merge commit detected! History is not linear.");
  }

  /**
   * Builds the success JSON response.
   *
   * @param sourceBranch the source branch name
   * @param commitSha the commit SHA
   * @param commitMessage the commit message
   * @param commitCount the number of commits merged
   * @param duration the operation duration in seconds
   * @return JSON string
   * @throws IOException if JSON creation fails
   */
  private String buildSuccessJson(String sourceBranch, String commitSha, String commitMessage,
    int commitCount, long duration)
    throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "success");
    json.put("message", "Linear merge completed successfully");
    json.put("duration_seconds", duration);
    json.put("source_branch", sourceBranch);
    json.put("merged_commit", commitSha);
    json.put("merged_commit_message", commitMessage);
    json.put("commits_merged", commitCount);
    json.put("timestamp", Instant.now().toString());

    return scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(json);
  }

  /**
   * Main method for command-line execution.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GitMergeLinear.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the git-merge-linear logic with a caller-provided output stream.
   * <p>
   * Separated from {@link #main(String[])} to allow unit testing without JVM exit.
   * IOException is converted to a block response on {@code out}.
   *
   * @param scope the JVM scope
   * @param args  command-line arguments
   * @param out   the output stream to write JSON to
   * @throws NullPointerException if {@code args} or {@code out} are null
   */
  public static void run(JvmScope scope, String[] args, PrintStream out)
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length == 0)
    {
      out.println(block(scope, "Usage: git-merge-linear <issue-branch> --target <branch>"));
      return;
    }

    String sourceBranch = args[0];
    String targetBranch = "";

    for (int i = 1; i < args.length; ++i)
    {
      if (args[i].equals("--target") && i + 1 < args.length)
      {
        targetBranch = args[i + 1];
        ++i;
      }
      else
      {
        out.println(block(scope, "Unknown argument: " + args[i]));
        return;
      }
    }

    if (targetBranch.isEmpty())
    {
      out.println(block(scope,
        "Missing required argument: --target <branch>. " +
        "Usage: git-merge-linear <issue-branch> --target <branch>"));
      return;
    }

    GitMergeLinear cmd = new GitMergeLinear(scope, ".");
    try
    {
      String result = cmd.execute(sourceBranch, targetBranch);
      out.println(result);
    }
    catch (IOException e)
    {
      out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
