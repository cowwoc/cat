/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.CatMetadata;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.GitRebase;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitRebase.
 * <p>
 * Tests verify argument parsing, backup creation, conflict detection, and rebase behavior
 * in isolated git repositories.
 */
public class GitRebaseTest
{
  /**
   * Verifies that constructing with a null scope throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = "(?s).*")
  public void constructorRejectsNullScope()
  {
    new GitRebase(null, Path.of("."));
  }

  /**
   * Verifies that constructing with a null working directory throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = "(?s).*")
  public void constructorRejectsNullWorkingDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("git-rebase-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new GitRebase(scope, null);
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

        GitRebase cmd = new GitRebase(scope, repoDir);
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

        GitRebase cmd = new GitRebase(scope, repoDir);
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
        GitRebase cmd = new GitRebase(scope, repoDir);
        // When the target branch doesn't exist, detectForkPoint throws IOException
        // because git merge-base fails. This propagates as an IOException.
        try
        {
          cmd.execute("nonexistent-branch-xyz");
          requireThat(false, "shouldNotReachHere").isTrue();
        }
        catch (IOException e)
        {
          requireThat(e.getMessage(), "errorMessage").contains("nonexistent-branch-xyz");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that a cat-branch-point file containing a commit hash is used when target branch is not
   * provided.
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

        GitRebase cmd = new GitRebase(scope, repoDir);
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
        GitRebase cmd = new GitRebase(scope, repoDir);
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
   * Verifies that tree-state comparison correctly validates rebase success.
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

        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

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
   */
  @Test
  public void executeSucceedsAndTreeStatePassesOnCleanRebase() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

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
   * Verifies that deleted_orphans is empty when no new untracked files appear after rebase.
   */
  @Test
  public void executeReturnsEmptyDeletedOrphansWhenNoOrphans() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"deleted_orphans\" : [ ]");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that the deleted_orphans field is present in the JSON output.
   * <p>
   * Standard git rebase properly removes tracked files when the base changes, so orphans
   * don't appear in normal rebase scenarios. This test verifies the output format includes
   * the field. The orphan cleanup mechanism is a defensive safety net for edge cases.
   */
  @Test
  public void executeIncludesDeletedOrphansField() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"deleted_orphans\"");
        requireThat(result, "result").contains("\"deleted_orphans\" : [ ]");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that pre-existing untracked files are preserved and not deleted as orphans after rebase.
   */
  @Test
  public void executePreservesPreExistingUntrackedFiles() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Create a pre-existing untracked file before the rebase
        Path preExistingFile = repoDir.resolve("pre-existing-untracked.txt");
        Files.writeString(preExistingFile, "pre-existing content");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
        // deleted_orphans should be empty — pre-existing file must not be deleted
        requireThat(result, "result").contains("\"deleted_orphans\" : [ ]");

        // Verify the pre-existing file still exists on disk
        requireThat(Files.exists(preExistingFile), "preExistingFileExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebasing onto a rewritten upstream that dropped a file removes the orphaned file.
   * <p>
   * Scenario: main has a file, feature branches from it, then main is retroactively rewritten to
   * remove the file. A standard {@code git rebase main} would say "up to date" and leave the file
   * tracked. Using {@code git merge-base --fork-point}, GitRebase detects the retroactive
   * rewrite and uses {@code git rebase --onto} to correctly replay only the feature commits.
   */
  @Test
  public void executeRemovesOrphanedFileAfterRetroactiveRewrite() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Add a file on main that will later be retroactively removed
        Files.writeString(repoDir.resolve("doomed.txt"), "this file will be removed");
        TestUtils.runGit(repoDir, "add", "doomed.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add doomed.txt");
        String forkPoint = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

        // Create feature branch with its own commit
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Go back to main and retroactively rewrite it to remove doomed.txt
        TestUtils.runGit(repoDir, "checkout", "main");
        String initialCommit = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD~1");
        TestUtils.runGit(repoDir, "rebase", "--onto", initialCommit, forkPoint, "main");

        // Switch to feature and run rebase with branch name
        TestUtils.runGit(repoDir, "checkout", "feature");
        requireThat(Files.exists(repoDir.resolve("doomed.txt")), "doomedExistsBefore").isTrue();

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"OK\"");
        requireThat(Files.exists(repoDir.resolve("doomed.txt")), "doomedExistsAfter").isFalse();
        requireThat(Files.exists(repoDir.resolve("feature.txt")), "featureExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebasing onto a rewritten upstream that dropped a directory removes the orphaned
   * directory and all its contents.
   */
  @Test
  public void executeRemovesOrphanedDirectoryAfterRetroactiveRewrite() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Add a directory tree on main that will be retroactively removed
        Path issueDir = repoDir.resolve("issues").resolve("v2.1").resolve("some-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("PLAN.md"), "plan content");
        Files.writeString(issueDir.resolve("STATE.md"), "state content");
        TestUtils.runGit(repoDir, "add", "issues/");
        TestUtils.runGit(repoDir, "commit", "-m", "add issue directory");
        String forkPoint = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

        // Create feature branch
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Rewrite main to remove the issue directory
        TestUtils.runGit(repoDir, "checkout", "main");
        String initialCommit = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD~1");
        TestUtils.runGit(repoDir, "rebase", "--onto", initialCommit, forkPoint, "main");

        TestUtils.runGit(repoDir, "checkout", "feature");
        requireThat(Files.exists(issueDir.resolve("PLAN.md")), "planExistsBefore").isTrue();

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"OK\"");
        requireThat(Files.exists(issueDir), "issueDirExistsAfter").isFalse();
        requireThat(Files.exists(repoDir.resolve("issues")), "issuesRootExistsAfter").isFalse();
        requireThat(Files.exists(repoDir.resolve("feature.txt")), "featureExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that multiple feature commits are all replayed when rebasing onto a rewritten upstream.
   */
  @Test
  public void executeReplaysMultipleCommitsAfterRetroactiveRewrite() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        Files.writeString(repoDir.resolve("doomed.txt"), "doomed");
        TestUtils.runGit(repoDir, "add", "doomed.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add doomed.txt");
        String forkPoint = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("f1.txt"), "f1");
        TestUtils.runGit(repoDir, "add", "f1.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit 1");
        Files.writeString(repoDir.resolve("f2.txt"), "f2");
        TestUtils.runGit(repoDir, "add", "f2.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit 2");
        Files.writeString(repoDir.resolve("f3.txt"), "f3");
        TestUtils.runGit(repoDir, "add", "f3.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit 3");

        TestUtils.runGit(repoDir, "checkout", "main");
        String initialCommit = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD~1");
        TestUtils.runGit(repoDir, "rebase", "--onto", initialCommit, forkPoint, "main");

        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"commits_rebased\" : 3");
        requireThat(Files.exists(repoDir.resolve("doomed.txt")), "doomedExists").isFalse();
        requireThat(Files.exists(repoDir.resolve("f1.txt")), "f1Exists").isTrue();
        requireThat(Files.exists(repoDir.resolve("f2.txt")), "f2Exists").isTrue();
        requireThat(Files.exists(repoDir.resolve("f3.txt")), "f3Exists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that pre-existing untracked files survive a rebase onto a rewritten upstream.
   */
  @Test
  public void executePreservesUntrackedFilesDuringRetroactiveRewrite() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        Files.writeString(repoDir.resolve("doomed.txt"), "doomed");
        TestUtils.runGit(repoDir, "add", "doomed.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add doomed.txt");
        String forkPoint = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        TestUtils.runGit(repoDir, "checkout", "main");
        String initialCommit = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD~1");
        TestUtils.runGit(repoDir, "rebase", "--onto", initialCommit, forkPoint, "main");

        TestUtils.runGit(repoDir, "checkout", "feature");

        // Create an untracked file before the rebase
        Path untrackedFile = repoDir.resolve("my-notes.txt");
        Files.writeString(untrackedFile, "user notes");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"OK\"");
        requireThat(Files.exists(untrackedFile), "untrackedFilePreserved").isTrue();
        requireThat(Files.readString(untrackedFile), "untrackedContent").isEqualTo("user notes");
        requireThat(Files.exists(repoDir.resolve("doomed.txt")), "doomedRemoved").isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase works correctly when main gains new commits (no retroactive rewrite).
   * <p>
   * This is the standard rebase scenario. The fork point equals the merge base, so
   * {@code git rebase --onto target merge-base} is equivalent to {@code git rebase target}.
   */
  @Test
  public void executeSucceedsWhenMainMovedForward() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Create feature branch
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Add new commits on main (forward progress, not retroactive rewrite)
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.writeString(repoDir.resolve("main-new.txt"), "new main content");
        TestUtils.runGit(repoDir, "add", "main-new.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "new main commit");

        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"commits_rebased\" : 1");
        // Feature should have both its own file and the new main file
        requireThat(Files.exists(repoDir.resolve("feature.txt")), "featureExists").isTrue();
        requireThat(Files.exists(repoDir.resolve("main-new.txt")), "mainNewExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that when upstream drops multiple files, all orphaned files are removed.
   */
  @Test
  public void executeRemovesMultipleOrphanedFiles() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Add multiple files on main
        Files.writeString(repoDir.resolve("doomed1.txt"), "d1");
        Files.writeString(repoDir.resolve("doomed2.txt"), "d2");
        Files.writeString(repoDir.resolve("doomed3.txt"), "d3");
        TestUtils.runGit(repoDir, "add", "doomed1.txt", "doomed2.txt", "doomed3.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add doomed files");
        String forkPoint = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        TestUtils.runGit(repoDir, "checkout", "main");
        String initialCommit = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD~1");
        TestUtils.runGit(repoDir, "rebase", "--onto", initialCommit, forkPoint, "main");

        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"OK\"");
        requireThat(Files.exists(repoDir.resolve("doomed1.txt")), "d1").isFalse();
        requireThat(Files.exists(repoDir.resolve("doomed2.txt")), "d2").isFalse();
        requireThat(Files.exists(repoDir.resolve("doomed3.txt")), "d3").isFalse();
        requireThat(Files.exists(repoDir.resolve("feature.txt")), "feature").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that fork-point detection gracefully falls back to merge-base when the target
   * is a commit hash (not a branch name), since {@code git merge-base --fork-point} requires
   * a branch name with reflog entries.
   */
  @Test
  public void executeFallsBackToMergeBaseWithCommitHash() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Use commit hash instead of branch name
        String mainHash = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "main");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute(mainHash);

        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"commits_rebased\" : 1");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that when upstream is rewritten to add new commits while dropping old ones,
   * the feature commits are correctly replayed and new upstream content is incorporated.
   */
  @Test
  public void executeHandlesUpstreamRewriteWithNewContent() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Add a file on main
        Files.writeString(repoDir.resolve("doomed.txt"), "doomed");
        TestUtils.runGit(repoDir, "add", "doomed.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add doomed.txt");
        String forkPoint = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

        // Branch feature
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Rewrite main: drop doomed.txt, add replacement.txt
        TestUtils.runGit(repoDir, "checkout", "main");
        String initialCommit = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD~1");
        TestUtils.runGit(repoDir, "rebase", "--onto", initialCommit, forkPoint, "main");
        Files.writeString(repoDir.resolve("replacement.txt"), "replacement");
        TestUtils.runGit(repoDir, "add", "replacement.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add replacement.txt");

        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"OK\"");
        requireThat(Files.exists(repoDir.resolve("doomed.txt")), "doomedRemoved").isFalse();
        requireThat(Files.exists(repoDir.resolve("replacement.txt")), "replacementExists").isTrue();
        requireThat(Files.exists(repoDir.resolve("feature.txt")), "featureExists").isTrue();
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

        GitRebase cmd = new GitRebase(scope, repoDir);
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
