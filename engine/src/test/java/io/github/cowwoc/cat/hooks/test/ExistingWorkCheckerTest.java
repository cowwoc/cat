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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ExistingWorkChecker.
 * <p>
 * Tests verify checking for existing commits in a worktree compared to a base branch.
 * Each test is self-contained with temporary git repositories to support parallel execution.
 */
public class ExistingWorkCheckerTest
{
  /**
   * Verifies that check returns no existing work when worktree is at base branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkReturnsNoExistingWorkWhenAtBaseBranch() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "base-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "task-branch");

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "base-branch");

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
   * Verifies that check returns existing work when worktree has commits ahead of base.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkReturnsExistingWorkWhenCommitsAhead() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "base-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "task-branch");
      createCommit(tempDir, "Work in progress 1");
      createCommit(tempDir, "Work in progress 2");

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "base-branch");

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
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "base-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "task-branch");
      for (int i = 1; i <= 7; ++i)
        createCommit(tempDir, "Commit " + i);

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "base-branch");

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
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "base-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "task-branch");
      createCommit(tempDir, "First work");
      createCommit(tempDir, "Second work");

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "base-branch");

      requireThat(result.commitSummary(), "commitSummary").contains("|");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check throws on non-existent worktree path.
   */
  @Test
  public void checkThrowsOnNonExistentWorktreePath()
  {
    try
    {
      ExistingWorkChecker.check("/nonexistent/path", "base-branch");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("Cannot access worktree");
    }
    catch (IOException _)
    {
      requireThat(false, "wrongException").isTrue();
    }
  }

  /**
   * Verifies that check throws IOException on empty git repository.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkThrowsOnEmptyRepository() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empty-repo-test");
    try
    {
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "init");
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "config", "user.name", "Test User");
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "config", "user.email", "test@example.com");

      try
      {
        ExistingWorkChecker.check(tempDir.toString(), "main");
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").isNotNull();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check throws IOException on non-existent base branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkThrowsOnNonExistentBaseBranch() throws IOException
  {
    Path tempDir = createTempGitRepo();
    try
    {
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "real-branch");
      createCommit(tempDir, "Initial commit");

      try
      {
        ExistingWorkChecker.check(tempDir.toString(), "nonexistent-branch");
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").isNotNull();
      }
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
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "base-branch");
      createCommit(tempDir, "Initial commit");

      GitCommands.runGitCommandInDirectory(tempDir.toString(), "checkout", "-b", "task-branch");
      for (int i = 1; i <= 5; ++i)
        createCommit(tempDir, "Commit " + i);

      CheckResult result = ExistingWorkChecker.check(tempDir.toString(), "base-branch");

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
   * Verifies that toJson produces correct format for no existing work.
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

      requireThat(json, "json").contains("\"has_existing_work\"");
      requireThat(json, "json").contains("false");
      requireThat(json, "json").contains("\"existing_commits\"");
      requireThat(json, "json").contains("0");
      requireThat(json, "json").contains("\"commit_summary\"");
    }
  }

  /**
   * Verifies that toJson produces correct format for existing work.
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

      requireThat(json, "json").contains("\"has_existing_work\"");
      requireThat(json, "json").contains("true");
      requireThat(json, "json").contains("\"existing_commits\"");
      requireThat(json, "json").contains("3");
      requireThat(json, "json").contains("\"commit_summary\"");
      requireThat(json, "json").contains("abc1234");
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
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "init");
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "config", "user.name", "Test User");
      GitCommands.runGitCommandInDirectory(tempDir.toString(), "config", "user.email", "test@example.com");
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
    GitCommands.runGitCommandInDirectory(repoPath.toString(), "add", file.getFileName().toString());
    GitCommands.runGitCommandInDirectory(repoPath.toString(), "commit", "-m", message);
  }
}
