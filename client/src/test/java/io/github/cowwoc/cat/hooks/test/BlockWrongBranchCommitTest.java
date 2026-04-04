/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;

import io.github.cowwoc.cat.hooks.bash.BlockWrongBranchCommit;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link BlockWrongBranchCommit}.
 * <p>
 * Tests verify that git commit commands in a CAT worktree are blocked when the current branch
 * does not match the expected issue branch, and allowed when on the correct branch or outside a
 * CAT worktree.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class BlockWrongBranchCommitTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";

  /**
   * Verifies that non-commit commands are allowed without branch checks.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonCommitCommandIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("bwbc-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git status", tempDir.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a git commit outside a CAT worktree is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commitOutsideCatWorktreeIsAllowed() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // Regular repo: git dir parent is not "worktrees", so not a CAT worktree
      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit -m \"feature: add something\"", tempDir.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a git commit inside a CAT worktree on the correct branch is allowed.
   * <p>
   * The expected branch is derived from the worktree's git directory name, which matches the
   * current branch checked out in the worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commitInWorktreeOnCorrectBranchIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-my-issue");
      try
      {
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Working in the worktree directory — current branch is "2.1-my-issue"
        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: implement the feature\"",
            worktreeDir.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isFalse();
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
   * Verifies that a git commit inside a CAT worktree on the wrong branch is blocked.
   * <p>
   * The worktree git dir structure is correct but the current branch does not match the git dir name.
   * This simulates a subagent that checked out a different branch inside the worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commitInWorktreeOnWrongBranchIsBlocked() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // Create a second branch "wrong-branch" in mainRepo
      TestUtils.runGit(mainRepo, "branch", "wrong-branch");
      // Create a worktree for "2.1-wrong-test" and then check out "wrong-branch" inside it
      Path wrongWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-wrong-test");
      try
      {
        // Checkout a different branch inside the worktree — current branch "wrong-branch" but
        // git dir name is "2.1-wrong-test"
        TestUtils.runGit(wrongWorktreeDir, "checkout", "wrong-branch");

        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: wrong branch commit\"",
            wrongWorktreeDir.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("BLOCKED");
        requireThat(result.reason(), "reason").contains("git checkout");
        requireThat(result.reason(), "reason").contains("2.1-wrong-test");
        requireThat(result.reason(), "reason").contains("wrong-branch");
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", wrongWorktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(wrongWorktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that a command with {@code cd <worktree>} is handled correctly.
   * <p>
   * When the command contains {@code cd /worktree && git commit}, the handler must evaluate
   * the branch in the worktree directory, not the calling session's cwd.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void cdPathUsedForWorktreeDetection() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-cd-issue");
      try
      {
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // cwd is mainRepo but command cd's into the worktree
        String command = "cd " + worktreeDir + " && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, mainRepo.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isFalse();
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
   * Verifies that a git push command (not commit) is allowed without branch checks.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void gitPushCommandIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-push-test");
      try
      {
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // git push — not a commit
        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git push origin 2.1-push-test", worktreeDir.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isFalse();
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
   * Verifies that a commit is allowed when git commands fail (IOException path).
   * <p>
   * When git is not available or the working directory is not a valid git repository,
   * the handler must not block the commit — it should fail open.
   */
  @Test
  public void gitCommandFailureAllowsCommit() throws IOException
  {
    // Use a temp directory that is NOT a git repository
    Path tempDir = Files.createTempDirectory("bwbc-test-no-git-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // This will throw IOException because tempDir is not a git repo
      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit -m \"feature: something\"", tempDir.toString(), SESSION_ID, scope));

      // Should allow because IOException → fail-open
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when a command contains multiple cd directives, the last one is used.
   * <p>
   * Example: "cd /wrong && cd /correct && git commit" should evaluate branch in /correct.
   */
  @Test
  public void multipleCdCommandsUsesLastTarget() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // Create two worktrees: one we'll end up in (correct), one we start from (wrong)
      Path correctWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-correct-issue");
      Path wrongWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-wrong-issue");
      try
      {
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Command starts in wrong worktree but ends in the correct one
        String command = "cd " + wrongWorktreeDir + " && cd " + correctWorktreeDir +
          " && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, mainRepo.toString(), SESSION_ID, scope));

        // Should allow because the LAST cd target (correctWorktreeDir) is on the correct branch
        requireThat(result.blocked(), "blocked").isFalse();
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", correctWorktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(correctWorktreeDir);
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", wrongWorktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(wrongWorktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that git commit --amend inside a CAT worktree on the wrong branch is blocked.
   */
  @Test
  public void gitCommitAmendOnWrongBranchIsBlocked() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      TestUtils.runGit(mainRepo, "branch", "wrong-amend");
      Path wrongWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-amend-test");
      try
      {
        TestUtils.runGit(wrongWorktreeDir, "checkout", "wrong-amend");
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit --amend --no-edit", wrongWorktreeDir.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isTrue();
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", wrongWorktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(wrongWorktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that a git commit is allowed when the git dir parent is not named "worktrees".
   * <p>
   * A regular git repository (not a CAT worktree) has its git dir parent set to the repo root,
   * which is not named "worktrees". The handler must allow commits in these directories.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commitInNonWorktreeGitStructureIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // Main repo: .git dir parent is mainRepo itself, which is NOT named "worktrees"
      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit -m \"feature: something\"", mainRepo.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that a relative {@code cd} path in a command is resolved correctly.
   * <p>
   * When the command contains {@code cd ../sibling && git commit}, the relative path
   * must be resolved against the current working directory to determine the effective
   * directory for git operations.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void cdWithRelativePathIsResolved() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // Create two sibling worktrees: cwd starts in "sibling-a", cd relative to "sibling-b"
      Path siblingA = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-sibling-a");
      Path siblingB = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-sibling-b");
      try
      {
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Relative cd from siblingA to siblingB using "../sibling-b"
        String command = "cd ../2.1-sibling-b && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, siblingA.toString(), SESSION_ID, scope));

        // siblingB is a CAT worktree on the correct branch → should be allowed
        requireThat(result.blocked(), "blocked").isFalse();
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", siblingA.toString());
        TestUtils.deleteDirectoryRecursively(siblingA);
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", siblingB.toString());
        TestUtils.deleteDirectoryRecursively(siblingB);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that an empty {@code cd} target does not crash or produce wrong directory.
   * <p>
   * When a command contains {@code cd ""} or {@code cd  } (whitespace-only), the handler
   * must ignore the empty target and fall back to the original working directory.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void cdWithEmptyTargetIsIgnored() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // Command with whitespace-only cd target — should fall back to cwd (mainRepo, not a CAT worktree)
      BashHandler.Result result = handler.check(
        TestUtils.bashHook("cd   && git commit -m \"feature: something\"", mainRepo.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that various {@code git commit} flag combinations are detected correctly.
   * <p>
   * The COMMIT_PATTERN must match common variants such as {@code git commit -a},
   * {@code git commit -am}, and {@code git commit --message="..."}.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void gitCommitVariantFlagsAreDetected() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      TestUtils.runGit(mainRepo, "branch", "wrong-branch");
      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-flag-test");
      try
      {
        TestUtils.runGit(worktreeDir, "checkout", "wrong-branch");
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Test: git commit -a
        BashHandler.Result result1 = handler.check(
          TestUtils.bashHook("git commit -a", worktreeDir.toString(), SESSION_ID, scope));
        requireThat(result1.blocked(), "blockedForDashA").isTrue();

        // Test: git commit -am "message"
        BashHandler.Result result2 = handler.check(
          TestUtils.bashHook("git commit -am \"feature: add something\"", worktreeDir.toString(), SESSION_ID, scope));
        requireThat(result2.blocked(), "blockedForDashAm").isTrue();

        // Test: git commit --message="message"
        BashHandler.Result result3 = handler.check(
          TestUtils.bashHook("git commit --message=\"feature: long form\"",
            worktreeDir.toString(), SESSION_ID, scope));
        requireThat(result3.blocked(), "blockedForMessageFlag").isTrue();
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
   * Verifies that committing on the shared sanitized branch ({issue-branch-name}-sanitized) is allowed
   * even when inside a CAT issue worktree.
   * <p>
   * The instruction-builder-agent Step 6 pipeline creates an orphan branch named
   * {@code {issue-branch-name}-sanitized} (e.g. {@code 2.1-my-issue-sanitized}) and commits stripped
   * test-case files to it. Per-runner branches ({@code {issue-branch-name}-tcN-rM}) are then
   * branched from this sanitized branch. This branch name differs from the worktree's expected issue
   * branch but must not be blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void testIsolationBranchCommitIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-my-issue");
      try
      {
        TestUtils.runGit(worktreeDir, "checkout", "--orphan", "2.1-my-issue-sanitized");
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();
        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"test-runner workspace\"",
            worktreeDir.toString(), SESSION_ID, scope));
        requireThat(result.blocked(), "blocked").isFalse();
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
}
