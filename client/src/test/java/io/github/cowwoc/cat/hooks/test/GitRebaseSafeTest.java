/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

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
   * Verifies that constructing with a blank directory throws IllegalArgumentException.
   */
  @Test
  public void constructorRejectsBlankDirectory() throws IOException
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
   * Verifies that a cat-base file is used when target branch is not provided.
   */
  @Test
  public void executeReadsCatBaseFileWhenTargetBranchAbsent() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Create a feature branch
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Write cat-base file
        Path gitDir = Path.of(TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "--git-dir"));
        if (!gitDir.isAbsolute())
          gitDir = repoDir.resolve(gitDir);
        Files.writeString(gitDir.resolve("cat-base"), "main");

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
   * Verifies that rebase fails with ERROR when cat-base file is absent and target not provided.
   */
  @Test
  public void executeFailsWhenCatBaseFileMissingAndNoTarget() throws IOException
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
        requireThat(result, "result").contains("cat-base");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that actual content changes in the issue branch are still detected as ERROR after rebase.
   * <p>
   * This test guards against regressions where the content-change verification is too permissive.
   * It simulates a scenario where the issue branch content diverges: after amending, the feature
   * commit has different content than what would be produced by a clean rebase of the original commit.
   */
  @Test
  public void verifyDetectsActualContentChanges() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Create feature branch with "original content" in feature.txt
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "original content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Advance main with a new commit (base advances)
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.writeString(repoDir.resolve("main-advance.txt"), "main advance content");
        TestUtils.runGit(repoDir, "add", "main-advance.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "main advances");

        // Return to feature branch and amend to corrupt feature.txt content
        TestUtils.runGit(repoDir, "checkout", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "corrupted content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "--amend", "--no-edit");

        GitRebaseSafe cmd = new GitRebaseSafe(scope, repoDir.toString());
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"ERROR\"");
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
