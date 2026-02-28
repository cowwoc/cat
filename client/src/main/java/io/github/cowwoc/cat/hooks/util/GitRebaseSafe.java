/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.CatMetadata;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandSingleLineInDirectory;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Safe git rebase with backup creation, conflict detection, and content verification.
 * <p>
 * Reads the target from {@code cat-branch-point} (a 40-character commit hash) when no target is provided,
 * creates a timestamped backup branch, attempts the rebase, verifies no content changes, counts
 * commits rebased, and cleans up the backup.
 */
public final class GitRebaseSafe
{
  private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private final JvmScope scope;
  private final String sourceBranch;

  /**
   * Creates a new GitRebaseSafe instance.
   *
   * @param scope        the JVM scope providing JSON mapper
   * @param sourceBranch the working directory or worktree path for git commands
   * @throws NullPointerException     if {@code scope} is null
   * @throws IllegalArgumentException if {@code sourceBranch} is blank
   */
  public GitRebaseSafe(JvmScope scope, String sourceBranch)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(sourceBranch, "sourceBranch").isNotBlank();
    this.scope = scope;
    this.sourceBranch = sourceBranch;
  }

  /**
   * Executes the safe rebase operation.
   * <p>
   * The process:
   * <ol>
   *   <li>If target not provided, read commit hash from cat-branch-point file</li>
   *   <li>Create timestamped backup branch</li>
   *   <li>Attempt rebase</li>
   *   <li>On conflict: collect conflicting files, abort rebase, return CONFLICT JSON</li>
   *   <li>On success: verify backup and HEAD have the same tree state</li>
   *   <li>Count commits rebased</li>
   *   <li>Clean up backup</li>
   *   <li>Return OK JSON</li>
   * </ol>
   * <p>
   * Success JSON is written to stdout; error/conflict JSON to stderr.
   *
   * @param targetBranch the branch or commit hash to rebase onto, or empty string to read from cat-branch-point file
   * @return JSON string with operation result
   * @throws NullPointerException if {@code targetBranch} is null
   * @throws IOException          if the operation fails
   */
  public String execute(String targetBranch) throws IOException
  {
    requireThat(targetBranch, "targetBranch").isNotNull();

    // Step 1: If target branch not provided, read from cat-branch-point file
    String resolvedTarget = targetBranch;
    if (resolvedTarget.isEmpty())
    {
      String gitDirStr;
      try
      {
        gitDirStr = runGitCommandSingleLineInDirectory(sourceBranch, "rev-parse", "--git-dir");
      }
      catch (IOException e)
      {
        return buildErrorJson("Failed to resolve git dir: " + e.getMessage(), null, null);
      }

      Path gitDir = Path.of(gitDirStr);
      if (!gitDir.isAbsolute())
        gitDir = Path.of(sourceBranch).resolve(gitDir);

      Path catBranchPointFile = gitDir.resolve(CatMetadata.BRANCH_POINT_FILE);
      if (!Files.exists(catBranchPointFile))
      {
        return buildErrorJson(
          CatMetadata.BRANCH_POINT_FILE + " file not found: " + catBranchPointFile +
            ". Recreate worktree with /cat:work.", null, null);
      }
      resolvedTarget = Files.readString(catBranchPointFile).strip();
    }

    // Step 2: Create timestamped backup branch
    String backup = "backup-before-rebase-" + LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMATTER);
    ProcessRunner.Result branchResult = ProcessRunner.run(
      "git", "-C", sourceBranch, "branch", backup);
    if (branchResult.exitCode() != 0)
      return buildErrorJson("Failed to create backup branch: " + branchResult.stdout().strip(), null, null);

    // Verify backup was created (fail-fast)
    ProcessRunner.Result verifyResult = ProcessRunner.run(
      "git", "-C", sourceBranch, "show-ref", "--verify", "--quiet", "refs/heads/" + backup);
    if (verifyResult.exitCode() != 0)
    {
      return buildErrorJson(
        "Backup branch '" + backup + "' was not created. Do NOT proceed with rebase without backup.", null, null);
    }

    // Step 3: Attempt rebase
    ProcessRunner.Result rebaseResult = ProcessRunner.run(
      "git", "-C", sourceBranch, "rebase", resolvedTarget);

    if (rebaseResult.exitCode() != 0)
    {
      // Rebase failed — check if it's a conflict
      ProcessRunner.Result conflictResult = ProcessRunner.run(
        "git", "-C", sourceBranch, "diff", "--name-only", "--diff-filter=U");
      String conflictingFiles = conflictResult.stdout().strip();

      // Abort rebase to return to clean state
      ProcessRunner.run("git", "-C", sourceBranch, "rebase", "--abort");

      if (!conflictingFiles.isEmpty())
        return buildConflictJson(resolvedTarget, backup, conflictingFiles);
      return buildErrorJson("Rebase failed: " + rebaseResult.stdout().strip(), resolvedTarget, backup);
    }

    // Step 4: Rebase succeeded — verify backup and HEAD have same tree state
    ProcessRunner.Result treeResult = ProcessRunner.run(
      "git", "-C", sourceBranch, "diff", "--quiet", backup, "HEAD");
    if (treeResult.exitCode() != 0)
    {
      // Tree state differs — get stat for error message
      ProcessRunner.Result diffStatResult = ProcessRunner.run(
        "git", "-C", sourceBranch, "diff", backup, "--stat");
      String diffStat = "";
      if (diffStatResult.exitCode() == 0)
        diffStat = diffStatResult.stdout().strip();
      return buildContentChangedErrorJson(resolvedTarget, backup, diffStat);
    }

    // Step 5: Count commits rebased
    String countStr = runGitCommandSingleLineInDirectory(sourceBranch, "rev-list", "--count",
      resolvedTarget + "..HEAD");
    int commitsRebased = Integer.parseInt(countStr);

    // Step 6: Delete backup
    ProcessRunner.run("git", "-C", sourceBranch, "branch", "-D", backup);

    return buildOkJson(resolvedTarget, commitsRebased);
  }

  /**
   * Builds an OK JSON response.
   *
   * @param target          the target commit hash
   * @param commitsRebased  the number of commits rebased
   * @return JSON string with OK status
   * @throws IOException if JSON serialization fails
   */
  private String buildOkJson(String target, int commitsRebased) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "OK");
    json.put("target_branch", target);
    json.put("commits_rebased", commitsRebased);
    json.put("backup_cleaned", true);
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Builds a CONFLICT JSON response.
   *
   * @param target          the target commit hash
   * @param backupBranch    the backup branch name
   * @param conflictingFiles newline-separated list of conflicting file paths
   * @return JSON string with CONFLICT status
   * @throws IOException if JSON serialization fails
   */
  private String buildConflictJson(String target, String backupBranch,
    String conflictingFiles) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "CONFLICT");
    json.put("target_branch", target);
    json.put("backup_branch", backupBranch);
    ArrayNode filesArray = json.putArray("conflicting_files");
    for (String file : conflictingFiles.split("\n"))
    {
      String trimmed = file.strip();
      if (!trimmed.isEmpty())
        filesArray.add(trimmed);
    }
    json.put("message", "Rebase conflict - backup preserved at " + backupBranch);
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Builds an ERROR JSON response.
   *
   * @param message      the error message
   * @param target       the pinned target commit hash, or null if resolution failed before pinning
   * @param backupBranch the backup branch name, or null if backup was not created
   * @return JSON string with ERROR status
   * @throws IOException if JSON serialization fails
   */
  private String buildErrorJson(String message, String target, String backupBranch) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "ERROR");
    if (target != null)
      json.put("target_branch", target);
    if (backupBranch != null)
      json.put("backup_branch", backupBranch);
    else
      json.putNull("backup_branch");
    json.put("message", message);
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Builds an ERROR JSON response for when content changed unexpectedly during rebase.
   *
   * @param target       the pinned target commit hash
   * @param backupBranch the backup branch name
   * @param diffStat     the diff stat output
   * @return JSON string with ERROR status
   * @throws IOException if JSON serialization fails
   */
  private String buildContentChangedErrorJson(String target, String backupBranch,
    String diffStat) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "ERROR");
    json.put("target_branch", target);
    json.put("backup_branch", backupBranch);
    json.put("message", "Content changed during rebase - backup preserved for investigation");
    json.put("diff_stat", diffStat);
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage: git-rebase-safe SOURCE_BRANCH [TARGET_BRANCH]
   * <p>
   * If TARGET_BRANCH is not provided, reads from the cat-branch-point file in the git directory.
   * Outputs JSON to stdout on success (OK).
   * Outputs JSON to stderr on failure (CONFLICT, ERROR).
   * Exit code 0 for success, 1 for errors.
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 1)
    {
      System.err.println("""
        {
          "status": "ERROR",
          "message": "Usage: git-rebase-safe <SOURCE_BRANCH> [TARGET_BRANCH]",
          "backup_branch": null
        }""");
      System.exit(1);
    }

    String sourceBranch = args[0];
    String targetBranch = "";
    if (args.length > 1)
      targetBranch = args[1];

    try (JvmScope scope = new MainJvmScope())
    {
      GitRebaseSafe cmd = new GitRebaseSafe(scope, sourceBranch);
      try
      {
        String result = cmd.execute(targetBranch);
        // Determine output stream based on status field
        String status;
        try
        {
          ObjectNode resultJson = (ObjectNode) scope.getJsonMapper().readTree(result);
          status = resultJson.get("status").asString();
        }
        catch (JacksonException _)
        {
          // If JSON parsing fails, treat as error
          status = "ERROR";
        }
        if (status.equals("ERROR") || status.equals("CONFLICT"))
        {
          System.err.println(result);
          System.exit(1);
        }
        else
        {
          System.out.println(result);
        }
      }
      catch (IOException e)
      {
        System.err.println("""
          {
            "status": "ERROR",
            "message": "%s",
            "backup_branch": null
          }""".formatted(e.getMessage().replace("\"", "\\\"")));
        System.exit(1);
      }
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(GitRebaseSafe.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}
