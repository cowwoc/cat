/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.GitMergeLinear;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;


/**
 * Tests for GitMergeLinear validation, error handling, and git operations.
 * <p>
 * Validation tests verify input constraints without requiring actual git repository setup.
 * Integration tests use isolated temporary git repositories to verify actual merge behavior.
 */
public class GitMergeLinearTest
{
  /**
   * Verifies that execute rejects null sourceBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*sourceBranch.*")
  public void executeRejectsNullSourceBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute(null, "main");
    }
  }

  /**
   * Verifies that execute rejects blank sourceBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sourceBranch.*")
  public void executeRejectsBlankSourceBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute("", "main");
    }
  }

  /**
   * Verifies that execute rejects null targetBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void executeRejectsNullTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute("task-branch", null);
    }
  }

  /**
   * Verifies that execute rejects blank targetBranch.
   * <p>
   * The target branch is required — there is no auto-detect fallback.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void executeRejectsBlankTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute("task-branch", "");
    }
  }

  /**
   * Verifies that a successful fast-forward merge returns JSON with status "success".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void successfulMergeReturnsSuccessStatus() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      // Create a branch with exactly one commit ahead of main
      createSingleCommitBranch(repoDir, "task-branch");

      // Check out the target branch to perform the merge from
      TestUtils.runGit(repoDir, "checkout", "main");

      Path pluginRoot = Files.createTempDirectory("plugin-root-");
      try (JvmScope scope = new TestJvmScope(repoDir, pluginRoot))
      {
        GitMergeLinear cmd = new GitMergeLinear(scope, repoDir.toString());
        String result = cmd.execute("task-branch", "main");

        JsonNode json = scope.getJsonMapper().readTree(result);
        requireThat(json.get("status").asString(), "status").isEqualTo("success");
        requireThat(json.get("merged_commit").asString(), "merged_commit").isNotBlank();
        requireThat(json.get("source_branch").asString(), "source_branch").isEqualTo("task-branch");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that execute throws IOException when the source branch has more than one commit.
   * <p>
   * The linear merge requires exactly one commit so that the merge history stays clean.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*exactly 1 commit.*")
  public void multipleCommitsBranchIsRejected() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      // Create a branch with two commits ahead of main
      TestUtils.runGit(repoDir, "checkout", "-b", "task-branch");
      writeAndCommit(repoDir, "file1.txt", "first", "first commit");
      writeAndCommit(repoDir, "file2.txt", "second", "second commit");

      TestUtils.runGit(repoDir, "checkout", "main");

      Path pluginRoot = Files.createTempDirectory("plugin-root-");
      try (JvmScope scope = new TestJvmScope(repoDir, pluginRoot))
      {
        GitMergeLinear cmd = new GitMergeLinear(scope, repoDir.toString());
        cmd.execute("task-branch", "main");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that execute throws IOException when not on the target branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Must be on.*")
  public void wrongCurrentBranchIsRejected() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      createSingleCommitBranch(repoDir, "task-branch");
      // Stay on task-branch (not main) and try to merge into main

      Path pluginRoot = Files.createTempDirectory("plugin-root-");
      try (JvmScope scope = new TestJvmScope(repoDir, pluginRoot))
      {
        GitMergeLinear cmd = new GitMergeLinear(scope, repoDir.toString());
        cmd.execute("task-branch", "main");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  // ============================================================================
  // Helper methods
  // ============================================================================

  /**
   * Creates a branch with exactly one commit ahead of the current branch.
   *
   * @param repoDir the repository directory
   * @param branchName the name of the branch to create
   */
  private static void createSingleCommitBranch(Path repoDir, String branchName)
  {
    TestUtils.runGit(repoDir, "checkout", "-b", branchName);
    writeAndCommit(repoDir, "task.txt", "task content", "feature: add task");
  }

  /**
   * Writes a file and commits it.
   *
   * @param repoDir the repository directory
   * @param fileName the file name to write
   * @param content the file content
   * @param message the commit message
   */
  private static void writeAndCommit(Path repoDir, String fileName, String content, String message)
  {
    try
    {
      Files.writeString(repoDir.resolve(fileName), content);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    TestUtils.runGit(repoDir, "add", fileName);
    TestUtils.runGit(repoDir, "commit", "-m", message);
  }
}
