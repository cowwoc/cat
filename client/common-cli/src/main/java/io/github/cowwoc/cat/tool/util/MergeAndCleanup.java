/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import static io.github.cowwoc.cat.tool.util.GitCommands.runGit;
import static io.github.cowwoc.cat.tool.util.GitCommands.runGitCommandSingleLineInDirectory;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import static io.github.cowwoc.cat.tool.Strings.block;

import io.github.cowwoc.cat.agent.Config;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.MainCliTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Merge issue branch and clean up worktree, branch, and lock.
 * <p>
 * Provides both monolithic merge-and-cleanup execution and split merge-only / cleanup-only phases
 * for CAT's /cat:work command.
 */
public final class MergeAndCleanup
{
  private final Logger log = LoggerFactory.getLogger(MergeAndCleanup.class);
  private final CliTool scope;

  /**
   * Creates a new MergeAndCleanup instance.
   *
   * @param scope the tool scope providing JSON mapper and plugin root
   * @throws NullPointerException if {@code scope} is null
   */
  public MergeAndCleanup(CliTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Executes the merge and cleanup operation.
   * <p>
   * Note: Both the issue worktree and the main worktree are checked before merge. The issue worktree's
   * state is verified via {@link #isWorktreeDirty(String)}, and the main worktree's state is verified
   * via {@link #verifyMainWorkspaceClean(String)}. The merge itself is atomic (git fast-forward merge)
   * and cannot be partially applied.
   *
   * @param projectPath the project root directory
   * @param issueId the issue identifier
   * @param sessionId the Claude session UUID
   * @param targetBranch the target branch name to merge to
   * @param worktreePath the optional worktree path (empty for auto-detect)
   * @param pluginRoot the plugin root directory
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String execute(String projectPath, String issueId, String sessionId, String targetBranch,
    String worktreePath, String pluginRoot) throws IOException
  {
    requireThat(projectPath, "projectPath").isNotBlank();
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();
    requireThat(worktreePath, "worktreePath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotBlank();

    long startTime = System.currentTimeMillis();

    Path projectRootPath = Paths.get(projectPath);
    if (!Files.isDirectory(projectRootPath.resolve(Config.CAT_DIR_NAME)))
      throw new IOException("Not a CAT project: '" + projectPath + "' (no .cat directory)");

    String taskBranch = issueId;

    if (worktreePath.isEmpty())
      worktreePath = findWorktreeForBranch(projectPath, taskBranch);

    if (worktreePath.isEmpty() || !Files.isDirectory(Paths.get(worktreePath)))
      throw new IOException("Worktree not found for issue branch: " + taskBranch);

    guardAgainstSelfDeletingCleanup(worktreePath, pluginRoot);

    String commitSha = mergeIssueBranch(projectPath, worktreePath, targetBranch, taskBranch);
    boolean lockReleased = cleanupMergedIssue(projectPath, issueId, sessionId, targetBranch,
      worktreePath, taskBranch, commitSha, pluginRoot);

    long endTime = System.currentTimeMillis();
    long duration = (endTime - startTime) / 1000;

    return buildSuccessJson(issueId, targetBranch, commitSha, lockReleased, duration);
  }

  /**
   * Merges an issue branch into the target branch without removing the worktree or deleting the branch.
   *
   * @param projectPath the project root directory
   * @param issueId the issue identifier
   * @param targetBranch the target branch name to merge to
   * @param worktreePath the optional worktree path (empty for auto-detect)
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String executeMergeOnly(String projectPath, String issueId, String targetBranch,
    String worktreePath) throws IOException
  {
    requireThat(projectPath, "projectPath").isNotBlank();
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();
    requireThat(worktreePath, "worktreePath").isNotNull();

    Path projectRootPath = Paths.get(projectPath);
    if (!Files.isDirectory(projectRootPath.resolve(Config.CAT_DIR_NAME)))
      throw new IOException("Not a CAT project: '" + projectPath + "' (no .cat directory)");

    String taskBranch = issueId;
    if (worktreePath.isEmpty())
      worktreePath = findWorktreeForBranch(projectPath, taskBranch);

    if (worktreePath.isEmpty() || !Files.isDirectory(Paths.get(worktreePath)))
      throw new IOException("Worktree not found for issue branch: " + taskBranch);

    long startTime = System.currentTimeMillis();
    String commitSha = mergeIssueBranch(projectPath, worktreePath, targetBranch, taskBranch);
    long duration = (System.currentTimeMillis() - startTime) / 1000;
    return buildMergeJson(issueId, targetBranch, commitSha, duration);
  }

  /**
   * Cleans up a worktree, branch, and lock after verifying the target branch contains the expected commit.
   *
   * @param projectPath the project root directory
   * @param issueId the issue identifier
   * @param sessionId the Claude session UUID
   * @param targetBranch the target branch name that must contain the merged commit
   * @param worktreePath the optional worktree path (empty for auto-detect)
   * @param expectedCommit the expected merged commit SHA or prefix
   * @param pluginRoot the plugin root directory
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String executeCleanupOnly(String projectPath, String issueId, String sessionId,
    String targetBranch, String worktreePath, String expectedCommit, String pluginRoot) throws IOException
  {
    requireThat(projectPath, "projectPath").isNotBlank();
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();
    requireThat(worktreePath, "worktreePath").isNotNull();
    requireThat(expectedCommit, "expectedCommit").isNotBlank();
    requireThat(pluginRoot, "pluginRoot").isNotBlank();

    Path projectRootPath = Paths.get(projectPath);
    if (!Files.isDirectory(projectRootPath.resolve(Config.CAT_DIR_NAME)))
      throw new IOException("Not a CAT project: '" + projectPath + "' (no .cat directory)");

    String taskBranch = issueId;
    if (worktreePath.isEmpty())
      worktreePath = findWorktreeForBranch(projectPath, taskBranch);

    long startTime = System.currentTimeMillis();
    boolean lockReleased = cleanupMergedIssue(projectPath, issueId, sessionId, targetBranch,
      worktreePath, taskBranch, expectedCommit, pluginRoot);
    long duration = (System.currentTimeMillis() - startTime) / 1000;
    return buildCleanupJson(issueId, targetBranch, expectedCommit, lockReleased, duration);
  }

  /**
   * Finds the worktree path for a branch.
   *
   * @param projectPath the project directory
   * @param branch the branch name
   * @return the worktree path, or empty string if not found
   * @throws IOException if the operation fails
   */
  private String findWorktreeForBranch(String projectPath, String branch) throws IOException
  {
    String output = runGit(Path.of(projectPath), "worktree", "list", "--porcelain");
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
   * Merges an issue branch into the target branch and leaves cleanup to a separate phase.
   *
   * @param projectPath the project root directory
   * @param worktreePath the issue worktree path
   * @param targetBranch the target branch name
   * @param taskBranch the issue branch name
   * @return the merged commit SHA
   * @throws IOException if the operation fails
   */
  private String mergeIssueBranch(String projectPath, String worktreePath, String targetBranch,
    String taskBranch) throws IOException
  {
    if (isWorktreeDirty(worktreePath))
    {
      throw new IOException("Worktree has uncommitted changes: " + worktreePath +
        ". Commit or stash changes first.");
    }

    verifyMainWorkspaceClean(projectPath);

    syncTargetBranchWithOrigin(projectPath, targetBranch);

    int diverged = getDivergenceCount(worktreePath, targetBranch);
    if (diverged > 0)
      rebaseOnto(worktreePath, targetBranch);

    if (!isFastForwardPossible(worktreePath, targetBranch))
    {
      throw new IOException("Fast-forward merge not possible. Issue branch has diverged from " +
        targetBranch + ". Rebase required.");
    }

    String commitSha = getCommitSha(worktreePath, "HEAD");
    fastForwardMerge(projectPath, taskBranch);
    return commitSha;
  }

  /**
   * Verifies the main workspace working tree is clean before merge.
   *
   * @param projectPath the project root directory (main worktree)
   * @throws IOException if the working tree has uncommitted changes, with message listing dirty files
   */
  private void verifyMainWorkspaceClean(String projectPath) throws IOException
  {
    String dirty = runGit(Path.of(projectPath), "status", "--porcelain");
    if (!dirty.isEmpty())
    {
      throw new IOException(
        "Cannot merge: main workspace has uncommitted changes in '" + projectPath + "'.\n" +
          "Commit or discard these changes before merging:\n" + dirty);
    }
  }

  /**
   * Fetches the target branch from origin and fast-forwards the local target branch to match
   * using {@code git merge --ff-only}. This updates the ref, index, and working tree atomically.
   *
   * @param projectPath the project root directory (main worktree)
   * @param targetBranch the target branch name
   * @throws IOException if fetch fails (network/remote unavailable) or fast-forward fails
   *   (local branch has diverged from origin)
   */
  private void syncTargetBranchWithOrigin(String projectPath, String targetBranch) throws IOException
  {
    try
    {
      runGit(Path.of(projectPath), "fetch", "origin", targetBranch);
    }
    catch (IOException e)
    {
      throw new IOException(
        "Failed to fetch origin/" + targetBranch + " in directory: " + projectPath +
          ". Check network connectivity and that 'origin' remote is available. " +
          "Original error: " + e.getMessage(), e);
    }

    mergeWithRetry(projectPath, "origin/" + targetBranch,
      "Failed to update local " + targetBranch + " to match origin/" + targetBranch +
        " in directory: " + projectPath + ". The local " + targetBranch +
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
   * Fast-forward merges the issue branch into the target branch using {@code git merge --ff-only}.
   * <p>
   * This is run in the main worktree ({@code projectPath}), which atomically updates the ref,
   * index, and working tree. Retries up to 3 times on index.lock contention.
   *
   * @param projectPath the project root directory (main worktree)
   * @param issueBranch the issue branch to merge
   * @throws IOException if the merge fails
   */
  private void fastForwardMerge(String projectPath, String issueBranch) throws IOException
  {
    mergeWithRetry(projectPath, issueBranch,
      "Fast-forward merge of " + issueBranch + " failed in directory: " + projectPath +
        ". The target branch may have diverged.");
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
   * @param projectPath the project directory
   * @param worktreePath the worktree path
   * @throws IOException if the operation fails
   */
  private void removeWorktree(String projectPath, String worktreePath) throws IOException
  {
    runGit(Path.of(projectPath), "worktree", "remove", worktreePath);
  }

  /**
   * Deletes a branch.
   *
   * @param projectPath the project directory
   * @param branch the branch name
   * @throws IOException if the operation fails
   */
  private void deleteBranch(String projectPath, String branch) throws IOException
  {
    runGit(Path.of(projectPath), "branch", "-d", branch);
  }

  /**
   * Cleans up merged issue artifacts after verifying the target branch contains the expected commit.
   *
   * @param projectPath the project root directory
   * @param issueId the issue identifier
   * @param sessionId the Claude session UUID
   * @param targetBranch the target branch name
   * @param worktreePath the optional issue worktree path
   * @param taskBranch the issue branch name
   * @param expectedCommit the expected merged commit SHA or prefix
   * @param pluginRoot the plugin root directory
   * @return true if the lock was released
   * @throws IOException if cleanup is unsafe or cannot be completed
   */
  private boolean cleanupMergedIssue(String projectPath, String issueId, String sessionId,
    String targetBranch, String worktreePath, String taskBranch, String expectedCommit, String pluginRoot)
    throws IOException
  {
    if (worktreePath != null && !worktreePath.isEmpty() && Files.isDirectory(Path.of(worktreePath)))
      guardAgainstSelfDeletingCleanup(worktreePath, pluginRoot);

    String targetCommit = runGitCommandSingleLineInDirectory(projectPath, "rev-parse", targetBranch);
    if (!targetCommit.startsWith(expectedCommit) && !expectedCommit.startsWith(targetCommit))
    {
      throw new IOException("Refusing cleanup: " + targetBranch + " points to " + targetCommit +
        ", not expected merged commit " + expectedCommit);
    }

    if (worktreePath != null && !worktreePath.isEmpty() && Files.isDirectory(Path.of(worktreePath)))
      removeWorktree(projectPath, worktreePath);
    if (branchExists(projectPath, taskBranch))
      deleteBranch(projectPath, taskBranch);

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
    return lockReleased;
  }

  /**
   * Verifies cleanup will not delete the engine context that is executing the cleanup.
   *
   * @param worktreePath the worktree that would be removed
   * @param pluginRoot the active plugin root
   * @throws IOException if cleanup would remove the active engine context
   */
  private void guardAgainstSelfDeletingCleanup(String worktreePath, String pluginRoot) throws IOException
  {
    Path worktree = Path.of(worktreePath).toAbsolutePath().normalize();
    rejectInsideWorktree("plugin root", Path.of(pluginRoot), worktree);
    rejectInsideWorktree("current working directory", Path.of(System.getProperty("user.dir")), worktree);
    rejectInsideWorktree("Java engine", Path.of(System.getProperty("java.home")), worktree);
  }

  private void rejectInsideWorktree(String name, Path path, Path worktree) throws IOException
  {
    Path normalized = path.toAbsolutePath().normalize();
    if (normalized.startsWith(worktree))
    {
      throw new IOException("Refusing to remove worktree while " + name +
        " is inside the worktree being removed: " + normalized);
    }
  }

  private boolean branchExists(String projectPath, String branch) throws IOException
  {
    try
    {
      runGit(Path.of(projectPath), "show-ref", "--verify", "--quiet", "refs/heads/" + branch);
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

  private String buildMergeJson(String issueId, String targetBranch, String commitSha, long duration)
    throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "success");
    json.put("phase", "merge");
    json.put("message", "Merged issue branch; cleanup still required");
    json.put("issue_id", issueId);
    json.put("target_branch", targetBranch);
    json.put("merged_commit", commitSha);
    json.put("cleanup_required", true);
    json.put("duration_seconds", duration);
    return scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(json);
  }

  private String buildCleanupJson(String issueId, String targetBranch, String commitSha,
    boolean lockReleased, long duration)
    throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "success");
    json.put("phase", "cleanup");
    json.put("message", "Cleaned up merged issue");
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
   */
  public static void main(String[] args)
  {
    try (CliTool scope = new MainCliTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(MergeAndCleanup.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the merge-and-cleanup command.
   *
   * @param scope the JVM scope
   * @param args  command-line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException if any of {@code scope}, {@code args}, or {@code out} are null
   * @throws IOException          if the operation fails
   */
  public static void run(CliTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length < 4)
    {
      out.println(block(scope,
        "Usage: merge-and-cleanup <project-dir> <issue-id> <session-id> <target-branch> [--worktree <path>]"));
      return;
    }

    String worktreePath = "";
    String expectedCommit = "";
    boolean mergeOnly = false;
    boolean cleanupOnly = false;
    for (int i = 4; i < args.length; ++i)
    {
      if (args[i].equals("--worktree") && i + 1 < args.length)
      {
        worktreePath = args[i + 1];
        ++i;
      }
      else if (args[i].equals("--expected-commit") && i + 1 < args.length)
      {
        expectedCommit = args[i + 1];
        ++i;
      }
      else if (args[i].equals("--merge-only"))
      {
        mergeOnly = true;
      }
      else if (args[i].equals("--cleanup-only"))
      {
        cleanupOnly = true;
      }
      else
      {
        throw new IllegalArgumentException(
          "Unknown argument: " + args[i] +
            ". Valid arguments: --worktree <path>, --merge-only, --cleanup-only, --expected-commit <sha>");
      }
    }
    if (mergeOnly && cleanupOnly)
      throw new IllegalArgumentException("--merge-only and --cleanup-only are mutually exclusive");
    if (cleanupOnly && expectedCommit.isBlank())
      throw new IllegalArgumentException("--cleanup-only requires --expected-commit <sha>");

    String projectPath = args[0];
    String issueId = args[1];
    String sessionId = args[2];
    String targetBranch = args[3];
    String pluginRoot = scope.getPluginRoot().toString();
    MergeAndCleanup cmd = new MergeAndCleanup(scope);
    try
    {
      String result;
      if (mergeOnly)
        result = cmd.executeMergeOnly(projectPath, issueId, targetBranch, worktreePath);
      else if (cleanupOnly)
      {
        result = cmd.executeCleanupOnly(projectPath, issueId, sessionId, targetBranch,
          worktreePath, expectedCommit, pluginRoot);
      }
      else
        result = cmd.execute(projectPath, issueId, sessionId, targetBranch, worktreePath, pluginRoot);
      out.println(result);
    }
    catch (IOException e)
    {
      out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
