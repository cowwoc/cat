/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.bash.ValidateGitOperations;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link ValidateGitOperations}.
 */
public final class ValidateGitOperationsTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Verifies that force pushing directly to main is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksForcePushMain() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git push --force origin main", projectPath, SESSION_ID,
      projectPath, pluginRoot, projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that {@code --force-with-lease} is not treated as unsafe force push.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsForceWithLease() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git push --force-with-lease origin main", projectPath,
      SESSION_ID, projectPath, pluginRoot, projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that unsafe force push is allowed for non-protected destination branches.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsForcePushToNonProtectedBranch() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git push --force origin feature/my-branch",
      projectPath, SESSION_ID, projectPath, pluginRoot, projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that force push using refspec destination main is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksForcePushToMainViaRefspec() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git push --force origin HEAD:main", projectPath,
      SESSION_ID, projectPath, pluginRoot, projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that force push using namespaced source refspec to main is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksForcePushNamespacedSourceToMainViaRefspec() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git push -f origin agent/main:main", projectPath,
      SESSION_ID, projectPath, pluginRoot, projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that unrelated text containing "worktrees" does not bypass reset-hard blocking.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksResetHardWhenWorktreesAppearsInUnrelatedArgument() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    String command = "echo worktrees && git reset --hard";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that reset-hard scoped to an issue worktree via {@code -C} is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsResetHardInIssueWorktreeViaDashC() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "git -C " + worktreeDir + " reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that reset-hard scoped to an issue worktree with spaces via quoted {@code -C} is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsResetHardInIssueWorktreeViaQuotedDashCWithSpaces() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path issueWorktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path worktreeDir = Files.createDirectories(issueWorktreeDir.resolve("my worktree"));
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "git -C \"" + worktreeDir + "\" reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that reset-hard scoped to main via {@code --git-dir}/{@code --work-tree} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksResetHardScopedToMainViaGitDir() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    String command = "git --git-dir=" + projectPath.resolve(".git") + " --work-tree=" + projectPath +
      " reset --hard";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that reset-hard scoped to main via {@code --work-tree <path>} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksResetHardScopedToMainViaWorkTreeFlag() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    String command = "git --work-tree " + projectPath + " reset --hard";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that reset-hard scoped to main via quoted {@code --work-tree}/{@code --git-dir} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksResetHardScopedToMainViaQuotedWorkTreeAndGitDirWithSpaces() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    Path spacedMain = Files.createDirectories(projectPath.resolve("main dir"));
    String command = "git --git-dir=\"" + projectPath.resolve(".git") + "\" --work-tree=\"" + spacedMain +
      "\" reset --hard";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that reset-hard scoped to an issue worktree via {@code --work-tree=<path>} is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsResetHardInIssueWorktreeViaWorkTreeEqualsFlag() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "git --work-tree=" + worktreeDir + " reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that reset-hard in an issue worktree via {@code cd ... && git reset --hard} is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsResetHardInIssueWorktreeViaCdChain() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "cd " + worktreeDir + " && git reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that reset-hard in main via {@code cd ... && git --git-dir=... reset --hard} is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksResetHardInMainViaCdChainAndGitDir() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    String command = "cd " + projectPath + " && git --git-dir=" + projectPath.resolve(".git") +
      " reset --hard";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies that compact {@code -C<path>} scoped to main is blocked even with an active worktree lock.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksResetHardInMainViaCompactDashC() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "git -C" + projectPath + " reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that newline-separated command chains are parsed and allowed in issue worktree context.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsResetHardInIssueWorktreeViaNewlineChain() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "cd " + worktreeDir + "\ngit reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that reset-hard scoped via quoted split {@code --work-tree} and {@code --git-dir} is allowed in worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsResetHardViaQuotedSplitScopeFlagsInWorktree() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path issueWorktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path worktreeDir = Files.createDirectories(issueWorktreeDir.resolve("my worktree"));
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "git --git-dir \"" + worktreeDir.resolve(".git") + "\" --work-tree \"" + worktreeDir +
        "\" reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that repeated scope overrides ending on main are blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksResetHardWhenRepeatedScopeFlagsEndOnMain() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "git -C " + worktreeDir + " --work-tree " + projectPath + " reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies parser treats separator-like tokens inside quotes as plain text.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void quotedSeparatorsDoNotSplitSegments() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    String command = "echo \"a && b || c | d\" && git reset --hard";
    try (TestClaudeHook scope = TestUtils.bashHook(command, projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      ValidateGitOperations handler = new ValidateGitOperations(scope);
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
   * Verifies mixed separators and newline chaining preserve worktree scope for reset-hard.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsResetHardViaMixedSeparatorAndNewlineChainInWorktree() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("vgo-test-");
    try (TestClaudeHook scope = TestUtils.bashHook("git status", projectPath, SESSION_ID, projectPath, pluginRoot,
      projectPath))
    {
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      String command = "echo start || true\ncd " + worktreeDir + " && echo ok | cat\ngit reset --hard";
      try (TestClaudeHook scoped = TestUtils.bashHook(command, projectPath, SESSION_ID, scope))
      {
        ValidateGitOperations handler = new ValidateGitOperations(scoped);
        BashHandler.Result result = handler.check();
        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
