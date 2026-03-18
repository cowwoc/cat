/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.ExistingWorkChecker;
import io.github.cowwoc.cat.hooks.util.ExistingWorkChecker.CheckResult;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ExistingWorkChecker.
 * <p>
 * Tests verify checking for existing commits in a source branch compared to a target branch.
 * Each test is self-contained with temporary git repositories to support parallel execution.
 */
public class ExistingWorkCheckerTest
{
  /**
   * Verifies that check returns no existing work when source branch is at target branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkReturnsNoExistingWorkWhenAtTargetBranch() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGit(tempDir, "checkout", "-b", "target-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGit(tempDir, "checkout", "-b", "source-branch");

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "target-branch");

      requireThat(result.hasExistingWork(), "hasExistingWork").isFalse();
      requireThat(result.existingCommits(), "existingCommits").isEqualTo(0);
      requireThat(result.commitSummary(), "commitSummary").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check returns existing work when source branch has commits ahead of target.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkReturnsExistingWorkWhenCommitsAhead() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGit(tempDir, "checkout", "-b", "target-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGit(tempDir, "checkout", "-b", "source-branch");
      createCommit(tempDir, "Work in progress 1");
      createCommit(tempDir, "Work in progress 2");

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "target-branch");

      requireThat(result.hasExistingWork(), "hasExistingWork").isTrue();
      requireThat(result.existingCommits(), "existingCommits").isEqualTo(2);
      requireThat(result.commitSummary(), "commitSummary").contains("Work in progress");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check limits commit summary to 5 commits.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkLimitsCommitSummaryToFiveCommits() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGit(tempDir, "checkout", "-b", "target-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGit(tempDir, "checkout", "-b", "source-branch");
      for (int i = 1; i <= 7; ++i)
        createCommit(tempDir, "Commit " + i);

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "target-branch");

      requireThat(result.hasExistingWork(), "hasExistingWork").isTrue();
      requireThat(result.existingCommits(), "existingCommits").isEqualTo(7);

      String[] commits = result.commitSummary().split("\\|");
      requireThat(commits.length, "summaryLength").isEqualTo(5);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check uses pipe separator for multiple commits.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkUsesPipeSeparatorForMultipleCommits() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGit(tempDir, "checkout", "-b", "target-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGit(tempDir, "checkout", "-b", "source-branch");
      createCommit(tempDir, "First work");
      createCommit(tempDir, "Second work");

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "target-branch");

      requireThat(result.commitSummary(), "commitSummary").contains("|");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check throws on non-existent source path.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Cannot access worktree.*")
  public void checkThrowsOnNonExistentWorktreePath() throws IOException
  {
    ExistingWorkChecker.check("/nonexistent/path", "target-branch");
  }

  /**
   * Verifies that check throws IOException on empty git repository.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = "(?s).*")
  public void checkThrowsOnEmptyRepository() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empty-repo-test");
    try
    {
      GitCommands.runGit(tempDir, "init");
      GitCommands.runGit(tempDir, "config", "user.name", "Test User");
      GitCommands.runGit(tempDir, "config", "user.email", "test@example.com");

      ExistingWorkChecker.check(tempDir.toString(), "main");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check throws IOException on non-existent target branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = "(?s).*")
  public void checkThrowsOnNonExistentTargetBranch() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGit(tempDir, "checkout", "-b", "real-branch");
      createCommit(tempDir, "Initial commit");

      ExistingWorkChecker.check(tempDir.toString(), "nonexistent-branch");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check returns all 5 commits when exactly 5 commits ahead.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkReturnsExactlyFiveCommitsWhenAtBoundary() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGit(tempDir, "checkout", "-b", "target-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGit(tempDir, "checkout", "-b", "source-branch");
      for (int i = 1; i <= 5; ++i)
        createCommit(tempDir, "Commit " + i);

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "target-branch");

      requireThat(result.hasExistingWork(), "hasExistingWork").isTrue();
      requireThat(result.existingCommits(), "existingCommits").isEqualTo(5);

      String[] commits = result.commitSummary().split("\\|");
      requireThat(commits.length, "summaryLength").isEqualTo(5);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that toJson produces correct field values for no existing work.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesCorrectFormatForNoExistingWork() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      CheckResult result = new CheckResult(false, 0, "");

      JsonMapper mapper = scope.getJsonMapper();
      String json = result.toJson(mapper);
      JsonNode parsed = mapper.readTree(json);

      requireThat(parsed.get("hasExistingWork").asBoolean(), "hasExistingWork").isFalse();
      requireThat(parsed.get("existingCommits").asInt(), "existingCommits").isEqualTo(0);
      requireThat(parsed.get("commitSummary").asString(), "commitSummary").isEqualTo("");
    }
  }

  /**
   * Verifies that toJson produces correct field values for existing work.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesCorrectFormatForExistingWork() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      CheckResult result = new CheckResult(true, 3, "abc1234 First|def5678 Second");

      JsonMapper mapper = scope.getJsonMapper();
      String json = result.toJson(mapper);
      JsonNode parsed = mapper.readTree(json);

      requireThat(parsed.get("hasExistingWork").asBoolean(), "hasExistingWork").isTrue();
      requireThat(parsed.get("existingCommits").asInt(), "existingCommits").isEqualTo(3);
      requireThat(parsed.get("commitSummary").asString(), "commitSummary").
        isEqualTo("abc1234 First|def5678 Second");
    }
  }

  /**
   * Verifies that run() with valid --worktree and --target-branch writes JSON output and returns true.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runWithValidArgsWritesJsonToStdout() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try (JvmScope scope = new TestJvmScope())
    {
      try
      {
        GitCommands.runGit(tempDir, "checkout", "-b", "target-branch");
        createCommit(tempDir, "Initial commit");
        GitCommands.runGit(tempDir, "checkout", "-b", "source-branch");

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = ExistingWorkChecker.run(
          new String[]{"--worktree", tempDir.toString(), "--target-branch", "target-branch"},
          scope, out, err);

        requireThat(success, "success").isTrue();
        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"hasExistingWork\"");
        requireThat(output, "output").contains("\"existingCommits\"");
        String errOutput = errBytes.toString(StandardCharsets.UTF_8);
        requireThat(errOutput, "errOutput").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that run() with missing --worktree writes error to stderr and returns false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runWithMissingWorktreeWritesErrorToStderr() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
      PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

      boolean success = ExistingWorkChecker.run(
        new String[]{"--target-branch", "main"},
        scope, out, err);

      requireThat(success, "success").isFalse();
      String errOutput = errBytes.toString(StandardCharsets.UTF_8);
      requireThat(errOutput, "errOutput").contains("--worktree");
      requireThat(errOutput, "errOutput").contains("required");
    }
  }

  /**
   * Verifies that run() with missing --target-branch writes error to stderr and returns false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runWithMissingTargetBranchWritesErrorToStderr() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try (JvmScope scope = new TestJvmScope())
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = ExistingWorkChecker.run(
          new String[]{"--worktree", tempDir.toString()},
          scope, out, err);

        requireThat(success, "success").isFalse();
        String errOutput = errBytes.toString(StandardCharsets.UTF_8);
        requireThat(errOutput, "errOutput").contains("--target-branch");
        requireThat(errOutput, "errOutput").contains("required");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Creates a temporary git repository for test isolation.
   *
   * @return the path to the created temporary directory
   */
  private Path createTempGitRepo()
  {
    try
    {
      Path tempDir = Files.createTempDirectory("existing-work-test");
      GitCommands.runGit(tempDir, "init");
      GitCommands.runGit(tempDir, "config", "user.name", "Test User");
      GitCommands.runGit(tempDir, "config", "user.email", "test@example.com");
      return tempDir;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a commit in the specified git repository.
   *
   * @param repoPath the repository path
   * @param message the commit message
   * @throws IOException if git operations fail
   */
  private void createCommit(Path repoPath, String message) throws IOException
  {
    Path file = repoPath.resolve("file-" + System.nanoTime() + ".txt");
    Files.writeString(file, "content " + System.nanoTime());
    GitCommands.runGit(repoPath, "add", file.getFileName().toString());
    GitCommands.runGit(repoPath, "commit", "-m", message);
  }
}
