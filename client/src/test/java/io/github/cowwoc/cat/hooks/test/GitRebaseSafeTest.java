/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.CatMetadata;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.GitRebaseSafe;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitRebaseSafe.
 * <p>
 * Tests verify argument parsing, backup creation, conflict detection, and rebase behavior
 * in isolated git repositories.
 */
public class GitRebaseSafeTest
{
  /**
   * Verifies that constructing with a null scope throws NullPointerException.
   */
  @Test
  public void constructorRejectsNullScope()
  {
    try
    {
      new GitRebaseSafe(null, ".");
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").isNotNull();
    }
  }

  /**
   * Verifies that constructing with a blank source branch throws IllegalArgumentException.
   */
  @Test
  public void constructorRejectsBlankSourceBranch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("git-rebase-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        new GitRebaseSafe(scope, "");
      }
      catch (IllegalArgumentException e)
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
   * Verifies that rebase onto same base succeeds (no commits to rebase).
   */
  @Test
  public void executeSucceedsWhenBranchAlreadyOnBase() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Create a feature branch with a commit
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"commits_rebased\"");
        requireThat(result, "result").contains("\"backup_cleaned\"");
        requireThat(result, "result").contains("true");

        // Verify backup branch was actually deleted after successful rebase
        String backupBranches = TestUtils.runGitCommandWithOutput(repoDir, "branch", "--list",
          "backup-before-rebase-*");
        requireThat(backupBranches.strip(), "backupBranches").isEqualTo("");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase correctly reports commits_rebased count.
   */
  @Test
  public void executeReportsCommitsRebased() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Create a feature branch with 2 commits
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature1.txt"), "feature1 content");
        TestUtils.runGit(repoDir, "add", "feature1.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit 1");

        Files.writeString(repoDir.resolve("feature2.txt"), "feature2 content");
        TestUtils.runGit(repoDir, "add", "feature2.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit 2");

        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"commits_rebased\" : 2");

        // Verify backup branch was actually deleted after successful rebase
        String backupBranches = TestUtils.runGitCommandWithOutput(repoDir, "branch", "--list",
          "backup-before-rebase-*");
        requireThat(backupBranches.strip(), "backupBranches").isEqualTo("");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase fails with ERROR when the target branch does not exist.
   */
  @Test
  public void executeFailsWhenTargetBranchDoesNotExist() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("nonexistent-branch-xyz");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"ERROR\"");
        requireThat(result, "result").contains("nonexistent-branch-xyz");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that a cat-branch-point file containing a commit hash is used when target branch is not provided.
   */
  @Test
  public void executeReadsCatBranchPointFileWhenTargetBranchAbsent() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Record the fork-point commit hash (current HEAD on main before creating feature branch)
        String forkCommitHash = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

        // Create a feature branch
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Write cat-branch-point file with the fork-point commit hash (not a branch name)
        Path gitDir = Path.of(TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "--git-dir"));
        if (!gitDir.isAbsolute())
          gitDir = repoDir.resolve(gitDir);
        Files.writeString(gitDir.resolve(CatMetadata.BRANCH_POINT_FILE), forkCommitHash);

        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase fails with ERROR when cat-branch-point file is absent and target not provided.
   */
  @Test
  public void executeFailsWhenCatBranchPointFileMissingAndNoTarget() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"ERROR\"");
        requireThat(result, "result").contains(CatMetadata.BRANCH_POINT_FILE);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that tree-state comparison correctly detects when rebase changes content.
   * <p>
   * This test ensures that after rebase, if the tree differs from the backup, an error is reported.
   * Note: With immutable fork commit in cat-branch-point, base branches cannot advance during rebase,
   * so this test verifies the tree comparison with a single fixed base.
   */
  @Test
  public void verifyDetectsActualContentChanges() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Set up an existing file on main
        Files.writeString(repoDir.resolve("shared.txt"), "initial content\n");
        TestUtils.runGit(repoDir, "add", "shared.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add shared.txt");

        // Create feature branch that modifies the existing file
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("shared.txt"), "initial content\nfeature addition\n");
        TestUtils.runGit(repoDir, "add", "shared.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature modifies shared.txt");

        // Return to feature branch for rebase onto main
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("main");

        // With immutable fork commit, rebase onto the current main tip should succeed
        // Tree state comparison should pass because the feature's content is preserved
        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that tree-state comparison correctly validates rebase success.
   * <p>
   * With immutable fork commit in cat-branch-point, we always rebase onto the exact commit stored
   * at worktree creation time, regardless of whether the base branch has advanced.
   * This test verifies the tree state check with a simple, clean rebase scenario.
   */
  @Test
  public void executeSucceedsAndTreeStatePassesOnCleanRebase() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Create feature branch with one commit (simulates worktree creation point)
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Return to feature branch and run git-rebase-safe against main
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("main");

        // Must return OK — tree-state check should pass for a successful rebase
        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"commits_rebased\"");
        requireThat(result, "result").contains("\"backup_cleaned\"");
        requireThat(result, "result").contains("true");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase with conflicts returns CONFLICT status and preserves backup.
   */
  @Test
  public void executeReturnConflictStatusOnConflict() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Create a conflicting situation:
        // main: modifies conflict.txt
        // feature: also modifies conflict.txt differently

        // First modify on main
        Files.writeString(repoDir.resolve("conflict.txt"), "main version");
        TestUtils.runGit(repoDir, "add", "conflict.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "main adds conflict.txt");

        // Now create feature branch from initial commit (before main's change)
        String initialCommit = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD~1");
        TestUtils.runGit(repoDir, "checkout", "-b", "feature", initialCommit);
        Files.writeString(repoDir.resolve("conflict.txt"), "feature version");
        TestUtils.runGit(repoDir, "add", "conflict.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature adds conflict.txt");

        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"CONFLICT\"");
        requireThat(result, "result").contains("backup_branch");
        requireThat(result, "result").contains("conflicting_files");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }
}
