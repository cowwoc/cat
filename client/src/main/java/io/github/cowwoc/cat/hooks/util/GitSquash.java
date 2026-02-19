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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic git squash via commit-tree with race condition prevention.
 * <p>
 * Implements the commit-tree approach: pin base branch reference, rebase onto base,
 * detect concurrent modifications, create backup, squash via commit-tree, and verify.
 * <p>
 * This replaces the prohibited soft-reset approach. The commit-tree method creates
 * commits from committed tree objects, ignoring working directory state entirely.
 */
public final class GitSquash
{
  private static final Pattern COMMIT_MESSAGE_PATTERN =
    Pattern.compile("^(feature|bugfix|refactor|test|performance|config|planning|docs): \\S");
  private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private final JvmScope scope;
  private final String directory;

  /**
   * Creates a new GitSquash instance.
   *
   * @param scope     the JVM scope providing JSON mapper
   * @param directory the working directory for git commands
   * @throws NullPointerException     if {@code scope} is null
   * @throws IllegalArgumentException if {@code directory} is blank
   */
  public GitSquash(JvmScope scope, String directory)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(directory, "directory").isNotBlank();
    this.scope = scope;
    this.directory = directory;
  }

  /**
   * Executes the squash operation using the commit-tree approach.
   * <p>
   * The process:
   * <ol>
   *   <li>Validate commit message format</li>
   *   <li>Pin base branch reference to prevent race conditions</li>
   *   <li>Compute merge-base for concurrent modification detection</li>
   *   <li>Rebase onto pinned base (handle failures gracefully)</li>
   *   <li>Detect concurrent modifications (files modified on both branches)</li>
   *   <li>Create timestamped backup branch</li>
   *   <li>Verify clean working directory</li>
   *   <li>Create squashed commit via commit-tree</li>
   *   <li>Move branch to new commit</li>
   *   <li>Verify diff with backup is empty</li>
   *   <li>Verify exactly 1 commit from base</li>
   * </ol>
   *
   * @param baseBranch    the base branch to squash onto
   * @param commitMessage the commit message for the squashed commit
   * @return JSON string with operation result
   * @throws IllegalArgumentException if {@code baseBranch} is blank, or {@code commitMessage} is blank
   *                                  or has an invalid format
   * @throws IOException              if the operation fails
   */
  public String execute(String baseBranch, String commitMessage) throws IOException
  {
    requireThat(baseBranch, "baseBranch").isNotBlank();
    requireThat(commitMessage, "commitMessage").isNotBlank();

    // Step 1: Validate commit message format
    if (!COMMIT_MESSAGE_PATTERN.matcher(commitMessage).find())
    {
      throw new IllegalArgumentException("""
        Invalid commit message format.

        Commit message must start with a valid type prefix:
          feature:     New capability
          bugfix:      Bug fix
          refactor:    Code restructure
          test:        Test addition/modification
          performance: Optimization
          config:      Configuration change
          planning:    Issue tracking
          docs:        Documentation

        Received: %s

        Example: feature: add user authentication""".formatted(commitMessage));
    }

    // Step 2: Pin base branch reference BEFORE rebase to prevent race conditions
    String base = runGitCommandSingleLineInDirectory(directory, "rev-parse", baseBranch);

    // Step 3: Save pre-rebase state for concurrent modification detection
    String mergeBase = runGitCommandSingleLineInDirectory(directory, "merge-base", "HEAD", base);

    // Step 4: Rebase onto pinned base
    ProcessRunner.Result rebaseResult = ProcessRunner.run(
      "git", "-C", directory, "rebase", base);
    if (rebaseResult.exitCode() != 0)
      return handleRebaseFailure(rebaseResult);

    // Step 5: Detect concurrent modifications
    Set<String> concurrentMods = detectConcurrentModifications(base, mergeBase);

    // Step 6: Create timestamped backup branch
    String backupBranch = "backup-before-squash-" + LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMATTER);
    runGitCommandInDirectory(directory, "branch", backupBranch);

    // Verify backup was created
    ProcessRunner.Result verifyBackup = ProcessRunner.run(
      "git", "-C", directory, "show-ref", "--verify", "--quiet",
      "refs/heads/" + backupBranch);
    if (verifyBackup.exitCode() != 0)
      throw new IOException("Backup branch '" + backupBranch + "' was not created");

    // Step 7: Verify clean working directory
    String status = runGitCommandInDirectory(directory, "status", "--porcelain");
    if (!status.isEmpty())
      throw new IOException("Working directory is not clean");

    // Step 8: Create squashed commit using commit-tree
    String tree = runGitCommandSingleLineInDirectory(directory, "rev-parse", "HEAD^{tree}");
    String newCommit = runGitCommandSingleLineInDirectory(directory,
      "commit-tree", tree, "-p", base, "-m", commitMessage);

    // Step 9: Move branch to new squashed commit
    runGitCommandInDirectory(directory, "reset", "--hard", newCommit);

    // Step 10: Verify diff with backup is empty
    String diffOutput = runGitCommandInDirectory(directory, "diff", backupBranch);
    if (!diffOutput.isEmpty())
    {
      String diffStat = runGitCommandInDirectory(directory, "diff", backupBranch, "--stat");
      ObjectNode errorJson = scope.getJsonMapper().createObjectNode();
      errorJson.put("status", "VERIFY_FAILED");
      errorJson.put("backup_branch", backupBranch);
      errorJson.put("message", "Content changed during squash - backup preserved");
      errorJson.put("diff_stat", diffStat);
      return scope.getJsonMapper().writeValueAsString(errorJson);
    }

    // Step 11: Verify exactly 1 commit from base
    String countStr = runGitCommandSingleLineInDirectory(directory,
      "rev-list", "--count", base + "..HEAD");
    // git rev-list --count always returns a non-negative integer
    int commitCount = Integer.parseInt(countStr);
    if (commitCount != 1)
    {
      // Restore from backup
      runGitCommandInDirectory(directory, "reset", "--hard", backupBranch);
      throw new IOException("Expected 1 commit from base, got " + commitCount);
    }

    // Build success JSON
    String shortCommit = runGitCommandSingleLineInDirectory(directory,
      "rev-parse", "--short", "HEAD");
    return buildSuccessJson(shortCommit, newCommit, backupBranch, concurrentMods);
  }

  /**
   * Handles a rebase failure by creating a backup, aborting the rebase, and returning
   * appropriate JSON.
   *
   * @param rebaseResult the failed rebase result
   * @return JSON string with REBASE_CONFLICT or ERROR status
   * @throws IOException if git operations fail during error handling
   */
  private String handleRebaseFailure(ProcessRunner.Result rebaseResult) throws IOException
  {
    // Check for conflicting files
    ProcessRunner.Result conflictResult = ProcessRunner.run(
      "git", "-C", directory, "diff", "--name-only", "--diff-filter=U");
    String conflictingFiles = conflictResult.stdout().strip();

    // Create backup before aborting
    String rebaseBackup = "backup-after-rebase-conflict-" +
      LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMATTER);
    ProcessRunner.run("git", "-C", directory, "branch", rebaseBackup);

    // Abort rebase to return to clean state
    ProcessRunner.run("git", "-C", directory, "rebase", "--abort");

    ObjectNode json = scope.getJsonMapper().createObjectNode();

    if (!conflictingFiles.isEmpty())
    {
      json.put("status", "REBASE_CONFLICT");
      json.put("backup_branch", rebaseBackup);
      json.put("message", "Conflict during pre-squash rebase");
      ArrayNode filesArray = json.putArray("conflicting_files");
      for (String file : conflictingFiles.split("\n"))
      {
        String trimmed = file.strip();
        if (!trimmed.isEmpty())
          filesArray.add(trimmed);
      }
    }
    else
    {
      json.put("status", "ERROR");
      json.put("backup_branch", rebaseBackup);
      json.put("message", "Rebase failed: " + rebaseResult.stdout().strip());
    }
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Detects files modified on both the base branch and the issue branch.
   * <p>
   * These were auto-resolved by rebase, but the resolution should be verified by the caller.
   *
   * @param base      the pinned base commit
   * @param mergeBase the merge-base before rebase
   * @return set of files modified on both branches, or empty set if none
   */
  private Set<String> detectConcurrentModifications(String base, String mergeBase)
  {
    Set<String> result = new LinkedHashSet<>();

    // Files modified on base branch since the worktree branched
    ProcessRunner.Result baseChangedResult = ProcessRunner.run(
      "git", "-C", directory, "diff", "--name-only", mergeBase + ".." + base);
    String baseOutput = baseChangedResult.stdout().strip();
    if (baseChangedResult.exitCode() != 0 || baseOutput.isBlank())
      return result;

    Set<String> baseChanged = parseLinesToSet(baseOutput);

    // Files modified on issue branch (after rebase, relative to base)
    ProcessRunner.Result issueChangedResult = ProcessRunner.run(
      "git", "-C", directory, "diff", "--name-only", base + "..HEAD");
    String issueOutput = issueChangedResult.stdout().strip();
    if (issueChangedResult.exitCode() != 0 || issueOutput.isBlank())
      return result;

    Set<String> issueChanged = parseLinesToSet(issueOutput);

    // Intersection
    for (String file : baseChanged)
    {
      if (issueChanged.contains(file))
        result.add(file);
    }
    return result;
  }

  /**
   * Parses newline-delimited output into a set of non-empty strings.
   *
   * @param output the newline-delimited string
   * @return a set of non-empty lines, preserving insertion order
   */
  private static Set<String> parseLinesToSet(String output)
  {
    Set<String> result = new LinkedHashSet<>(Arrays.asList(output.split("\n")));
    result.removeIf(String::isEmpty);
    return result;
  }

  /**
   * Builds the success JSON response.
   *
   * @param shortCommit    the short commit hash
   * @param fullCommit     the full commit hash
   * @param backupBranch   the backup branch name
   * @param concurrentMods files modified on both branches, or empty set
   * @return JSON string with OK status
   * @throws IOException if JSON serialization fails
   */
  private String buildSuccessJson(String shortCommit, String fullCommit, String backupBranch,
    Set<String> concurrentMods) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "OK");
    json.put("commit", shortCommit);
    json.put("commit_full", fullCommit);
    json.put("backup_branch", backupBranch);
    json.put("backup_verified", true);

    if (!concurrentMods.isEmpty())
    {
      ArrayNode warningsArray = json.putArray("warnings");
      ObjectNode warning = warningsArray.addObject();
      warning.put("type", "CONCURRENT_MODIFICATION");
      ArrayNode filesArray = warning.putArray("files");
      for (String file : concurrentMods)
        filesArray.add(file);
      warning.put("message",
        "These files were modified on both branches. Rebase auto-resolved the changes " +
          "but the result should be verified.");
    }

    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Main method for command-line execution.
   *
   * @param args command-line arguments: BASE_BRANCH COMMIT_MESSAGE [WORKTREE_PATH]
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 2)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: git-squash <BASE_BRANCH> <COMMIT_MESSAGE> [WORKTREE_PATH]"
        }""");
      System.exit(1);
    }

    String baseBranch = args[0];
    String commitMessage = args[1];
    String directory;
    if (args.length > 2)
      directory = args[2];
    else
      directory = ".";

    try (JvmScope scope = new MainJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, directory);
      try
      {
        String result = cmd.execute(baseBranch, commitMessage);
        System.out.println(result);
      }
      catch (IllegalArgumentException e)
      {
        // Commit message validation failure - print plain text to stderr (matching bash behavior)
        System.err.println(e.getMessage());
        System.exit(1);
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
      Logger log = LoggerFactory.getLogger(GitSquash.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}
