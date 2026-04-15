/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.util.GitRebase;
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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
   * Verifies that rebase fails with a block response when target branch is not provided.
   */
  @Test
  public void executeFailsWhenTargetBranchAbsent() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("");

        requireThat(result, "result").contains("\"decision\"");
        requireThat(result, "result").contains("\"block\"");
        requireThat(result, "result").contains("TARGET_BRANCH is required");
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Add a directory tree on main that will be retroactively removed
        Path issueDir = repoDir.resolve("issues").resolve("v2.1").resolve("some-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("plan.md"), "plan content");
        Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");
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
        requireThat(Files.exists(issueDir.resolve("plan.md")), "planExistsBefore").isTrue();

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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
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

  /**
   * Verifies that rebase fails with a block response when the target branch renames a tracked path that still
   * exists in the current branch.
   * <p>
   * Scenario: main renames {@code .claude/cat/} to {@code .cat/} via {@code git mv}. Feature branch still
   * tracks {@code .claude/cat/config.json} (the old path). The rebase should fail before creating
   * a backup, reporting the old path as a tracked-path conflict.
   */
  @Test
  public void executeFailsWhenTargetBranchRenamesTrackedPath() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Set up the old directory structure on main
        Path oldDir = repoDir.resolve(".claude").resolve("cat");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("config.json"), "{\"key\": \"value\"}");
        TestUtils.runGit(repoDir, "add", ".claude/");
        TestUtils.runGit(repoDir, "commit", "-m", "add .claude/cat/config.json");

        // Create feature branch from here (still has old path)
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "feature commit");

        // Go back to main and rename .claude/cat to .cat
        TestUtils.runGit(repoDir, "checkout", "main");
        TestUtils.runGit(repoDir, "mv", ".claude/cat", ".cat");
        TestUtils.runGit(repoDir, "commit", "-m", "rename .claude/cat to .cat");

        // Switch to feature branch (still tracks .claude/cat/config.json)
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"decision\"");
        requireThat(result, "result").contains("\"block\"");
        requireThat(result, "result").contains(".claude/cat");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase fails with a block response when the current branch has file content referencing a
   * path that was renamed on the target branch.
   * <p>
   * Scenario: main adds and renames {@code .claude/cat/} to {@code .cat/}. Feature branch (forked before
   * the rename) has only {@code skill.md} containing the text {@code .claude/cat} — the old tracked config
   * file is removed from the feature branch so only the content reference remains. The rebase should fail
   * before creating a backup, reporting {@code skill.md} and the old path {@code .claude/cat}.
   */
  @Test
  public void executeFailsWhenCurrentBranchHasContentReferencingRenamedPath() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Set up the old directory structure on main and create a feature branch at this point
        Path oldDir = repoDir.resolve(".claude").resolve("cat");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("config.json"), "{\"key\": \"value\"}");
        TestUtils.runGit(repoDir, "add", ".claude/");
        TestUtils.runGit(repoDir, "commit", "-m", "add .claude/cat/config.json");

        // Create feature branch — remove the tracked config file, add skill.md with old path reference
        // (simulates a branch that only has a documentation reference, not the actual config file)
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        TestUtils.runGit(repoDir, "rm", "-r", ".claude/cat");
        Files.writeString(repoDir.resolve("skill.md"), "See .claude/cat for configuration");
        TestUtils.runGit(repoDir, "add", "skill.md");
        TestUtils.runGit(repoDir, "commit", "-m", "remove config file, add skill.md referencing .claude/cat");

        // Go back to main and rename .claude/cat to .cat
        TestUtils.runGit(repoDir, "checkout", "main");
        TestUtils.runGit(repoDir, "mv", ".claude/cat", ".cat");
        TestUtils.runGit(repoDir, "commit", "-m", "rename .claude/cat to .cat");

        // Switch to feature branch
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        requireThat(result, "result").contains("\"decision\"");
        requireThat(result, "result").contains("\"block\"");
        requireThat(result, "result").contains("skill.md");
        requireThat(result, "result").contains(".claude/cat");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase succeeds (no false positive) when the target branch has no renames.
   * <p>
   * The feature branch adds a new file. The path consistency validation should find no renamed paths and
   * allow the rebase to proceed normally.
   */
  @Test
  public void executeSucceedsWithNoPathRenamesOnTarget() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Add a commit on main with no renames
        Files.writeString(repoDir.resolve("main-file.txt"), "main content");
        TestUtils.runGit(repoDir, "add", "main-file.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add main-file.txt");

        // Feature branch adds a new file
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "feature content");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add feature.txt");

        // Add another commit on main (no rename)
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.writeString(repoDir.resolve("main-file2.txt"), "more main content");
        TestUtils.runGit(repoDir, "add", "main-file2.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add main-file2.txt");

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
   * Verifies that rebase succeeds (no false positive) when the feature branch performs the same rename
   * as the target branch, including documentation files that mention the old path.
   * <p>
   * Scenario: main renames {@code .claude/cat/} to {@code .cat/} via {@code git mv}. The feature branch
   * independently performs the same rename AND has a {@code plan.md} that mentions the old path
   * {@code .claude/cat} in its content (because the feature branch's purpose is to do the rename).
   * The current branch has already handled the rename, so the rebase should NOT flag it as an error.
   * This prevents false positives when the feature branch's purpose is to do the same rename as the target.
   */
  @Test
  public void executeSucceedsWhenFeatureBranchPerformsSameRenameAsTarget() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Set up the old directory structure on main
        Path oldDir = repoDir.resolve(".claude").resolve("cat");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("config.json"), "{\"key\": \"value\"}");
        TestUtils.runGit(repoDir, "add", ".claude/");
        TestUtils.runGit(repoDir, "commit", "-m", "add .claude/cat/config.json");

        // Create feature branch from this point, perform the same rename, and add plan.md that
        // mentions the old path (simulating the feature branch's purpose being the rename itself)
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        TestUtils.runGit(repoDir, "mv", ".claude/cat", ".cat");
        Files.writeString(repoDir.resolve("plan.md"), "Rename .claude/cat to .cat");
        TestUtils.runGit(repoDir, "add", "plan.md");
        TestUtils.runGit(repoDir, "commit", "-m", "feature: rename .claude/cat to .cat");

        // On main: also perform the same rename independently
        TestUtils.runGit(repoDir, "checkout", "main");
        TestUtils.runGit(repoDir, "mv", ".claude/cat", ".cat");
        TestUtils.runGit(repoDir, "commit", "-m", "main: rename .claude/cat to .cat");

        // Switch to feature branch and attempt rebase
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        // Should NOT return a block response — the feature branch already handled the rename
        requireThat(result, "result").doesNotContain("\"block\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that rebase succeeds (no false positive) when the feature branch has no content references
   * to the old path after a rename on the target branch.
   * <p>
   * Scenario: main renames directory {@code old-config/} to {@code new-config/}. Feature branch has
   * {@code feature.txt} that mentions only {@code new-config/} (not {@code old-config/}). Since the
   * feature branch has no stale references, rebase should succeed with no validation error.
   */
  @Test
  public void executeSucceedsWhenContentReferencesAlreadyUpdated() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Feature branch adds feature.txt — it only references new-config/ (no old-config/ reference)
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature.txt"), "See new-config/ for configuration");
        TestUtils.runGit(repoDir, "add", "feature.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add feature.txt referencing new-config/");

        // Main adds old-config/settings.txt and then renames old-config/ to new-config/
        TestUtils.runGit(repoDir, "checkout", "main");
        Path oldConfigDir = repoDir.resolve("old-config");
        Files.createDirectories(oldConfigDir);
        Files.writeString(oldConfigDir.resolve("settings.txt"), "settings content");
        TestUtils.runGit(repoDir, "add", "old-config/");
        TestUtils.runGit(repoDir, "commit", "-m", "add old-config/settings.txt");
        TestUtils.runGit(repoDir, "mv", "old-config", "new-config");
        TestUtils.runGit(repoDir, "commit", "-m", "rename old-config to new-config");

        // Switch to feature branch — has no reference to old-config, so validation should pass
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
   * Verifies that files not modified by the issue's commits are not flagged as content conflicts,
   * even when those files contain stale old-path references.
   * <p>
   * Scenario: the merge base has {@code old-tools/helper.sh} and {@code plan.md} (which references
   * {@code old-tools} as text). The target branch renames {@code old-tools/helper.sh} to
   * {@code new-tools/helper.sh}. The feature branch only adds a new unrelated file, leaving both
   * {@code old-tools/helper.sh} and {@code plan.md} untouched. The pre-rebase {@code git grep}
   * check finds {@code plan.md} containing {@code old-tools}; before the fix this produced a false
   * positive in the content-reference section. After the fix, {@code plan.md} is excluded from
   * content conflicts because the feature's commits never modified it.
   * <p>
   * The test also confirms that the tracked-path section of the block correctly identifies
   * {@code old-tools/helper.sh} as a true positive (the feature still has it at the old path),
   * and that the content-reference section does NOT mention {@code plan.md}.
   */
  @Test
  public void executeDoesNotFlagUntouchedFilesWithStalePathReferences() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (TestClaudeTool scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Merge-base state:
        //   old-tools/helper.sh — the file that will be renamed on main
        //   plan.md — a planning file that references old-tools as text (never touched by feature)
        Files.createDirectories(repoDir.resolve("old-tools"));
        Files.writeString(repoDir.resolve("old-tools").resolve("helper.sh"), "#!/bin/sh\nhelp");
        Files.writeString(repoDir.resolve("plan.md"),
          "Migrate callers from old-tools to new-tools.");
        TestUtils.runGit(repoDir, "add", "old-tools/", "plan.md");
        TestUtils.runGit(repoDir, "commit", "-m", "add old-tools and plan.md");

        // Feature branch: only adds a new unrelated file.
        // Does NOT touch plan.md or old-tools/helper.sh.
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("feature-work.txt"), "new feature work");
        TestUtils.runGit(repoDir, "add", "feature-work.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "add feature-work.txt");

        // Target branch (main): renames old-tools/helper.sh to new-tools/helper.sh.
        // plan.md is NOT updated — it still contains old-tools as text.
        // This triggers both the tracked-path check (old-tools/helper.sh) and the
        // content-reference check (plan.md found by git grep for "old-tools").
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.createDirectories(repoDir.resolve("new-tools"));
        TestUtils.runGit(repoDir, "mv", "old-tools/helper.sh", "new-tools/helper.sh");
        TestUtils.runGit(repoDir, "commit", "-m", "rename old-tools/helper.sh to new-tools/helper.sh");

        // Switch to feature branch — plan.md and old-tools/helper.sh are both untouched by
        // the feature's commits. The tracked-path check correctly flags old-tools/helper.sh
        // (true positive). The content-reference check must NOT flag plan.md (false positive
        // removed by the fix).
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        // The tracked-path conflict for old-tools/helper.sh is a true positive — correctly blocked.
        requireThat(result, "result").contains("old-tools/helper.sh");
        // plan.md must NOT appear in the content-reference section: it was not modified by the
        // feature's commits, so it is excluded by the fix even though git grep finds it.
        requireThat(result, "result").doesNotContain("plan.md");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that content references to old paths are not flagged as conflicts when the old path
   * is still tracked in the feature branch.
   * <p>
   * <b>Edge case being fixed:</b> {@code git grep} performs a literal string search for the old path
   * prefix (e.g., "old-tools/") in all tracked files. This can produce false positives when documentation
   * or comments reference the old path as text. The fix skips content-reference validation when the old
   * path is still tracked in the current branch — if the file exists, references to it remain valid.
   * <p>
   * Scenario: the merge base has {@code old-tools/helper.sh}. The target branch renames it to
   * {@code new-tools/helper.sh}. The feature branch modifies {@code README.md} to add documentation
   * referencing the OLD path (e.g., "See old-tools/helper.sh for implementation") but does NOT
   * delete {@code old-tools/helper.sh} — the old path remains tracked in the feature branch.
   * <p>
   * Before the fix, {@code git grep} would find README.md containing "old-tools" and incorrectly
   * flag it as a content-reference conflict. With the fix, when the old path is still tracked,
   * references to it remain valid (the file exists), so no content-reference conflict is reported.
   * The tracked-path conflict for {@code old-tools/helper.sh} is still correctly reported.
   */
  @Test
  public void executeDoesNotFlagContentReferenceWhenOldPathStillTracked() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (TestClaudeTool scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Merge-base state: old-tools/helper.sh and README.md
        Files.createDirectories(repoDir.resolve("old-tools"));
        Files.writeString(repoDir.resolve("old-tools").resolve("helper.sh"), "#!/bin/sh\nhelp");
        Files.writeString(repoDir.resolve("README.md"), "# Project\n\nInitial documentation.");
        TestUtils.runGit(repoDir, "add", "old-tools/", "README.md");
        TestUtils.runGit(repoDir, "commit", "-m", "add old-tools and README");

        // Feature branch: modifies README.md to add a comment referencing the OLD path,
        // but does NOT delete old-tools/helper.sh (it's still tracked by the feature)
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("README.md"),
          "# Project\n\nSee old-tools/helper.sh for the implementation details.");
        TestUtils.runGit(repoDir, "add", "README.md");
        TestUtils.runGit(repoDir, "commit", "-m", "document old-tools location");

        // Target branch (main): renames old-tools/helper.sh to new-tools/helper.sh
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.createDirectories(repoDir.resolve("new-tools"));
        TestUtils.runGit(repoDir, "mv", "old-tools/helper.sh", "new-tools/helper.sh");
        TestUtils.runGit(repoDir, "commit", "-m", "rename old-tools/helper.sh to new-tools/helper.sh");

        // Switch to feature branch and attempt rebase
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        // After the fix, the content-reference check does NOT flag README.md (false positive removed
        // because old-tools/helper.sh is still tracked in the feature branch, so the reference is valid).
        // However, the tracked-path check still flags old-tools/helper.sh as a conflict (true positive).
        requireThat(result, "result").contains("Tracked files at renamed");
        requireThat(result, "result").contains("old-tools/helper.sh");
        // README.md should NOT appear in the content-reference section (false positive removed)
        requireThat(result, "result").doesNotContain("Files with content references");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that content references ARE flagged when the old path is NOT tracked and the file
   * was modified by the feature.
   * <p>
   * Scenario: the merge base has {@code old-tools/helper.sh}. The target branch renames it to
   * {@code new-tools/helper.sh}. The feature branch deletes {@code old-tools/helper.sh} AND
   * modifies {@code README.md} to reference the old path. Since the old path is no longer tracked,
   * the content reference in the modified file IS a conflict.
   */
  @Test
  public void executeDoesFlagContentReferenceWhenOldPathNotTracked() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (TestClaudeTool scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Merge-base state: old-tools/helper.sh and README.md
        Files.createDirectories(repoDir.resolve("old-tools"));
        Files.writeString(repoDir.resolve("old-tools").resolve("helper.sh"), "#!/bin/sh\nhelp");
        Files.writeString(repoDir.resolve("README.md"), "# Project\n\nInitial documentation.");
        TestUtils.runGit(repoDir, "add", "old-tools/", "README.md");
        TestUtils.runGit(repoDir, "commit", "-m", "add old-tools and README");

        // Feature branch: deletes old-tools/ AND adds reference to it in README.md
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        TestUtils.runGit(repoDir, "rm", "-r", "old-tools/");
        Files.writeString(repoDir.resolve("README.md"),
          "# Project\n\nSee old-tools/helper.sh for implementation (now removed).");
        TestUtils.runGit(repoDir, "add", "README.md");
        TestUtils.runGit(repoDir, "commit", "-m", "remove old-tools and document it");

        // Target branch (main): renames old-tools/helper.sh to new-tools/helper.sh
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.createDirectories(repoDir.resolve("new-tools"));
        TestUtils.runGit(repoDir, "mv", "old-tools/helper.sh", "new-tools/helper.sh");
        TestUtils.runGit(repoDir, "commit", "-m", "rename old-tools/helper.sh to new-tools/helper.sh");

        // Switch to feature branch and attempt rebase
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        // Old path is NOT tracked, so content reference in README.md (which was modified) IS flagged
        requireThat(result, "result").contains("Files with content references");
        requireThat(result, "result").contains("README.md");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that files NOT modified by the feature are not flagged for content references,
   * even if they contain references to the old path.
   * <p>
   * Scenario: the merge base has {@code old-tools/helper.sh} and {@code NOTES.md} (which already
   * references "old-tools/"). The target branch renames the helper script. The feature branch makes
   * unrelated changes (adds a new file) but does NOT modify NOTES.md. Since NOTES.md was not
   * modified by the feature, it should not be flagged for content references.
   */
  @Test
  public void executeDoesNotFlagUnmodifiedFilesWithContentReferences() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (TestClaudeTool scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Merge-base state: old-tools/helper.sh and NOTES.md (which references old-tools/)
        Files.createDirectories(repoDir.resolve("old-tools"));
        Files.writeString(repoDir.resolve("old-tools").resolve("helper.sh"), "#!/bin/sh\nhelp");
        Files.writeString(repoDir.resolve("NOTES.md"),
          "# Notes\n\nThe old-tools/helper.sh script was moved.");
        TestUtils.runGit(repoDir, "add", "old-tools/", "NOTES.md");
        TestUtils.runGit(repoDir, "commit", "-m", "add old-tools and notes");

        // Feature branch: adds a new file, does NOT modify NOTES.md
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("NEW_FEATURE.md"), "# New Feature");
        TestUtils.runGit(repoDir, "add", "NEW_FEATURE.md");
        TestUtils.runGit(repoDir, "commit", "-m", "add new feature");

        // Target branch (main): renames old-tools/helper.sh to new-tools/helper.sh
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.createDirectories(repoDir.resolve("new-tools"));
        TestUtils.runGit(repoDir, "mv", "old-tools/helper.sh", "new-tools/helper.sh");
        TestUtils.runGit(repoDir, "commit", "-m", "rename old-tools/helper.sh to new-tools/helper.sh");

        // Switch to feature branch and attempt rebase
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        // NOTES.md was NOT modified by the feature, so it should not be flagged
        // Only the tracked-path conflict for old-tools/helper.sh should be reported
        requireThat(result, "result").contains("Tracked files at renamed");
        requireThat(result, "result").contains("old-tools/helper.sh");
        requireThat(result, "result").doesNotContain("NOTES.md");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that .cat/ files are skipped from tracked-path checks, even if they reference old paths.
   * <p>
   * Scenario: the merge base has {@code old-tools/helper.sh} and {@code .cat/issues/notes.md} which
   * references the old path. The target branch renames the helper script. The feature branch is
   * unrelated. The .cat/ file should not trigger a conflict since .cat/ files are planning artifacts
   * and stale path references there are acceptable.
   */
  @Test
  public void executeSkipsCatDirectoryFromPathChecks() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (TestClaudeTool scope = new TestClaudeTool(repoDir, repoDir))
    {
      try
      {
        // Merge-base state: old-tools/helper.sh and .cat/issues/notes.md
        Files.createDirectories(repoDir.resolve("old-tools"));
        Files.writeString(repoDir.resolve("old-tools").resolve("helper.sh"), "#!/bin/sh\nhelp");
        Files.createDirectories(repoDir.resolve(".cat").resolve("issues"));
        Files.writeString(repoDir.resolve(".cat").resolve("issues").resolve("notes.md"),
          "# Issue Notes\n\nSee old-tools/helper.sh for details.");
        TestUtils.runGit(repoDir, "add", "old-tools/", ".cat/");
        TestUtils.runGit(repoDir, "commit", "-m", "add old-tools and planning notes");

        // Feature branch: makes unrelated changes
        TestUtils.runGit(repoDir, "checkout", "-b", "feature");
        Files.writeString(repoDir.resolve("README.md"), "# Project");
        TestUtils.runGit(repoDir, "add", "README.md");
        TestUtils.runGit(repoDir, "commit", "-m", "add readme");

        // Target branch (main): renames old-tools/helper.sh to new-tools/helper.sh
        TestUtils.runGit(repoDir, "checkout", "main");
        Files.createDirectories(repoDir.resolve("new-tools"));
        TestUtils.runGit(repoDir, "mv", "old-tools/helper.sh", "new-tools/helper.sh");
        TestUtils.runGit(repoDir, "commit", "-m", "rename old-tools/helper.sh to new-tools/helper.sh");

        // Switch to feature branch and attempt rebase
        TestUtils.runGit(repoDir, "checkout", "feature");

        GitRebase cmd = new GitRebase(scope, repoDir);
        String result = cmd.execute("main");

        // .cat/issues/notes.md should NOT be flagged (planning artifacts are exempt)
        // Only the tracked-path conflict for old-tools/helper.sh should be reported
        requireThat(result, "result").contains("Tracked files at renamed");
        requireThat(result, "result").contains("old-tools/helper.sh");
        requireThat(result, "result").doesNotContain(".cat/issues/notes.md");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }
}
