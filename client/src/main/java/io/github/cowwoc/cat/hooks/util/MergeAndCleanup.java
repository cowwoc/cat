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

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Merge issue branch and clean up worktree, branch, and lock.
 * <p>
 * Handles the happy path of the merging phase for CAT's /cat:work command:
 * 1. Fast-forward merge issue branch to base branch in the main worktree
 * 2. Remove the issue worktree
 * 3. Delete the issue branch
 * 4. Release the issue lock
 */
public final class MergeAndCleanup
{
  private final Logger log = LoggerFactory.getLogger(MergeAndCleanup.class);
  private final JvmScope scope;

  /**
   * Creates a new MergeAndCleanup instance.
   *
   * @param scope the JVM scope providing JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public MergeAndCleanup(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Executes the merge and cleanup operation.
   *
   * @param projectDir the project root directory
   * @param issueId the issue identifier
   * @param sessionId the Claude session UUID
   * @param targetBranch the target branch name to merge to
   * @param worktreePath the optional worktree path (empty for auto-detect)
   * @param pluginRoot the plugin root directory
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String execute(String projectDir, String issueId, String sessionId, String targetBranch,
    String worktreePath, String pluginRoot) throws IOException
  {
    requireThat(projectDir, "projectDir").isNotBlank();
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();
    requireThat(worktreePath, "worktreePath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotBlank();

    long startTime = System.currentTimeMillis();

    Path projectPath = Paths.get(projectDir);
    if (!Files.isDirectory(projectPath.resolve(".claude/cat")))
      throw new IOException("Not a CAT project: '" + projectDir + "' (no .claude/cat directory)");

    String taskBranch = issueId;

    if (worktreePath.isEmpty())
      worktreePath = findWorktreeForBranch(projectDir, taskBranch);

    if (worktreePath.isEmpty() || !Files.isDirectory(Paths.get(worktreePath)))
      throw new IOException("Worktree not found for issue branch: " + taskBranch);

    if (isWorktreeDirty(worktreePath))
    {
      throw new IOException("Worktree has uncommitted changes: " + worktreePath +
        ". Commit or stash changes first.");
    }

    syncBaseBranchWithOrigin(projectDir, targetBranch);

    int diverged = getDivergenceCount(worktreePath, targetBranch);
    if (diverged > 0)
      rebaseOnto(worktreePath, targetBranch);

    if (!isFastForwardPossible(worktreePath, targetBranch))
    {
      throw new IOException("Fast-forward merge not possible. Issue branch has diverged from " +
        targetBranch + ". Rebase required.");
    }

    String commitSha = getCommitSha(worktreePath, "HEAD");
    fastForwardMerge(projectDir, taskBranch);

    removeWorktree(projectDir, worktreePath);
    deleteBranch(projectDir, taskBranch);

    boolean lockReleased = false;
    try
    {
      IssueLock issueLock = new IssueLock(scope);
      issueLock.release(issueId, sessionId);
      lockReleased = true;
    }
    catch (IllegalArgumentException _)
    {
      // Not a CAT project or lock directory not set up - skip lock release
    }

    long endTime = System.currentTimeMillis();
    long duration = (endTime - startTime) / 1000;

    return buildSuccessJson(issueId, targetBranch, commitSha, lockReleased, duration);
  }

  /**
   * Finds the worktree path for a branch.
   *
   * @param projectDir the project directory
   * @param branch the branch name
   * @return the worktree path, or empty string if not found
   * @throws IOException if the operation fails
   */
  private String findWorktreeForBranch(String projectDir, String branch) throws IOException
  {
    String output = runGit(Path.of(projectDir), "worktree", "list", "--porcelain");
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
   * Checks if a worktree has uncommitted changes.
   *
   * @param worktreePath the worktree path
   * @return true if dirty
   * @throws IOException if the operation fails
   */
  private boolean isWorktreeDirty(String worktreePath) throws IOException
  {
    String status = runGit(Path.of(worktreePath), "status", "--porcelain");
    return !status.isEmpty();
  }

  /**
   * Fetches the target branch from origin and fast-forwards the local target branch to match
   * using {@code git merge --ff-only}. This updates the ref, index, and working tree atomically.
   *
   * @param projectDir the project root directory (main worktree)
   * @param targetBranch the target branch name
   * @throws IOException if fetch fails (network/remote unavailable) or fast-forward fails
   *   (local branch has diverged from origin)
   */
  private void syncBaseBranchWithOrigin(String projectDir, String targetBranch) throws IOException
  {
    try
    {
      runGit(Path.of(projectDir), "fetch", "origin", targetBranch);
    }
    catch (IOException e)
    {
      throw new IOException(
        "Failed to fetch origin/" + targetBranch + " in directory: " + projectDir +
          ". Check network connectivity and that 'origin' remote is available. " +
          "Original error: " + e.getMessage(), e);
    }

    mergeWithRetry(projectDir, "origin/" + targetBranch,
      "Failed to update local " + targetBranch + " to match origin/" + targetBranch +
        " in directory: " + projectDir + ". The local " + targetBranch +
        " branch has diverged from origin and cannot be fast-forwarded. " +
        "Resolve the divergence before merging.");
  }

  /**
   * Gets the number of commits the target branch has that HEAD doesn't.
   *
   * @param worktreePath the worktree path
   * @param targetBranch the target branch
   * @return the divergence count
   * @throws IOException if the operation fails
   */
  private int getDivergenceCount(String worktreePath, String targetBranch) throws IOException
  {
    String count = runGitCommandSingleLineInDirectory(worktreePath, "rev-list", "--count",
      "HEAD.." + targetBranch);
    return Integer.parseInt(count);
  }

  /**
   * Rebases the worktree branch onto the target branch using {@code git rebase --onto}.
   * <p>
   * This replays only the issue-specific commits onto the current tip of the target branch,
   * avoiding the "120 skipped previously applied commit" problem from naive {@code git rebase <target>}.
   * <p>
   * If rebase fails due to conflicts, the rebase is aborted and an {@code IOException} is thrown.
   *
   * @param worktreePath the worktree path
   * @param targetBranch the target branch to rebase onto
   * @throws IOException if the rebase fails or is interrupted
   */
  private void rebaseOnto(String worktreePath, String targetBranch) throws IOException
  {
    Path worktree = Path.of(worktreePath);
    String mergeBase = runGit(worktree, "merge-base", "HEAD", targetBranch).strip();

    try
    {
      runGit(worktree, "rebase", "--onto", targetBranch, mergeBase);
    }
    catch (IOException e)
    {
      try
      {
        runGit(worktree, "rebase", "--abort");
      }
      catch (IOException _)
      {
        // Abort best-effort: ignore errors
      }
      throw new IOException("Rebase --onto failed in worktree: " + worktreePath +
        ". Conflicts may exist.", e);
    }
  }

  /**
   * Checks if fast-forward merge is possible.
   *
   * @param worktreePath the worktree path
   * @param targetBranch the target branch
   * @return true if fast-forward is possible
   * @throws IOException if the git operation fails
   */
  private boolean isFastForwardPossible(String worktreePath, String targetBranch) throws IOException
  {
    try
    {
      runGit(Path.of(worktreePath), "merge-base", "--is-ancestor", targetBranch, "HEAD");
      return true;
    }
    catch (IOException _)
    {
      return false;
    }
  }

  /**
   * Gets the short SHA for a commit reference.
   *
   * @param worktreePath the worktree path
   * @param ref the reference
   * @return the short SHA
   * @throws IOException if the operation fails
   */
  private String getCommitSha(String worktreePath, String ref) throws IOException
  {
    return runGitCommandSingleLineInDirectory(worktreePath, "rev-parse", "--short", ref);
  }

  /**
   * Fast-forward merges the issue branch into the base branch using {@code git merge --ff-only}.
   * <p>
   * This is run in the main worktree ({@code projectDir}), which atomically updates the ref,
   * index, and working tree. Retries up to 3 times on index.lock contention.
   *
   * @param projectDir the project root directory (main worktree)
   * @param issueBranch the issue branch to merge
   * @throws IOException if the merge fails
   */
  private void fastForwardMerge(String projectDir, String issueBranch) throws IOException
  {
    mergeWithRetry(projectDir, issueBranch,
      "Fast-forward merge of " + issueBranch + " failed in directory: " + projectDir +
        ". The base branch may have diverged.");
  }

  /**
   * Runs {@code git merge --ff-only <ref>} in the given directory, retrying up to 3 times
   * if the failure is caused by index.lock contention from concurrent agents.
   * <p>
   * Fails fast with a clear error if uncommitted changes would be overwritten by the merge.
   *
   * @param directory the directory to run the merge in
   * @param ref the ref to merge (e.g., branch name or "origin/branch")
   * @param failureMessage the message to include in the exception if the merge fails
   *   for reasons other than index.lock contention
   * @throws IOException if the merge fails after retries or due to a non-retryable error
   */
  private void mergeWithRetry(String directory, String ref, String failureMessage)
    throws IOException
  {
    int maxRetries = 3;
    for (int attempt = 1; attempt <= maxRetries; ++attempt)
    {
      String[] command = {"git", "-C", directory, "merge", "--ff-only", ref};
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          if (!output.isEmpty())
            output.append('\n');
          output.append(line);
          line = reader.readLine();
        }
      }
      int exitCode;
      try
      {
        exitCode = process.waitFor();
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while running: " + String.join(" ", command), e);
      }
      if (exitCode == 0)
        return;

      String errorOutput = output.toString();

      if (errorOutput.contains("would be overwritten"))
      {
        throw new IOException("Uncommitted changes in " + directory +
          " would be overwritten by merge. Commit or stash changes first. " +
          "Git output: " + errorOutput);
      }

      if (errorOutput.contains("index.lock") && attempt < maxRetries)
      {
        log.debug("index.lock contention on attempt {}/{}, retrying in 1 second: {}",
          attempt, maxRetries, directory);
        try
        {
          Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while waiting to retry merge", e);
        }
        continue;
      }

      throw new IOException(failureMessage + " Original error: " + errorOutput);
    }
  }

  /**
   * Removes a worktree.
   *
   * @param projectDir the project directory
   * @param worktreePath the worktree path
   * @throws IOException if the operation fails
   */
  private void removeWorktree(String projectDir, String worktreePath) throws IOException
  {
    runGit(Path.of(projectDir), "worktree", "remove", worktreePath);
  }

  /**
   * Deletes a branch.
   *
   * @param projectDir the project directory
   * @param branch the branch name
   * @throws IOException if the operation fails
   */
  private void deleteBranch(String projectDir, String branch) throws IOException
  {
    runGit(Path.of(projectDir), "branch", "-d", branch);
  }


  /**
   * Builds the success JSON response.
   *
   * @param issueId the issue ID
   * @param targetBranch the target branch
   * @param commitSha the commit SHA
   * @param lockReleased whether the lock was released
   * @param duration the operation duration in seconds
   * @return JSON string
   * @throws IOException if JSON creation fails
   */
  private String buildSuccessJson(String issueId, String targetBranch, String commitSha,
    boolean lockReleased, long duration)
    throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "success");
    json.put("message", "Merged and cleaned up issue");
    json.put("issue_id", issueId);
    json.put("target_branch", targetBranch);
    json.put("merged_commit", commitSha);
    json.put("lock_released", lockReleased);
    json.put("duration_seconds", duration);

    return scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(json);
  }

  /**
   * Main method for command-line execution.
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 4)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: merge-and-cleanup <project-dir> <issue-id> <session-id> <target-branch> \\
[--worktree <path>]"
        }""");
      System.exit(1);
    }

    String projectDir = args[0];
    String issueId = args[1];
    String sessionId = args[2];
    String targetBranch = args[3];
    String worktreePath = "";

    for (int i = 4; i < args.length; ++i)
    {
      if (args[i].equals("--worktree") && i + 1 < args.length)
      {
        worktreePath = args[i + 1];
        ++i;
      }
    }

    try (JvmScope scope = new MainJvmScope())
    {
      String pluginRoot = scope.getClaudePluginRoot().toString();
      MergeAndCleanup cmd = new MergeAndCleanup(scope);
      try
      {
        String result = cmd.execute(projectDir, issueId, sessionId, targetBranch, worktreePath, pluginRoot);
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
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(MergeAndCleanup.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}
