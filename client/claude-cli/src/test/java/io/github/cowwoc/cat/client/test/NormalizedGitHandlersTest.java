/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.bash.BlockMainRebase;
import io.github.cowwoc.cat.claude.hook.bash.BlockMergeCommits;
import io.github.cowwoc.cat.claude.hook.bash.BlockReflogDestruction;
import io.github.cowwoc.cat.claude.hook.bash.ValidateGitFilterBranch;
import io.github.cowwoc.cat.claude.hook.bash.ValidateGitOperations;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Regression tests for handlers that now consume normalized git commands.
 */
public final class NormalizedGitHandlersTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-normalized-git-test";

  /**
   * Verifies that rebase-on-main blocking still works when git uses a {@code -C} prefix.
   */
  @Test
  public void blockMainRebaseRecognizesGlobalFlags() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    String command = "git -C " + projectPath + " rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, "session-1",
      projectPath, pluginRoot, projectPath))
    {
      BashHandler.Result result = new BlockMainRebase(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that merge-commit blocking still works with a {@code -C} prefix.
   */
  @Test
  public void blockMergeCommitsRecognizesGlobalFlags() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git -C /tmp/repo merge feature-x",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockMergeCommits(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reflog destruction blocking still works with a {@code -C} prefix.
   */
  @Test
  public void blockReflogDestructionRecognizesGlobal() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git -C /tmp/repo reflog expire --expire=now --all",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockReflogDestruction(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reflog expire remains blocked with work-tree prefixed form.
   */
  @Test
  public void blockReflogDestructionRecognizesWorkTree() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook(
      "git --work-tree=/tmp/repo reflog expire --expire=now --all", Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockReflogDestruction(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that gc prune-all remains blocked with work-tree prefixed form.
   */
  @Test
  public void blockReflogDestructionRecognizesWorkTre2() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git --work-tree=/tmp/repo gc --prune=all",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockReflogDestruction(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that gc prune-now is blocked by reflog-destruction guard.
   */
  @Test
  public void blockReflogDestructionBlocksGcPruneNow() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git gc --prune=now",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockReflogDestruction(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that acknowledged gc prune-now is allowed.
   */
  @Test
  public void blockReflogDestructionAllowsAcknowledged() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git gc --prune=now # ACKNOWLEDGED: gc prune",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockReflogDestruction(scope).check();

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that history-rewrite validation still works with a {@code -C} prefix.
   */
  @Test
  public void validateGitFilterBranchRecognizesGlobal() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git -C /tmp/repo filter-branch --all",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new ValidateGitFilterBranch(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reset-hard blocking still works with a {@code -C} prefix.
   */
  @Test
  public void validateGitOperationsRecognizesGlobal() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git -C /tmp/repo reset --hard HEAD~1",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new ValidateGitOperations(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reset-hard acknowledgment must appear on the reset command segment.
   */
  @Test
  public void validateGitOperationsDoesNotUseCross() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("echo '# ACKNOWLEDGED' && git reset --hard HEAD",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new ValidateGitOperations(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that unrelated text containing {@code worktrees} does not bypass reset-hard blocking.
   */
  @Test
  public void validateGitOperationsDoesNotUseWorktrees() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("echo worktrees && git reset --hard HEAD",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new ValidateGitOperations(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reset-hard is allowed when the command runs from the active issue worktree.
   */
  @Test
  public void validateGitOperationsAllowsResetHardIn() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git reset --hard HEAD", mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      try (TestClaudeHook worktreeScope = TestUtils.bashHook("git reset --hard HEAD", worktree,
        SESSION_ID, mainRepo, pluginRoot, mainRepo))
      {
        BashHandler.Result result = new ValidateGitOperations(worktreeScope).check();

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that an acknowledged reset-hard command is allowed in issue worktree context.
   */
  @Test
  public void validateGitOperationsContinuesAfter() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    try (TestClaudeHook scope = TestUtils.bashHook(
      "git reset --hard '# ACKNOWLEDGED' && git reset --hard HEAD",
      mainRepo, SESSION_ID, mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      try (TestClaudeHook worktreeScope = TestUtils.bashHook(
        "git reset --hard '# ACKNOWLEDGED' && git reset --hard HEAD", worktree, SESSION_ID,
        mainRepo, pluginRoot, mainRepo))
      {
        BashHandler.Result result = new ValidateGitOperations(worktreeScope).check();

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that reset-hard is blocked when git scope is overridden outside the issue worktree.
   */
  @Test
  public void validateGitOperationsBlocksResetHard() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git reset --hard HEAD", mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path relativeToMain = worktree.relativize(mainRepo);

      try (TestClaudeHook worktreeScope = TestUtils.bashHook(
        "git -C " + relativeToMain + " reset --hard HEAD", worktree, SESSION_ID, mainRepo, pluginRoot,
        mainRepo))
      {
        BashHandler.Result result = new ValidateGitOperations(worktreeScope).check();

        requireThat(result.blocked(), "blocked").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that quoted ACK text in arguments does not bypass reset-hard blocking.
   */
  @Test
  public void validateGitOperationsDoesNotTreatQuoted() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git reset --hard '# ACKNOWLEDGED'",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new ValidateGitOperations(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reset-hard with work-tree override outside the issue worktree is blocked.
   */
  @Test
  public void validateGitOperationsBlocksResetHardWith()
    throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git reset --hard HEAD", mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      try (TestClaudeHook worktreeScope = TestUtils.bashHook(
        "git --work-tree " + mainRepo + " reset --hard HEAD", worktree, SESSION_ID, mainRepo,
        pluginRoot, mainRepo))
      {
        BashHandler.Result result = new ValidateGitOperations(worktreeScope).check();

        requireThat(result.blocked(), "blocked").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies compact work-tree override is blocked outside issue worktree.
   */
  @Test
  public void validateGitOperationsBlocksResetHardWit2()
    throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git reset --hard HEAD", mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      try (TestClaudeHook worktreeScope = TestUtils.bashHook(
        "git --work-tree=" + mainRepo + " reset --hard HEAD", worktree, SESSION_ID, mainRepo,
        pluginRoot, mainRepo))
      {
        BashHandler.Result result = new ValidateGitOperations(worktreeScope).check();

        requireThat(result.blocked(), "blocked").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that mixed overrides are blocked when git-dir escapes the issue worktree.
   */
  @Test
  public void validateGitOperationsBlocksResetHardWit3() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git reset --hard HEAD", mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      try (TestClaudeHook worktreeScope = TestUtils.bashHook(
        "git --work-tree " + worktree + " --git-dir " + mainRepo.resolve(".git") + " reset --hard HEAD",
        worktree, SESSION_ID, mainRepo, pluginRoot, mainRepo))
      {
        BashHandler.Result result = new ValidateGitOperations(worktreeScope).check();

        requireThat(result.blocked(), "blocked").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies compact git-dir override is blocked when it escapes issue worktree.
   */
  @Test
  public void validateGitOperationsBlocksResetHardWit4() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("ngr-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git reset --hard HEAD", mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      try (TestClaudeHook worktreeScope = TestUtils.bashHook(
        "git --work-tree=" + worktree + " --git-dir=" + mainRepo.resolve(".git") + " reset --hard HEAD",
        worktree, SESSION_ID, mainRepo, pluginRoot, mainRepo))
      {
        BashHandler.Result result = new ValidateGitOperations(worktreeScope).check();

        requireThat(result.blocked(), "blocked").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
