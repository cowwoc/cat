/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.BlockMainRebase;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

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
 * Lock and worktree files are created via {@link JvmScope#getCatWorkPath()} to match
 * the external CAT storage location used by the production code.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class BlockMainRebaseTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Creates a lock file associating {@code sessionId} with {@code issueId}.
   *
   * @param scope the JVM scope providing the lock directory path
   * @param issueId the issue identifier (becomes the lock filename stem)
   * @param sessionId the session ID to embed in the lock content
   * @throws IOException if the lock file cannot be written
   */
  private static void writeLockFile(JvmScope scope, String issueId, String sessionId) throws IOException
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
    Files.createDirectories(lockDir);
    String content = """
      {"session_id": "%s", "worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), content);
  }

  /**
   * Creates the worktree directory for the given issue ID.
   *
   * @param scope the JVM scope providing the worktree base path
   * @param issueId the issue identifier
   * @return the created worktree directory path
   * @throws IOException if the directory cannot be created
   */
  private static Path createWorktreeDir(JvmScope scope, String issueId) throws IOException
  {
    Path worktreeDir = scope.getCatWorkPath().resolve("worktrees").resolve(issueId);
    Files.createDirectories(worktreeDir);
    return worktreeDir;
  }

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
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockMainRebase handler = new BlockMainRebase(scope);
      // No lock file — session has no active worktree, so commands run in main context
      String command = "git rebase origin/main";

      BashHandler.Result result = handler.check(
        TestUtils.bashInput(mapper, command, projectPath.toString(), SESSION_ID));

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
    try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
    {
      // Create a worktree on a feature branch
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);

      // Create the lock file so the handler resolves to the worktree context
      writeLockFile(scope, ISSUE_ID, SESSION_ID);

      JsonMapper mapper = scope.getJsonMapper();
      BlockMainRebase handler = new BlockMainRebase(scope);
      String command = "git rebase main";

      BashHandler.Result result = handler.check(
        TestUtils.bashInput(mapper, command, worktree.toString(), SESSION_ID));

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
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockMainRebase handler = new BlockMainRebase(scope);
      // No lock — session is in main context; checkout must be blocked
      String command = "git checkout feature-branch";

      BashHandler.Result result = handler.check(
        TestUtils.bashInput(mapper, command, projectPath.toString(), SESSION_ID));

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
    try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
    {
      createWorktreeDir(scope, ISSUE_ID);
      writeLockFile(scope, ISSUE_ID, SESSION_ID);

      JsonMapper mapper = scope.getJsonMapper();
      BlockMainRebase handler = new BlockMainRebase(scope);
      // The session has a lock — it's in an issue worktree context, not main
      String command = "git checkout -b new-branch";

      BashHandler.Result result = handler.check(
        TestUtils.bashInput(mapper, command, mainRepo.toString(), SESSION_ID));

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
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockMainRebase handler = new BlockMainRebase(scope);
      handler.check(TestUtils.bashInput(mapper, "git checkout feature-branch", projectPath.toString(), ""));
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
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockMainRebase handler = new BlockMainRebase(scope);
      handler.check(TestUtils.bashInput(mapper, "git rebase origin/main", projectPath.toString(), ""));
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
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockMainRebase handler = new BlockMainRebase(scope);
      String command = "git log --oneline -10";

      BashHandler.Result result = handler.check(
        TestUtils.bashInput(mapper, command, projectPath.toString(), SESSION_ID));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
