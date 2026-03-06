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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Safe git rebase with backup creation, conflict detection, and content verification.
 * <p>
 * Reads the target from {@code cat-branch-point} (a 40-character commit hash) when no target is provided,
 * creates a timestamped backup branch, attempts the rebase, verifies no content changes, counts
 * commits rebased, and cleans up the backup.
 * <p>
 * Uses {@code git merge-base --fork-point} to detect when the upstream has been retroactively
 * rewritten. Always uses {@code git rebase --onto} with the detected fork point:
 * <ul>
 *   <li>When the target branch has <b>added</b> files: after rebase, those files appear in the working tree
 *       because feature commits replay on top of the target, inheriting its content. This is normal git
 *       behavior and requires no special handling.</li>
 *   <li>When the target branch has <b>removed</b> files (retroactive rewrite): a plain
 *       {@code git rebase target} would consider the branch "up to date" and leave orphaned files tracked.
 *       Using {@code --onto} with fork-point detection, git properly replays only the feature commits on the
 *       new base. Files that existed in the old upstream tip but were removed by the rewrite become untracked
 *       orphans, which are detected and deleted.</li>
 *   <li>When fork-point equals merge-base (no rewrite): {@code git rebase --onto target merge-base} is
 *       equivalent to {@code git rebase target}, so using {@code --onto} unconditionally is safe.</li>
 * </ul>
 */
public final class GitRebase
{
  private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private final JvmScope scope;
  private final Path workingDirectory;

  /**
   * Creates a new GitRebase instance.
   *
   * @param scope            the JVM scope providing JSON mapper
   * @param workingDirectory the working directory or worktree path for git commands
   * @throws NullPointerException if any argument is null
   */
  public GitRebase(JvmScope scope, Path workingDirectory)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    this.scope = scope;
    this.workingDirectory = workingDirectory;
  }

  /**
   * Executes the safe rebase operation.
   * <p>
   * The process:
   * <ol>
   *   <li>If target not provided, read commit hash from cat-branch-point file</li>
   *   <li>Detect the fork point (always returns a non-null value)</li>
   *   <li>Create timestamped backup branch</li>
   *   <li>Attempt rebase with {@code --onto}, verify tree state, and clean orphaned files</li>
   *   <li>Count commits rebased</li>
   *   <li>Clean up backup</li>
   *   <li>Return OK JSON</li>
   * </ol>
   * <p>
   * Success JSON is written to stdout; error/conflict JSON to stderr.
   *
   * @param targetBranch the branch or commit hash to rebase onto, or empty string to read from
   *                     cat-branch-point file
   * @return JSON string with operation result
   * @throws NullPointerException if {@code targetBranch} is null
   * @throws IOException          if the operation fails
   */
  public String execute(String targetBranch) throws IOException
  {
    requireThat(targetBranch, "targetBranch").isNotNull();

    // Step 1: Resolve target branch
    String resolvedTarget = targetBranch;
    if (resolvedTarget.isEmpty())
    {
      ProcessRunner.Result gitDirResult = runGit("rev-parse", "--git-dir");
      if (gitDirResult.exitCode() != 0)
        return buildErrorJson("Failed to resolve git dir: " + gitDirResult.stdout().strip(), null, null);

      Path gitDir = Path.of(gitDirResult.stdout().strip());
      if (!gitDir.isAbsolute())
        gitDir = workingDirectory.resolve(gitDir);

      Path catBranchPointFile = gitDir.resolve(CatMetadata.BRANCH_POINT_FILE);
      if (!Files.exists(catBranchPointFile))
      {
        return buildErrorJson(
          CatMetadata.BRANCH_POINT_FILE + " file not found: " + catBranchPointFile +
            ". Recreate worktree with /cat:work.", null, null);
      }
      resolvedTarget = Files.readString(catBranchPointFile).strip();
    }

    // Step 2: Detect fork point (always returns a non-null value)
    String forkPoint = detectForkPoint(resolvedTarget);

    // Step 3: Create timestamped backup branch
    String backup = "backup-before-rebase-" + LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMATTER);
    ProcessRunner.Result branchResult = runGit("branch", backup);
    if (branchResult.exitCode() != 0)
      return buildErrorJson("Failed to create backup branch: " + branchResult.stdout().strip(), null, null);

    // Verify backup was created (fail-fast)
    ProcessRunner.Result verifyResult = runGit("show-ref", "--verify", "--quiet",
      "refs/heads/" + backup);
    if (verifyResult.exitCode() != 0)
    {
      return buildErrorJson(
        "Backup branch '" + backup + "' was not created. Do NOT proceed with rebase without backup.",
        null, null);
    }

    // Step 4: Capture pre-rebase untracked files, attempt rebase, verify tree state, clean orphans
    Set<String> untrackedBefore = getUntrackedFiles();
    RebaseOutcome outcome = performRebase(resolvedTarget, forkPoint, backup, untrackedBefore);
    if (outcome.error() != null)
      return outcome.error();

    // Step 5: Count commits rebased
    ProcessRunner.Result countResult = runGit("rev-list", "--count", resolvedTarget + "..HEAD");
    int commitsRebased = Integer.parseInt(countResult.stdout().strip());

    // Step 6: Delete backup
    runGit("branch", "-D", backup);

    return buildOkJson(resolvedTarget, commitsRebased, outcome.deletedOrphans());
  }

  /**
   * Performs the rebase and post-rebase verification/cleanup.
   * <p>
   * Attempts the rebase, verifies tree state, and deletes orphaned files. The pre-rebase untracked
   * file snapshot is passed in from the caller to ensure it is captured before the rebase modifies
   * the working tree.
   *
   * @param resolvedTarget  the target branch or commit hash
   * @param forkPoint       the fork-point commit hash
   * @param backup          the backup branch name
   * @param untrackedBefore untracked file paths captured before the rebase
   * @return the rebase outcome containing either an error JSON or the list of deleted orphans
   * @throws IOException if git commands fail unexpectedly
   */
  private RebaseOutcome performRebase(String resolvedTarget, String forkPoint,
    String backup, Set<String> untrackedBefore) throws IOException
  {
    String rebaseError = attemptRebase(resolvedTarget, forkPoint, backup);
    if (rebaseError != null)
      return new RebaseOutcome(rebaseError, List.of());

    // Verify backup and HEAD have same tree state.
    // Only check for unexpected modifications (--diff-filter=M): additions from upstream and
    // deletions from upstream rewrites are both expected and should not trigger an error.
    ProcessRunner.Result treeResult = runGit("diff", "--quiet", "--diff-filter=M", backup, "HEAD");
    if (treeResult.exitCode() != 0)
    {
      ProcessRunner.Result diffStatResult = runGit("diff", "--diff-filter=M", backup, "--stat");
      String diffStat;
      if (diffStatResult.exitCode() == 0)
        diffStat = diffStatResult.stdout().strip();
      else
        diffStat = "";
      return new RebaseOutcome(buildContentChangedErrorJson(resolvedTarget, backup, diffStat), List.of());
    }

    Set<String> untrackedAfter = getUntrackedFiles();
    List<String> deletedOrphans = deleteOrphanedFiles(untrackedBefore, untrackedAfter);
    return new RebaseOutcome(null, deletedOrphans);
  }

  /**
   * Result of a rebase attempt including post-rebase verification and orphan cleanup.
   *
   * @param error          JSON error string on failure, or {@code null} on success
   * @param deletedOrphans list of orphaned file paths that were deleted (empty on failure)
   */
  private record RebaseOutcome(String error, List<String> deletedOrphans)
  {
  }

  /**
   * Runs a git command in the working directory.
   *
   * @param args the git command arguments (without the leading "git")
   * @return the process result
   */
  private ProcessRunner.Result runGit(String... args)
  {
    String[] command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    return ProcessRunner.run(workingDirectory, command);
  }

  /**
   * Detects the fork point between the current branch and the given target.
   * <p>
   * First computes the merge base (common ancestor). Then attempts {@code git merge-base --fork-point}
   * which uses the reflog to find the historical fork point. If the fork point differs from the merge
   * base, the upstream was retroactively rewritten and the fork point is returned so {@code --onto}
   * can correctly replay only the feature commits.
   * <p>
   * When fork-point detection fails (e.g., target is a commit hash without reflog, or reflog was pruned),
   * the merge base is returned as the fork point. This is safe because
   * {@code git rebase --onto target merge-base} is equivalent to {@code git rebase target}.
   *
   * @param resolvedTarget the target branch name or commit hash
   * @return the fork-point commit hash (historical divergence point), or the merge-base if fork-point
   *         detection is unavailable
   * @throws IOException if {@code git merge-base} fails unexpectedly
   */
  private String detectForkPoint(String resolvedTarget) throws IOException
  {
    // Always compute the merge base first — this is the minimum required information.
    ProcessRunner.Result mergeBaseResult = runGit("merge-base", resolvedTarget, "HEAD");
    if (mergeBaseResult.exitCode() != 0)
    {
      throw new IOException("git merge-base failed for target '" + resolvedTarget +
        "': " + mergeBaseResult.stdout().strip());
    }
    String mergeBase = mergeBaseResult.stdout().strip();

    // Try fork-point detection via reflog. This fails when the target is a commit hash
    // (no reflog) or when the reflog has been pruned — both are expected conditions.
    ProcessRunner.Result forkPointResult = runGit("merge-base", "--fork-point", resolvedTarget, "HEAD");
    if (forkPointResult.exitCode() != 0)
      return mergeBase;

    return forkPointResult.stdout().strip();
  }

  /**
   * Attempts the git rebase onto the given target using {@code --onto}.
   * <p>
   * Always uses {@code git rebase --onto <target> <forkPoint>} to handle both normal and retroactive
   * rewrite scenarios uniformly. When {@code forkPoint} equals the merge base (no rewrite),
   * {@code git rebase --onto target merge-base} is equivalent to {@code git rebase target}.
   * When {@code forkPoint} differs (retroactive upstream rewrite), {@code --onto} correctly replays
   * only the feature commits, preventing orphaned files from remaining tracked.
   * <p>
   * On failure, aborts the rebase and returns a non-null JSON error/conflict string.
   * On success, returns {@code null}.
   *
   * @param resolvedTarget the target branch or commit hash to rebase onto
   * @param forkPoint      the fork-point commit hash (never null)
   * @param backup         the backup branch name created before the rebase
   * @return {@code null} on success, or a JSON error/conflict string on failure
   * @throws IOException if git commands fail unexpectedly
   */
  private String attemptRebase(String resolvedTarget, String forkPoint, String backup) throws IOException
  {
    // Always use --onto: when forkPoint equals merge-base, this is equivalent to plain rebase.
    // When forkPoint differs (retroactive upstream rewrite), --onto correctly replays only
    // feature commits, preventing orphaned files.
    ProcessRunner.Result rebaseResult = runGit("rebase", "--onto", resolvedTarget, forkPoint);
    if (rebaseResult.exitCode() == 0)
      return null;

    // Rebase failed — check if it's a conflict
    ProcessRunner.Result conflictResult = runGit("diff", "--name-only", "--diff-filter=U");
    String conflictingFiles = conflictResult.stdout().strip();

    // Abort rebase to return to clean state
    runGit("rebase", "--abort");

    if (!conflictingFiles.isEmpty())
      return buildConflictJson(resolvedTarget, backup, conflictingFiles);
    return buildErrorJson("Rebase failed: " + rebaseResult.stdout().strip(), resolvedTarget, backup);
  }

  /**
   * Returns the set of untracked file paths in the working directory.
   * <p>
   * Parses {@code git status --porcelain} output and collects paths from lines starting with {@code ??}.
   *
   * @return set of untracked file paths relative to the repository root
   * @throws IOException if the git command fails
   */
  private Set<String> getUntrackedFiles() throws IOException
  {
    ProcessRunner.Result result = runGit("status", "--porcelain");
    if (result.exitCode() != 0)
      throw new IOException("Failed to run git status --porcelain: " + result.stdout().strip());

    Set<String> untracked = new HashSet<>();
    for (String line : result.stdout().split("\n"))
    {
      if (line.startsWith("?? "))
        untracked.add(line.substring(3).strip());
    }
    return untracked;
  }

  /**
   * Deletes files that appeared as untracked after a rebase but were not untracked before it.
   * <p>
   * These are rebase orphans: files that existed in the old upstream tip but were removed by a rewrite
   * of the upstream branch. Only newly-appeared untracked files are deleted; pre-existing untracked
   * files are preserved.
   *
   * @param untrackedBefore untracked file paths before the rebase
   * @param untrackedAfter  untracked file paths after the rebase
   * @return list of deleted orphan paths (relative to repository root), in the order deleted
   * @throws IOException if deleting a file fails
   */
  private List<String> deleteOrphanedFiles(Set<String> untrackedBefore,
    Set<String> untrackedAfter) throws IOException
  {
    List<String> deleted = new ArrayList<>();
    for (String relativePath : untrackedAfter)
    {
      if (!untrackedBefore.contains(relativePath))
      {
        Path orphan = workingDirectory.resolve(relativePath);
        if (Files.isDirectory(orphan))
          deleteDirectoryRecursively(orphan);
        else if (Files.exists(orphan))
          Files.delete(orphan);
        deleted.add(relativePath);
      }
    }
    return deleted;
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param directory the directory to delete
   * @throws IOException if deletion fails
   */
  private static void deleteDirectoryRecursively(Path directory) throws IOException
  {
    Files.walkFileTree(directory, new java.nio.file.SimpleFileVisitor<>()
    {
      @Override
      public java.nio.file.FileVisitResult visitFile(Path file,
        java.nio.file.attribute.BasicFileAttributes attrs) throws IOException
      {
        Files.delete(file);
        return java.nio.file.FileVisitResult.CONTINUE;
      }

      @Override
      public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
      {
        if (exc != null)
          throw exc;
        Files.delete(dir);
        return java.nio.file.FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Builds an OK JSON response.
   *
   * @param target          the target commit hash
   * @param commitsRebased  the number of commits rebased
   * @param deletedOrphans  list of orphaned file paths that were deleted
   * @return JSON string with OK status
   * @throws IOException if JSON serialization fails
   */
  private String buildOkJson(String target, int commitsRebased, List<String> deletedOrphans) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "OK");
    json.put("target_branch", target);
    json.put("commits_rebased", commitsRebased);
    json.put("backup_cleaned", true);
    ArrayNode orphansArray = json.putArray("deleted_orphans");
    for (String path : deletedOrphans)
      orphansArray.add(path);
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
   * Usage: git-rebase SOURCE_DIR [TARGET_BRANCH]
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
          "message": "Usage: git-rebase <SOURCE_DIR> [TARGET_BRANCH]",
          "backup_branch": null
        }""");
      System.exit(1);
    }

    Path workingDirectory = Path.of(args[0]);
    String targetBranch = "";
    if (args.length > 1)
      targetBranch = args[1];

    try (JvmScope scope = new MainJvmScope())
    {
      GitRebase cmd = new GitRebase(scope, workingDirectory);
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
      Logger log = LoggerFactory.getLogger(GitRebase.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}
