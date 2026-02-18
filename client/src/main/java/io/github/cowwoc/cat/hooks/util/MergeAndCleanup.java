/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandInDirectory;
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
 * 1. Fast-forward merge issue branch to base branch (from worktree, no checkout required)
 * 2. Remove the issue worktree
 * 3. Delete the issue branch
 * 4. Release the issue lock
 */
public final class MergeAndCleanup
{
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
   * @param worktreePath the optional worktree path (empty for auto-detect)
   * @param pluginRoot the plugin root directory
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String execute(String projectDir, String issueId, String sessionId, String worktreePath,
    String pluginRoot) throws IOException
  {
    requireThat(projectDir, "projectDir").isNotBlank();
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(worktreePath, "worktreePath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotBlank();

    long startTime = System.currentTimeMillis();

    Path projectPath = Paths.get(projectDir);
    if (!Files.isDirectory(projectPath.resolve(".claude/cat")))
      throw new IOException("Not a CAT project: '" + projectDir + "' (no .claude/cat directory)");

    boolean autoRemoveWorktrees = getAutoRemoveWorktrees(projectPath);
    String taskBranch = issueId;

    if (worktreePath.isEmpty())
      worktreePath = findWorktreeForBranch(projectDir, taskBranch);

    if (worktreePath.isEmpty() || !Files.isDirectory(Paths.get(worktreePath)))
      throw new IOException("Worktree not found for issue branch: " + taskBranch);

    String baseBranch = getBaseBranch(projectDir, taskBranch);

    if (isWorktreeDirty(worktreePath))
    {
      throw new IOException("Worktree has uncommitted changes: " + worktreePath +
        ". Commit or stash changes first.");
    }

    int diverged = getDivergenceCount(worktreePath, baseBranch);
    if (diverged > 0)
    {
      throw new IOException("Base branch has diverged: " + baseBranch + " has " + diverged +
        " commit(s) not in HEAD. Rebase required before merge.");
    }

    if (!isFastForwardPossible(worktreePath, baseBranch))
    {
      throw new IOException("Fast-forward merge not possible. Issue branch has diverged from " +
        baseBranch + ". Rebase required.");
    }

    String commitSha = getCommitSha(worktreePath, "HEAD");
    fastForwardMerge(worktreePath, baseBranch);

    boolean worktreeRemoved = false;
    if (autoRemoveWorktrees)
    {
      removeWorktree(projectDir, worktreePath);
      worktreeRemoved = true;
    }

    boolean branchDeleted = false;
    if (autoRemoveWorktrees)
    {
      deleteBranch(projectDir, taskBranch);
      branchDeleted = true;
    }

    boolean lockReleased = false;
    Path lockScript = Paths.get(pluginRoot, "scripts/issue-lock.sh");
    if (Files.isExecutable(lockScript))
    {
      releaseLock(lockScript, projectDir, issueId, sessionId);
      lockReleased = true;
    }

    long endTime = System.currentTimeMillis();
    long duration = (endTime - startTime) / 1000;

    return buildSuccessJson(issueId, baseBranch, commitSha, worktreeRemoved, branchDeleted,
      lockReleased, duration);
  }

  /**
   * Gets the autoRemoveWorktrees setting from cat-config.json.
   *
   * @param projectPath the project root path
   * @return true if worktrees should be auto-removed
   * @throws IOException if the config file exists but cannot be read
   */
  private boolean getAutoRemoveWorktrees(Path projectPath) throws IOException
  {
    Path configPath = projectPath.resolve(".claude/cat/cat-config.json");
    if (!Files.exists(configPath))
      return true;

    String content = Files.readString(configPath, StandardCharsets.UTF_8);
    ObjectNode config = (ObjectNode) scope.getJsonMapper().readTree(content);

    return !config.has("autoRemoveWorktrees") || config.get("autoRemoveWorktrees").asBoolean(true);
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
    String output = runGitCommandInDirectory(projectDir, "worktree", "list", "--porcelain");
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
   * Gets the base branch from worktree's cat-base file.
   *
   * @param projectDir the project directory
   * @param taskBranch the task branch
   * @return the base branch name
   * @throws IOException if the file is missing
   */
  private String getBaseBranch(String projectDir, String taskBranch)
    throws IOException
  {
    String gitDir = runGitCommandInDirectory(projectDir, "rev-parse", "--git-dir");
    Path catBasePath = Paths.get(gitDir, "worktrees", taskBranch, "cat-base");

    if (!Files.exists(catBasePath))
    {
      throw new IOException("cat-base file missing for issue branch: " + taskBranch +
        ". Cannot determine base branch.");
    }

    return Files.readString(catBasePath, StandardCharsets.UTF_8).trim();
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
    String status = runGitCommandInDirectory(worktreePath, "status", "--porcelain");
    return !status.isEmpty();
  }

  /**
   * Gets the number of commits the base branch has that HEAD doesn't.
   *
   * @param worktreePath the worktree path
   * @param baseBranch the base branch
   * @return the divergence count
   * @throws IOException if the operation fails
   */
  private int getDivergenceCount(String worktreePath, String baseBranch) throws IOException
  {
    String count = runGitCommandSingleLineInDirectory(worktreePath, "rev-list", "--count",
      "HEAD.." + baseBranch);
    return Integer.parseInt(count);
  }

  /**
   * Checks if fast-forward merge is possible.
   *
   * @param worktreePath the worktree path
   * @param baseBranch the base branch
   * @return true if fast-forward is possible
   * @throws IOException if the git operation fails
   */
  private boolean isFastForwardPossible(String worktreePath, String baseBranch) throws IOException
  {
    try
    {
      String[] command = {"git", "-C", worktreePath, "merge-base",
        "--is-ancestor", baseBranch, "HEAD"};
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      int exitCode = process.waitFor();
      return exitCode == 0;
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while checking merge-base", e);
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
   * Performs a fast-forward merge using git push.
   *
   * @param worktreePath the worktree path
   * @param baseBranch the base branch
   * @throws IOException if the merge fails
   */
  private void fastForwardMerge(String worktreePath, String baseBranch) throws IOException
  {
    runGitCommandInDirectory(worktreePath, "push", ".", "HEAD:" + baseBranch);
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
    runGitCommandInDirectory(projectDir, "worktree", "remove", worktreePath);
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
    runGitCommandInDirectory(projectDir, "branch", "-d", branch);
  }

  /**
   * Releases the issue lock by calling the lock script.
   *
   * @param lockScript the lock script path
   * @param projectDir the project directory
   * @param issueId the issue ID
   * @param sessionId the session ID
   * @throws IOException if the operation fails
   */
  private void releaseLock(Path lockScript, String projectDir, String issueId, String sessionId)
    throws IOException
  {
    ProcessBuilder pb = new ProcessBuilder(lockScript.toString(), "release", projectDir,
      issueId, sessionId);
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

    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while releasing lock", e);
    }

    if (exitCode != 0)
      throw new IOException("Failed to release lock: " + output.toString());
  }

  /**
   * Builds the success JSON response.
   *
   * @param issueId the issue ID
   * @param baseBranch the base branch
   * @param commitSha the commit SHA
   * @param worktreeRemoved whether the worktree was removed
   * @param branchDeleted whether the branch was deleted
   * @param lockReleased whether the lock was released
   * @param duration the operation duration in seconds
   * @return JSON string
   * @throws IOException if JSON creation fails
   */
  private String buildSuccessJson(String issueId, String baseBranch, String commitSha,
    boolean worktreeRemoved, boolean branchDeleted, boolean lockReleased, long duration)
    throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "success");
    json.put("message", "Merged and cleaned up issue");
    json.put("issue_id", issueId);
    json.put("base_branch", baseBranch);
    json.put("commit_sha", commitSha);
    json.put("worktree_removed", worktreeRemoved);
    json.put("branch_deleted", branchDeleted);
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
    if (args.length < 3)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: merge-and-cleanup <project-dir> <issue-id> <session-id> [--worktree <path>]"
        }""");
      System.exit(1);
    }

    String projectDir = args[0];
    String issueId = args[1];
    String sessionId = args[2];
    String worktreePath = "";

    for (int i = 3; i < args.length; ++i)
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
        String result = cmd.execute(projectDir, issueId, sessionId, worktreePath, pluginRoot);
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
