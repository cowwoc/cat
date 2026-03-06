/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.VerifyStateInCommit;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for VerifyStateInCommit.
 */
public final class VerifyStateInCommitTest
{
  /**
   * Verifies that non-commit commands are allowed without any checks.
   */
  @Test
  public void allowsNonCommitCommands() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(TestUtils.bashInput("git status", tempDir.toString(), "test-session"));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-bugfix/feature commits are allowed without STATE.md checks.
   */
  @Test
  public void allowsNonBugfixFeatureCommits() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"refactor: clean up code\"", tempDir.toString(), "test-session"));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that --amend commits are allowed without STATE.md checks.
   */
  @Test
  public void allowsAmendCommits() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit --amend -m \"feature: updated feature\"", tempDir.toString(), "test-session"));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that bugfix commits in a CAT worktree are blocked when STATE.md is not staged.
   */
  @Test
  public void blocksWhenStateMdNotStaged() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-fix-thing");
      try
      {
        // Stage a file that is NOT STATE.md
        Files.writeString(worktreeDir.resolve("some-file.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "some-file.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"bugfix: fix the thing\"", worktreeDir.toString(), "test-session"));

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("STATE.md not included");
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that a warning is issued when STATE.md is staged but does not contain "completed" status.
   */
  @Test
  public void warnsWhenStateMdNotCompleted() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-new-feature");
      try
      {
        // Create and stage STATE.md with "in-progress" status
        Path issueDir = worktreeDir.resolve(".claude").resolve("cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "status: in-progress\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add new feature\"", worktreeDir.toString(), "test-session"));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("does not contain 'completed'");
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that when a command contains "cd /path", the handler uses that path to detect a CAT worktree,
   * even when the working directory itself is not in a worktree.
   */
  @Test
  public void cdPathUsedForWorktreeDetection() throws IOException
  {
    // mainRepo is a regular non-worktree directory (session's working directory)
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // worktreeDir is a real CAT worktree with git dir ending in "worktrees/<branch>"
      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-fix-something");
      try
      {
        // Stage a file in worktreeDir that is NOT STATE.md
        Files.writeString(worktreeDir.resolve("some-file.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "some-file.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        // workingDirectory is mainRepo (not a CAT worktree), but command has "cd worktreeDir"
        // The handler should detect worktreeDir as the effective directory via cd extraction
        String command = "cd " + worktreeDir + " && git commit -m \"bugfix: fix something\"";
        BashHandler.Result result = handler.check(TestUtils.bashInput(command, mainRepo.toString(), "test-session"));

        // Since worktreeDir is a CAT worktree and STATE.md is not staged, it should be blocked
        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("STATE.md not included");
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that when a command has multiple cd statements, the last cd path is used as the effective directory.
   */
  @Test
  public void lastCdPathUsedWhenMultipleCdStatements() throws IOException
  {
    // mainRepo is used to create a real CAT worktree (firstDir)
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    // secondDir is a regular repo (NOT a CAT worktree) — the last cd target
    Path secondDir = TestUtils.createTempGitRepo("second-branch");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // firstDir is a real CAT worktree but NOT the last cd target
      Path firstDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-first-issue");
      try
      {
        VerifyStateInCommit handler = new VerifyStateInCommit();

        // Command cd's to firstDir (CAT worktree) then to secondDir (regular repo, not a worktree)
        // The last cd (secondDir) should be used, so not in a CAT worktree → allowed
        String command = "cd " + firstDir + " && cd " + secondDir +
          " && git commit -m \"bugfix: fix something\"";
        BashHandler.Result result = handler.check(TestUtils.bashInput(command, firstDir.toString(), "test-session"));

        // secondDir is not a CAT worktree, so no STATE.md check → allowed
        requireThat(result.blocked(), "blocked").isFalse();
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", firstDir.toString());
        TestUtils.deleteDirectoryRecursively(firstDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(secondDir);
    }
  }

  /**
   * Verifies that when a command has no cd statement, the working directory is used for worktree detection.
   */
  @Test
  public void fallsBackToWorkingDirectoryWhenNoCd() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try
    {
      // Regular repo: git dir parent is not "worktrees" → not a CAT worktree → should allow
      VerifyStateInCommit handler = new VerifyStateInCommit();

      // No cd in command — working directory is used
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"bugfix: fix something\"", tempDir.toString(), "test-session"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that commits in the main workspace (which has a .claude/cat directory) are not blocked
   * by the STATE.md check.
   */
  @Test
  public void allowsMainWorkspaceCommitsWithClaudeCatDirectory() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try
    {
      // Create .claude/cat directory (present in main workspace but not a CAT worktree)
      // The main workspace has .claude/cat for retrospectives/issues but its git dir parent
      // is not "worktrees", so it is not treated as a CAT worktree
      Path claudeCat = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(claudeCat);

      Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
      TestUtils.runGit(tempDir, "add", "Foo.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"bugfix: fix something\"", tempDir.toString(), "test-session"));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that feature commits outside a CAT worktree are allowed without STATE.md checks.
   */
  @Test
  public void allowsNonWorktreeCommits() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try
    {
      // Regular repo: git dir parent is not "worktrees" → not a CAT worktree
      Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
      TestUtils.runGit(tempDir, "add", "Foo.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"feature: add feature\"", tempDir.toString(), "test-session"));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
