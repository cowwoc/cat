/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.bash.BlockMainRebase;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockMainRebase}.
 * <p>
 * Tests verify that the handler blocks git rebase on main and git checkout in main worktree,
 * using {@link io.github.cowwoc.cat.hooks.WorktreeContext#forSession} for lock-based context
 * determination.
 * <p>
 * Lock and worktree files are created via the scope's {@code getCatWorkPath()} to match
 * the external CAT storage location used by the production code.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class BlockMainRebaseTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Verifies that git rebase is blocked when the session has no lock (main worktree context).
   * <p>
   * Without a lock file, the handler falls back to checking the project directory, which is on main.
   * Rebasing on main must be blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseOnMainIsBlockedWhenNoLock() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath.toString(), SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      // No lock file — session has no active worktree, so commands run in main context

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("REBASE ON MAIN BLOCKED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that git rebase is allowed when the session has an active worktree lock.
   * <p>
   * When the session holds a lock pointing to a worktree, the handler determines the current
   * branch from the worktree directory. Since the worktree is on a feature branch (not main),
   * rebase must be allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseIsAllowedWhenSessionHasWorktreeLock() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git rebase main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo.toString(), SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      // Create a worktree on a feature branch
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);

      // Create the lock file so the handler resolves to the worktree context
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BlockMainRebase handler = new BlockMainRebase(scope);

      BashHandler.Result result = handler.check(scope);

      // The worktree is on feature branch, not main — rebase must be allowed
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that checkout in main worktree is blocked when no lock exists for the session.
   * <p>
   * When the session has no active worktree lock, the handler treats the session as operating
   * in the main workspace context and blocks branch checkouts.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void checkoutInMainWorktreeIsBlockedWhenNoLock() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath.toString(), SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      // No lock — session is in main context; checkout must be blocked

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that checkout is allowed when the session has an active worktree lock.
   * <p>
   * When the session holds a lock, the handler recognizes the session is in a task worktree
   * context (not main) and allows checkout operations.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void checkoutIsAllowedWhenSessionHasWorktreeLock() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git checkout -b new-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo.toString(), SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BlockMainRebase handler = new BlockMainRebase(scope);
      // The session has a lock — it's in an issue worktree context, not main

      BashHandler.Result result = handler.check(scope);

      // -b is a flag, not a branch name — even in main context this would be a flag checkout
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that an empty session ID throws IllegalArgumentException (fail-fast).
   *
   * @throws IOException if test setup fails
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id.*")
  public void emptySessionIdThrowsForCheckout() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    try
    {
      // Empty session ID omits session_id from payload, causing AbstractClaudeHook to throw
      TestUtils.bashHook("git checkout feature-branch", projectPath.toString(), "",
        projectPath, pluginRoot, projectPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that an empty session ID throws IllegalArgumentException for rebase commands.
   *
   * @throws IOException if test setup fails
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id.*")
  public void emptySessionIdThrowsForRebase() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    try
    {
      // Empty session ID omits session_id from payload, causing AbstractClaudeHook to throw
      TestUtils.bashHook("git rebase origin/main", projectPath.toString(), "",
        projectPath, pluginRoot, projectPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that git commands unrelated to checkout/rebase are always allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonCheckoutNonRebaseCommandIsAllowed() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git log --oneline -10";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath.toString(), SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
