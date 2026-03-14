/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Safe git rebase with backup creation, conflict detection, and content verification.
 * <p>
 * Requires an explicit target branch or commit hash, creates a timestamped backup branch, attempts
 * the rebase, verifies no content changes, counts commits rebased, and cleans up the backup.
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
   *   <li>Validate that target branch is provided</li>
   *   <li>Detect the fork point (always returns a non-null value)</li>
   *   <li>Create timestamped backup branch</li>
   *   <li>Attempt rebase with {@code --onto}, verify tree state, and clean orphaned files</li>
   *   <li>Count commits rebased</li>
   *   <li>Clean up backup</li>
   *   <li>Return OK JSON</li>
   * </ol>
   * <p>
   * Success JSON is written to stdout; error/conflict JSON to stdout.
   *
   * @param targetBranch the branch or commit hash to rebase onto (must not be empty)
   * @return JSON string with operation result
   * @throws NullPointerException if {@code targetBranch} is null
   * @throws IOException          if the operation fails
   */
  public String execute(String targetBranch) throws IOException
  {
    requireThat(targetBranch, "targetBranch").isNotNull();

    // Step 1: Validate target branch
    String resolvedTarget = targetBranch;
    if (resolvedTarget.isEmpty())
    {
      return buildBlockResponse(
        "Usage: git-rebase <SOURCE_DIR> <TARGET_BRANCH>\n" +
          "TARGET_BRANCH is required. Provide the branch or commit hash to rebase onto.",
        null, null);
    }

    // Step 2: Detect fork point (always returns a non-null value)
    String forkPoint = detectForkPoint(resolvedTarget);

    // Step 2.5: Validate path consistency before creating backup
    String mergeBaseForValidation = detectMergeBase(resolvedTarget);
    if (mergeBaseForValidation != null)
    {
      String validationError = validatePathConsistency(resolvedTarget, mergeBaseForValidation);
      if (validationError != null)
        return validationError;
    }

    // Step 3: Create timestamped backup branch
    String backup = "backup-before-rebase-" + LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMATTER);
    ProcessRunner.Result branchResult = runGit("branch", backup);
    if (branchResult.exitCode() != 0)
      return buildBlockResponse("Failed to create backup branch: " + branchResult.stdout().strip(), null, null);

    // Verify backup was created (fail-fast)
    ProcessRunner.Result verifyResult = runGit("show-ref", "--verify", "--quiet",
      "refs/heads/" + backup);
    if (verifyResult.exitCode() != 0)
    {
      return buildBlockResponse(
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
      return new RebaseOutcome(buildContentChangedBlockResponse(resolvedTarget, backup, diffStat), List.of());
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
   * Detects the merge base between the current branch and the given target.
   * <p>
   * Unlike {@link #detectForkPoint(String)}, this method does not use reflog and simply returns the common
   * ancestor commit. Returns {@code null} if no common ancestor exists (i.e., unrelated histories).
   *
   * @param targetBranch the target branch name or commit hash
   * @return the merge-base commit hash, or {@code null} if not found
   * @throws IOException if the git command fails unexpectedly
   */
  private String detectMergeBase(String targetBranch) throws IOException
  {
    ProcessRunner.Result result = runGit("merge-base", targetBranch, "HEAD");
    if (result.exitCode() != 0)
      return null;
    return result.stdout().strip();
  }

  /**
   * Validates that the current branch is consistent with path renames introduced on the target branch
   * since the merge base.
   * <p>
   * Finds files renamed on the target branch (between merge base and target tip) and checks whether:
   * <ul>
   *   <li>The current branch still tracks files at the old (pre-rename) path</li>
   *   <li>The current branch has files whose content references the old path string</li>
   * </ul>
   * Renames that the current branch has also performed (same old path renamed to a new path) are skipped,
   * since the branch has already handled those renames. This prevents false positives when the feature
   * branch's purpose is to perform the same rename as the target.
   * <p>
   * If any inconsistency is found, returns an ERROR JSON string. Otherwise returns {@code null}.
   *
   * @param targetBranch the target branch to rebase onto
   * @param mergeBase    the merge-base commit hash between the current branch and target
   * @return an ERROR JSON string if inconsistencies are found, or {@code null} if everything is consistent
   * @throws IOException if git commands fail unexpectedly
   */
  private String validatePathConsistency(String targetBranch, String mergeBase) throws IOException
  {
    // Find renamed files on the target branch since the merge base
    ProcessRunner.Result renameResult = runGit("diff", "--name-status", "--diff-filter=R",
      "--find-renames=50%", mergeBase, targetBranch);
    if (renameResult.exitCode() != 0 || renameResult.stdout().isBlank())
      return null;

    // Find old path prefixes that the current branch has ALSO renamed since the merge base.
    // When the current branch performs the same rename as the target, it is not a conflict.
    Set<String> currentBranchRenamedPrefixes = detectCurrentBranchRenamedPrefixes(mergeBase);

    Set<String> trackedConflicts = new LinkedHashSet<>();
    // Map from file path to list of old paths it references
    Map<String, List<String>> contentConflicts = new LinkedHashMap<>();

    for (String line : renameResult.stdout().split("\n"))
    {
      String trimmed = line.strip();
      if (trimmed.isEmpty())
        continue;
      // Format: R<score>\t<oldPath>\t<newPath>
      String[] parts = trimmed.split("\t");
      if (parts.length < 3)
        continue;
      String oldPath = parts[1].strip();
      String newPath = parts[2].strip();

      // Compute the old directory prefix that changed (for content reference detection).
      // Example: oldPath=".claude/cat/config.json", newPath=".cat/config.json"
      //          commonSuffix="/config.json" → oldPrefix=".claude/cat"
      String oldPrefix = computeChangedPrefix(oldPath, newPath);

      // Skip this rename if the current branch has also renamed the same old prefix.
      // The current branch has already handled this rename — it is not a conflict.
      if (currentBranchRenamedPrefixes.contains(oldPrefix))
        continue;

      // Check if current branch still tracks the old path
      ProcessRunner.Result lsResult = runGit("ls-files", "--", oldPath);
      if (lsResult.exitCode() == 0 && !lsResult.stdout().isBlank())
        trackedConflicts.add(oldPath);

      // Check if any tracked file in the current branch contains the old prefix as text
      ProcessRunner.Result grepResult = runGit("grep", "-l", "--", oldPrefix);
      // git grep exits 1 when no matches (not an error); only exit code > 1 is a real error
      if (grepResult.exitCode() > 1)
        throw new IOException("git grep failed: " + grepResult.stdout().strip());
      if (grepResult.exitCode() == 0 && !grepResult.stdout().isBlank())
      {
        for (String file : grepResult.stdout().split("\n"))
        {
          String fileTrimmed = file.strip();
          if (!fileTrimmed.isEmpty())
            contentConflicts.computeIfAbsent(fileTrimmed, _ -> new ArrayList<>()).add(oldPrefix);
        }
      }
    }

    if (trackedConflicts.isEmpty() && contentConflicts.isEmpty())
      return null;

    String header = "Pre-rebase path consistency check failed. " +
      "The target branch has renamed paths that conflict with the current branch.\n" +
      "Update the current branch to use the new paths before rebasing.";

    String trackedSection = buildTrackedSection(trackedConflicts);
    String contentSection = buildContentSection(contentConflicts);

    String message = header + trackedSection + contentSection;
    return buildBlockResponse(message, null, null);
  }

  /**
   * Detects the set of old path prefixes that the current branch has renamed since the merge base.
   * <p>
   * Runs {@code git diff --name-status --diff-filter=R --find-renames=50% <mergeBase> HEAD} and collects
   * the old path prefix (computed via {@link #computeChangedPrefix(String, String)}) for each rename. This
   * set is used to skip target-branch renames that the current branch has already handled.
   *
   * @param mergeBase the merge-base commit hash between the current branch and target
   * @return set of old path prefixes renamed by the current branch since the merge base
   * @throws IOException if the git command fails unexpectedly
   */
  private Set<String> detectCurrentBranchRenamedPrefixes(String mergeBase) throws IOException
  {
    ProcessRunner.Result result = runGit("diff", "--name-status", "--diff-filter=R",
      "--find-renames=50%", mergeBase, "HEAD");
    Set<String> prefixes = new HashSet<>();
    if (result.exitCode() != 0 || result.stdout().isBlank())
      return prefixes;
    for (String line : result.stdout().split("\n"))
    {
      String trimmed = line.strip();
      if (trimmed.isEmpty())
        continue;
      // Format: R<score>\t<oldPath>\t<newPath>
      String[] parts = trimmed.split("\t");
      if (parts.length < 3)
        continue;
      String oldPath = parts[1].strip();
      String newPath = parts[2].strip();
      prefixes.add(computeChangedPrefix(oldPath, newPath));
    }
    return prefixes;
  }

  /**
   * Builds the tracked-path section of the path consistency error message.
   *
   * @param trackedConflicts set of old paths still tracked in the current branch
   * @return a multi-line string section, or empty string if no conflicts
   */
  private static String buildTrackedSection(Set<String> trackedConflicts)
  {
    if (trackedConflicts.isEmpty())
      return "";
    List<String> lines = new ArrayList<>();
    lines.add("\n\nTracked files at renamed (old) paths:");
    for (String path : trackedConflicts)
      lines.add("  " + path);
    return String.join("\n", lines);
  }

  /**
   * Builds the content-reference section of the path consistency error message.
   *
   * @param contentConflicts map from file path to list of old path strings it references
   * @return a multi-line string section, or empty string if no conflicts
   */
  private static String buildContentSection(Map<String, List<String>> contentConflicts)
  {
    if (contentConflicts.isEmpty())
      return "";
    List<String> lines = new ArrayList<>();
    lines.add("\n\nFiles with content references to renamed (old) paths:");
    for (Map.Entry<String, List<String>> entry : contentConflicts.entrySet())
      lines.add("  " + entry.getKey() + " references: " + String.join(", ", entry.getValue()));
    return String.join("\n", lines);
  }

  /**
   * Computes the old path prefix that changed in a rename.
   * <p>
   * Finds the longest common suffix shared by {@code oldPath} and {@code newPath} (aligned at path
   * component boundaries), then returns the old path with that suffix removed. This gives the portion of
   * the old path that no longer exists under the same name.
   * <p>
   * Examples:
   * <ul>
   *   <li>{@code oldPath=".claude/cat/config.json"}, {@code newPath=".cat/config.json"}
   *       → common suffix {@code "/config.json"} → returns {@code ".claude/cat"}</li>
   *   <li>{@code oldPath="src/old/Foo.java"}, {@code newPath="src/new/Foo.java"}
   *       → common suffix {@code "/Foo.java"} → returns {@code "src/old"}</li>
   *   <li>{@code oldPath="old.txt"}, {@code newPath="new.txt"} → no common suffix → returns
   *       {@code "old.txt"}</li>
   * </ul>
   *
   * @param oldPath the old file path before the rename
   * @param newPath the new file path after the rename
   * @return the old path prefix up to (but not including) the common suffix
   */
  private static String computeChangedPrefix(String oldPath, String newPath)
  {
    // Walk backwards through path components to find the longest common suffix
    String[] oldParts = oldPath.split("/");
    String[] newParts = newPath.split("/");
    int commonCount = 0;
    int oldIdx = oldParts.length - 1;
    int newIdx = newParts.length - 1;
    while (oldIdx >= 0 && newIdx >= 0 && oldParts[oldIdx].equals(newParts[newIdx]))
    {
      commonCount += 1;
      oldIdx -= 1;
      newIdx -= 1;
    }
    if (commonCount == 0 || oldIdx < 0)
      return oldPath;
    // Build the prefix from oldParts[0..oldIdx] inclusive
    List<String> prefixParts = new ArrayList<>();
    for (int i = 0; i <= oldIdx; i += 1)
      prefixParts.add(oldParts[i]);
    return String.join("/", prefixParts);
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
    return buildBlockResponse("Rebase failed: " + rebaseResult.stdout().strip(), resolvedTarget, backup);
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
   * Builds a block response for an error condition.
   *
   * @param message      the error message
   * @param target       the pinned target commit hash, or null if resolution failed before pinning
   * @param backupBranch the backup branch name, or null if backup was not created
   * @return JSON string with block decision
   */
  private String buildBlockResponse(String message, String target, String backupBranch)
  {
    HookOutput hookOutput = new HookOutput(scope);
    StringBuilder reason = new StringBuilder(message);
    if (target != null)
    {
      reason.append("\ntarget_branch: ").append(target);
    }
    if (backupBranch != null)
      reason.append("\nbackup_branch: ").append(backupBranch);
    return hookOutput.block(reason.toString());
  }

  /**
   * Builds a block response for when content changed unexpectedly during rebase.
   *
   * @param target       the pinned target commit hash
   * @param backupBranch the backup branch name
   * @param diffStat     the diff stat output
   * @return JSON string with block decision
   */
  private String buildContentChangedBlockResponse(String target, String backupBranch, String diffStat)
  {
    HookOutput hookOutput = new HookOutput(scope);
    String reason = "Content changed during rebase - backup preserved for investigation" +
      "\ntarget_branch: " + target +
      "\nbackup_branch: " + backupBranch +
      "\ndiff_stat:\n" + diffStat;
    return hookOutput.block(reason);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage: git-rebase SOURCE_DIR TARGET_BRANCH
   * <p>
   * Outputs JSON to stdout on all outcomes (OK, CONFLICT, ERROR).
   * Always exits with code 0 so Claude Code parses stdout as JSON.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      run(scope, args, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(GitRebase.class);
      log.error("Unexpected error", e);
      try (MainJvmScope errorScope = new MainJvmScope())
      {
        System.out.println(new HookOutput(errorScope).block(
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the git-rebase logic with a caller-provided output stream.
   * <p>
   * Separated from {@link #main(String[])} to allow unit testing without JVM exit.
   * IOException is converted to a block response on {@code out}.
   *
   * @param scope the JVM scope
   * @param args  command-line arguments
   * @param out   the output stream to write JSON to
   * @throws NullPointerException if {@code scope}, {@code args}, or {@code out} are null
   */
  public static void run(JvmScope scope, String[] args, PrintStream out)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    HookOutput hookOutput = new HookOutput(scope);
    if (args.length < 1)
    {
      out.println(hookOutput.block("Usage: git-rebase <SOURCE_DIR> <TARGET_BRANCH>"));
      return;
    }

    Path workingDirectory = Path.of(args[0]);
    String targetBranch = "";
    if (args.length > 1)
      targetBranch = args[1];

    GitRebase cmd = new GitRebase(scope, workingDirectory);
    try
    {
      String result = cmd.execute(targetBranch);
      out.println(result);
    }
    catch (IOException e)
    {
      out.println(hookOutput.block(Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
