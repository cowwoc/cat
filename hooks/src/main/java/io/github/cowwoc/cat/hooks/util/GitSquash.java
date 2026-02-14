package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommand;
import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandSingleLine;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Atomic git squash with comprehensive safety checks.
 * <p>
 * Performs all git-squash operations in a single atomic execution,
 * reducing round-trips while preserving all safety checks.
 */
public final class GitSquash
{
  private final JsonMapper mapper;
  private String backupBranch = "";

  /**
   * Creates a new GitSquash instance.
   *
   * @param mapper the JSON mapper to use for serialization
   * @throws NullPointerException if mapper is null
   */
  public GitSquash(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  /**
   * Executes the squash operation.
   *
   * @param baseCommit the parent of first commit to squash
   * @param lastCommit the last commit to include in squash
   * @param messageFile the file containing commit message
   * @param originalBranch the optional branch to update (empty for current)
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String execute(String baseCommit, String lastCommit, String messageFile,
    String originalBranch) throws IOException
  {
    requireThat(baseCommit, "baseCommit").isNotBlank();
    requireThat(lastCommit, "lastCommit").isNotBlank();
    requireThat(messageFile, "messageFile").isNotBlank();
    requireThat(originalBranch, "originalBranch").isNotNull();

    long startTime = System.currentTimeMillis();

    Path messagePath = Paths.get(messageFile);
    if (!Files.exists(messagePath))
      throw new IOException("Commit message file not found: " + messageFile);

    checkoutCommit(lastCommit);

    String currentHead = getCommitHash("HEAD");
    String expectedHead = getCommitHash(lastCommit);
    if (!currentHead.equals(expectedHead))
      throw new IOException("HEAD is not at expected last commit: " + lastCommit);

    createBackupBranch();

    if (!isWorkingDirectoryClean())
      throw new IOException("Working directory not clean - commit or stash changes first");

    runGitCommandSingleLine("rev-parse", "--verify", baseCommit);

    int commitCount = getCommitCount(baseCommit, "HEAD");

    String originalHead = getCommitHash("HEAD");
    String commitsBeforeBase = "";

    String parents = runGitCommandSingleLine("log", "-1", "--format=%P", baseCommit);
    if (!parents.isBlank())
      commitsBeforeBase = getCommitList(baseCommit + "~1");

    softResetToBase(baseCommit);

    verifyNoChangesLostOrAdded();

    String commitMessage = Files.readString(messagePath, StandardCharsets.UTF_8);
    createSquashedCommit(commitMessage);

    String squashedCommit = getCommitHash("HEAD");

    verifyNonSquashedCommitsUnchanged(baseCommit, commitsBeforeBase, originalHead);

    verifyFinalCount(baseCommit);

    if (!originalBranch.isEmpty() && isDetachedHead())
    {
      forceUpdateBranch(originalBranch, "HEAD");
      checkoutBranch(originalBranch);
    }

    deleteBackupBranch();

    long endTime = System.currentTimeMillis();
    long duration = (endTime - startTime) / 1000;

    return buildSuccessJson(commitCount, squashedCommit, duration);
  }

  /**
   * Checks out a commit.
   *
   * @param commit the commit reference
   * @throws IOException if the operation fails
   */
  private void checkoutCommit(String commit) throws IOException
  {
    runGitCommand("checkout", commit);
  }

  /**
   * Gets the full hash for a commit reference.
   *
   * @param ref the reference
   * @return the full commit hash
   * @throws IOException if the operation fails
   */
  private String getCommitHash(String ref) throws IOException
  {
    return runGitCommandSingleLine("rev-parse", ref);
  }

  /**
   * Creates a backup branch.
   *
   * @throws IOException if the operation fails
   */
  private void createBackupBranch() throws IOException
  {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    backupBranch = "backup-before-squash-" + LocalDateTime.now().format(formatter);

    runGitCommand("branch", backupBranch);
    runGitCommandSingleLine("rev-parse", "--verify", backupBranch);
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
   * Gets the commit count between two references.
   *
   * @param base the base reference
   * @param head the head reference
   * @return the commit count
   * @throws IOException if the operation fails
   */
  private int getCommitCount(String base, String head) throws IOException
  {
    String count = runGitCommandSingleLine("rev-list", "--count", base + ".." + head);
    return Integer.parseInt(count);
  }

  /**
   * Gets the list of commit hashes.
   *
   * @param ref the reference
   * @return newline-separated commit hashes
   * @throws IOException if the operation fails
   */
  private String getCommitList(String ref) throws IOException
  {
    return runGitCommand("rev-list", ref);
  }

  /**
   * Performs a soft reset to the base commit.
   *
   * @param baseCommit the base commit
   * @throws IOException if the operation fails
   */
  private void softResetToBase(String baseCommit) throws IOException
  {
    runGitCommand("reset", "--soft", baseCommit);
  }

  /**
   * Verifies that no changes were lost or added during squash.
   *
   * @throws IOException if changes were lost or added
   */
  private void verifyNoChangesLostOrAdded() throws IOException
  {
    String diffOutput = runGitCommand("diff", "--stat", backupBranch);
    if (!diffOutput.isEmpty())
    {
      throw new IOException("Staged changes don't match original commits! " +
        "Rollback: git reset --hard " + backupBranch);
    }
  }

  /**
   * Creates the squashed commit.
   *
   * @param message the commit message
   * @throws IOException if the operation fails
   */
  private void createSquashedCommit(String message) throws IOException
  {
    runGitCommand("commit", "-m", message);
  }

  /**
   * Verifies that non-squashed commits remain unchanged.
   *
   * @param baseCommit the base commit
   * @param commitsBeforeBase the commits before base
   * @param originalHead the original HEAD
   * @throws IOException if commits were modified
   */
  private void verifyNonSquashedCommitsUnchanged(String baseCommit, String commitsBeforeBase,
    String originalHead) throws IOException
  {
    if (!commitsBeforeBase.isEmpty())
    {
      String currentCommitsBeforeBase = getCommitList(baseCommit + "~1");
      if (!commitsBeforeBase.equals(currentCommitsBeforeBase))
      {
        throw new IOException("Commits before base were modified. " +
          "Rollback: git reset --hard " + backupBranch);
      }
    }

    String treeDiff = runGitCommand("diff", "--stat", originalHead);
    if (!treeDiff.isEmpty())
    {
      throw new IOException("Working tree doesn't match original HEAD! " +
        "Rollback: git reset --hard " + backupBranch);
    }
  }

  /**
   * Verifies that exactly 1 commit exists after squash.
   *
   * @param baseCommit the base commit
   * @throws IOException if the count is not 1
   */
  private void verifyFinalCount(String baseCommit) throws IOException
  {
    int newCommitCount = getCommitCount(baseCommit, "HEAD");
    if (newCommitCount != 1)
      throw new IOException("Expected 1 commit after squash, got " + newCommitCount);
  }

  /**
   * Checks if HEAD is detached.
   *
   * @return true if HEAD is detached
   * @throws IOException if the operation fails
   */
  private boolean isDetachedHead() throws IOException
  {
    String branch = runGitCommandSingleLine("rev-parse", "--abbrev-ref", "HEAD");
    return branch.equals("HEAD");
  }

  /**
   * Force updates a branch to point to a commit.
   *
   * @param branch the branch name
   * @param commit the commit reference
   * @throws IOException if the operation fails
   */
  private void forceUpdateBranch(String branch, String commit) throws IOException
  {
    runGitCommand("branch", "-f", branch, commit);
  }

  /**
   * Checks out a branch.
   *
   * @param branch the branch name
   * @throws IOException if the operation fails
   */
  private void checkoutBranch(String branch) throws IOException
  {
    runGitCommand("checkout", branch);
  }

  /**
   * Deletes the backup branch.
   */
  private void deleteBackupBranch()
  {
    try
    {
      if (!backupBranch.isEmpty())
        runGitCommand("branch", "-D", backupBranch);
    }
    catch (IOException _)
    {
    }
  }

  /**
   * Builds the success JSON response.
   *
   * @param commitCount the number of commits squashed
   * @param squashedCommit the squashed commit hash
   * @param duration the operation duration in seconds
   * @return JSON string
   * @throws IOException if JSON creation fails
   */
  private String buildSuccessJson(int commitCount, String squashedCommit, long duration)
    throws IOException
  {
    ObjectNode json = mapper.createObjectNode();
    json.put("status", "success");
    json.put("message", "Squash completed successfully: " + commitCount + " commits → 1 commit");
    json.put("duration_seconds", duration);
    json.put("backup_branch", "none");
    json.put("squashed_commit", squashedCommit);
    json.put("commits_squashed", commitCount);
    json.put("working_directory", System.getProperty("user.dir"));
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
    if (args.length < 3)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: git-squash <base_commit> <last_commit> <message_file> [branch]"
        }""");
      System.exit(1);
    }

    String baseCommit = args[0];
    String lastCommit = args[1];
    String messageFile = args[2];
    String originalBranch;
    if (args.length > 3)
      originalBranch = args[3];
    else
      originalBranch = "";

    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      GitSquash cmd = new GitSquash(mapper);
      try
      {
        String result = cmd.execute(baseCommit, lastCommit, messageFile, originalBranch);
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
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GitSquash.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}
