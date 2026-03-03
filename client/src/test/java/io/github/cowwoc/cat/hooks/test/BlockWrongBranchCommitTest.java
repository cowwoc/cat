/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.CatMetadata;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.BlockWrongBranchCommit;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
   * Creates a {@code cat-branch-point} file in the git directory of the given repository.
   * <p>
   * This simulates an issue worktree created by {@code /cat:work}. The file content is
   * a representative fork-point commit hash.
   *
   * @param repoDir the repository directory
   * @throws IOException if the git command fails or file creation fails
   */
  private static void createCatBranchPointFile(Path repoDir) throws IOException
  {
    String gitDirPath = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "--git-dir");
    Path gitDir;
    if (Paths.get(gitDirPath).isAbsolute())
      gitDir = Paths.get(gitDirPath);
    else
      gitDir = repoDir.resolve(gitDirPath);
    Files.writeString(gitDir.resolve(CatMetadata.BRANCH_POINT_FILE), "abc1234567890abcdef1234567890abcdef123456");
  }

  /**
   * Verifies that non-commit commands are allowed without branch checks.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonCommitCommandIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("bwbc-test-");
    try
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      BashHandler.Result result = handler.check("git status", tempDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a git commit outside a CAT worktree (no cat-branch-point marker) is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commitOutsideCatWorktreeIsAllowed() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("v2.1");
    try
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // No cat-branch-point file means not a CAT worktree
      BashHandler.Result result = handler.check(
        "git commit -m \"feature: add something\"", tempDir.toString(), null, null, SESSION_ID);

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
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-my-issue");
      try
      {
        createCatBranchPointFile(worktreeDir);
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Working in the worktree directory — current branch is "2.1-my-issue"
        BashHandler.Result result = handler.check(
          "git commit -m \"feature: implement the feature\"",
          worktreeDir.toString(), null, null, SESSION_ID);

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
   * The cat-branch-point marker exists but the current branch does not match the git dir name.
   * This simulates a subagent that checked out a different branch inside the worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commitInWorktreeOnWrongBranchIsBlocked() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // Create a second branch "wrong-branch" in mainRepo
      TestUtils.runGit(mainRepo, "branch", "wrong-branch");
      // Create a worktree for "2.1-wrong-test" and then check out "wrong-branch" inside it
      Path wrongWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-wrong-test");
      try
      {
        createCatBranchPointFile(wrongWorktreeDir);

        // Checkout a different branch inside the worktree — current branch "wrong-branch" but
        // git dir name is "2.1-wrong-test"
        TestUtils.runGit(wrongWorktreeDir, "checkout", "wrong-branch");

        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        BashHandler.Result result = handler.check(
          "git commit -m \"feature: wrong branch commit\"",
          wrongWorktreeDir.toString(), null, null, SESSION_ID);

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
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-cd-issue");
      try
      {
        createCatBranchPointFile(worktreeDir);
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // cwd is mainRepo but command cd's into the worktree
        String command = "cd " + worktreeDir + " && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(command, mainRepo.toString(), null, null, SESSION_ID);

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
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-push-test");
      try
      {
        createCatBranchPointFile(worktreeDir);
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // git push — not a commit
        BashHandler.Result result = handler.check(
          "git push origin 2.1-push-test",
          worktreeDir.toString(), null, null, SESSION_ID);

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
    try
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // This will throw IOException because tempDir is not a git repo
      BashHandler.Result result = handler.check(
        "git commit -m \"feature: something\"", tempDir.toString(), null, null, SESSION_ID);

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
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // Create two worktrees: one we'll end up in (correct), one we start from (wrong)
      Path correctWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-correct-issue");
      Path wrongWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-wrong-issue");
      try
      {
        createCatBranchPointFile(correctWorktreeDir);
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Command starts in wrong worktree but ends in the correct one
        String command = "cd " + wrongWorktreeDir + " && cd " + correctWorktreeDir +
          " && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(command, mainRepo.toString(), null, null, SESSION_ID);

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
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      TestUtils.runGit(mainRepo, "branch", "wrong-amend");
      Path wrongWorktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-amend-test");
      try
      {
        createCatBranchPointFile(wrongWorktreeDir);
        TestUtils.runGit(wrongWorktreeDir, "checkout", "wrong-amend");
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        BashHandler.Result result = handler.check(
          "git commit --amend --no-edit",
          wrongWorktreeDir.toString(), null, null, SESSION_ID);

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
   * Even if a {@code cat-branch-point} marker file exists, the handler must allow the commit when
   * the git directory structure does not match the expected {@code worktrees/<branch>} pattern.
   * This guards against false-positive blocking in non-worktree git configurations.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commitInNonWorktreeGitStructureIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try
    {
      // Add a cat-branch-point file directly in .git/ (not under .git/worktrees/<name>/)
      // This simulates a repository where the file exists but the structure is not a worktree
      Path gitDir = mainRepo.resolve(".git");
      Files.writeString(gitDir.resolve(CatMetadata.BRANCH_POINT_FILE),
        "abc1234567890abcdef1234567890abcdef123456");

      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // Main repo: .git dir parent is mainRepo itself, which is NOT named "worktrees"
      BashHandler.Result result = handler.check(
        "git commit -m \"feature: something\"",
        mainRepo.toString(), null, null, SESSION_ID);

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
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // Create two sibling worktrees: cwd starts in "sibling-a", cd relative to "sibling-b"
      Path siblingA = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-sibling-a");
      Path siblingB = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-sibling-b");
      try
      {
        createCatBranchPointFile(siblingB);

        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Relative cd from siblingA to siblingB using "../sibling-b"
        String command = "cd ../2.1-sibling-b && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(command, siblingA.toString(), null, null, SESSION_ID);

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
    try
    {
      BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

      // Command with whitespace-only cd target — should fall back to cwd (mainRepo, not a CAT worktree)
      BashHandler.Result result = handler.check(
        "cd   && git commit -m \"feature: something\"",
        mainRepo.toString(), null, null, SESSION_ID);

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
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      TestUtils.runGit(mainRepo, "branch", "wrong-branch");
      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-flag-test");
      try
      {
        createCatBranchPointFile(worktreeDir);
        TestUtils.runGit(worktreeDir, "checkout", "wrong-branch");
        BlockWrongBranchCommit handler = new BlockWrongBranchCommit();

        // Test: git commit -a
        BashHandler.Result result1 = handler.check(
          "git commit -a", worktreeDir.toString(), null, null, SESSION_ID);
        requireThat(result1.blocked(), "blockedForDashA").isTrue();

        // Test: git commit -am "message"
        BashHandler.Result result2 = handler.check(
          "git commit -am \"feature: add something\"",
          worktreeDir.toString(), null, null, SESSION_ID);
        requireThat(result2.blocked(), "blockedForDashAm").isTrue();

        // Test: git commit --message="message"
        BashHandler.Result result3 = handler.check(
          "git commit --message=\"feature: long form\"",
          worktreeDir.toString(), null, null, SESSION_ID);
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
}
