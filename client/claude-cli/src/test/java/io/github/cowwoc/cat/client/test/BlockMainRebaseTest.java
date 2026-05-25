/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.bash.BlockMainRebase;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockMainRebase}.
 * <p>
 * Tests verify that the handler blocks git rebase on main and git checkout in main worktree,
 * using {@link io.github.cowwoc.cat.tool.util.WorktreeContext#forSession} for lock-based context
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
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      // No lock file — session has no active worktree, so commands run in main context

      BashHandler.Result result = handler.check();

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
  public void rebaseIsAllowedWhenSessionHasWorktree() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git rebase main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      // Create a worktree on a feature branch
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);

      // Create the lock file so the handler resolves to the worktree context
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BlockMainRebase handler = new BlockMainRebase(scope);

      BashHandler.Result result = handler.check();

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
   * Verifies that {@code git -C <project>} cannot bypass main-worktree rebase blocking when the
   * session also has an active issue worktree lock.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseOnMainWithGitGlobalDirectoryIs() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git -C " + mainRepo + " rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BashHandler.Result result = new BlockMainRebase(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("REBASE ON MAIN BLOCKED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that {@code --git-dir <project>/.git} cannot bypass rebase blocking on main.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseOnMainWithGitDirIsBlockedWhen() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git --git-dir " + mainRepo.resolve(".git") + " rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BashHandler.Result result = new BlockMainRebase(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("REBASE ON MAIN BLOCKED");
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
  public void checkoutInMainWorktreeIsBlockedWhenNo() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      // No lock — session is in main context; checkout must be blocked

      BashHandler.Result result = handler.check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that switch in main worktree is blocked when no lock exists.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void switchInMainWorktreeIsBlockedWhenNoLock() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git switch feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
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
  public void checkoutIsAllowedWhenSessionHasWorktree() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git checkout -b new-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BlockMainRebase handler = new BlockMainRebase(scope);
      // The session has a lock — it's in an issue worktree context, not main

      BashHandler.Result result = handler.check();

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
   * Verifies that {@code git -C <project>} cannot bypass main-worktree checkout blocking when the
   * session also has an active issue worktree lock.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void checkoutInMainWithGitGlobalDirectoryIs() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git -C " + mainRepo + " checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BashHandler.Result result = new BlockMainRebase(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that {@code --git-dir <project>/.git} cannot bypass main-worktree checkout blocking.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void checkoutInMainWithGitDirIsBlockedWhen() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git --git-dir " + mainRepo.resolve(".git") + " checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, mainRepo, SESSION_ID,
      mainRepo, pluginRoot, mainRepo))
    {
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

      BashHandler.Result result = new BlockMainRebase(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
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
      TestUtils.bashHook("git checkout feature-branch", projectPath, "",
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
      TestUtils.bashHook("git rebase origin/main", projectPath, "",
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
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);

      BashHandler.Result result = handler.check();

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that checkout scoped to the main worktree using {@code -C} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void checkoutWithDashCToMainIsBlocked() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git -C " + projectPath + " checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that checkout scoped to main using quoted {@code -C} with spaces is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void checkoutWithQuotedDashCToMainWithSpaces() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    Path spacedMain = Files.createDirectories(projectPath.resolve("main dir"));
    String command = "git -C \"" + spacedMain + "\" checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that switch scoped to the main worktree using {@code -C} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void switchWithDashCToMainIsBlocked() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git -C " + projectPath + " switch feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that switch scoped to main via {@code --work-tree}/{@code --git-dir} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void switchWithGitDirAndWorkTreeOnMainIs() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git --git-dir=" + projectPath.resolve(".git") +
      " --work-tree=" + projectPath + " switch feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that checkout scoped to a subdirectory under main is blocked even with an active lock.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void checkoutWithDashCToMainSubdirectoryIs() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    Path mainSubdirectory = Files.createDirectories(projectPath.resolve("nested"));
    String command = "git -C " + mainSubdirectory + " checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that rebase scoped to main via {@code --git-dir}/{@code --work-tree} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseWithGitDirAndWorkTreeOnMainIs() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "git --git-dir=" + projectPath.resolve(".git") +
      " --work-tree=" + projectPath + " rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
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
   * Verifies that quoted scoped flags with spaces in a {@code cd ... && git ...} chain are still blocked on main.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseWithQuotedGitScopeInCdChainOnMain() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    Path spacedMain = Files.createDirectories(projectPath.resolve("main dir"));
    String command = "cd \"" + spacedMain + "\" && git --git-dir=\"" + projectPath.resolve(".git") +
      "\" --work-tree=\"" + spacedMain + "\" rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
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
   * Verifies that branch-detection failures return a warning (allow) result.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseWithUnresolvableGitDirReturns() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    Path missingGitDir = projectPath.resolve("missing").resolve(".git");
    String command = "git --git-dir=" + missingGitDir + " rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("Branch detection failed");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that rebase scoped via work-tree to a subdirectory under main is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rebaseWithWorkTreeInMainSubdirectoryIs() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    Path mainSubdirectory = Files.createDirectories(projectPath.resolve("nested"));
    String command = "git --work-tree=" + mainSubdirectory + " rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
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
   * Verifies that newline-separated checkout commands in main are blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void newlineSeparatedCheckoutInMainIsBlocked() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "echo prep\ngit checkout feature-branch";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that newline-separated rebase commands in main are blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void newlineSeparatedRebaseInMainIsBlocked() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("bmr-test-");
    String command = "echo prep\ngit rebase origin/main";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      BlockMainRebase handler = new BlockMainRebase(scope);
      BashHandler.Result result = handler.check();
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("REBASE ON MAIN BLOCKED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
