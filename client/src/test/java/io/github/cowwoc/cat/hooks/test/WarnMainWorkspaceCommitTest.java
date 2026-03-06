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
import io.github.cowwoc.cat.hooks.bash.WarnMainWorkspaceCommit;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Tests for {@link WarnMainWorkspaceCommit}.
 * <p>
 * Tests verify that:
 * <ul>
 *   <li>A warning is emitted when {@code git commit} is issued in the main workspace and an active
 *     worktree lock exists for the current session</li>
 *   <li>No warning is emitted when committing from inside an issue worktree</li>
 *   <li>No warning is emitted when no active worktree lock exists</li>
 *   <li>Non-commit commands are always allowed</li>
 * </ul>
 * <p>
 * Each test uses a unique session ID to avoid interference from the static session cache in
 * {@code WorktreeLock}.
 */
public final class WarnMainWorkspaceCommitTest
{
  /**
   * Creates a lock file for the given session and issue in the scope's lock directory.
   *
   * @param scope     the JVM scope providing the project CAT directory
   * @param issueId   the issue ID for the lock
   * @param sessionId the session ID to record in the lock file
   * @throws IOException if file operations fail
   */
  private static void createLockFile(JvmScope scope, String issueId, String sessionId) throws IOException
  {
    IssueLock lock = new IssueLock(scope);
    lock.acquire(issueId, sessionId, "");
  }

  /**
   * Verifies that committing from inside a real CAT worktree does NOT emit a warning.
   * <p>
   * A CAT worktree is identified by its git directory ending with {@code worktrees/<branch-name>}.
   * When the agent correctly commits from inside the worktree, no warning is emitted.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void cwdInWorktreeIsIdentifiedAsCatWorktree() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-cwd-issue");
      try
      {
        createLockFile(scope, "2.1-cwd-issue", sessionId);

        WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
        // Pass worktreeDir as the working directory — it IS a CAT worktree by git dir structure
        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: test cwd detection\"",
            worktreeDir.toString(), sessionId));

        // Should allow because the worktree is identified by git dir structure
        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
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
   * Verifies that a non-commit command is always allowed.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void nonCommitCommandIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "test-issue", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(TestUtils.bashInput("git status", mainRepo.toString(), sessionId));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that committing in the main workspace with an active worktree lock emits a warning.
   * <p>
   * This is the true-positive case: an agent commits to the main workspace instead of routing
   * the commit to the active worktree.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void commitInMainWorkspaceWithActiveLockEmitsWarning() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "2.1-my-feature", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"feature: add something\"", mainRepo.toString(), sessionId));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("MAIN WORKSPACE COMMIT DETECTED");
      requireThat(result.reason(), "reason").contains("2.1-my-feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that the warning message contains complete structure with worktree path,
   * routing guidance, and impact language.
   * <p>
   * The warning must include:
   * <ul>
   *   <li>The exact worktree path for the active issue</li>
   *   <li>Routing guidance in the form "cd &lt;path&gt; && git commit"</li>
   *   <li>Impact language about polluting the main branch</li>
   * </ul>
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void warningMessageContainsCompleteStructure() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "2.1-active-issue", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"bugfix: fix something\"", mainRepo.toString(), sessionId));

      String reason = result.reason();
      // Verify complete message structure
      requireThat(reason, "reason").contains("MAIN WORKSPACE COMMIT DETECTED");
      requireThat(reason, "reason").contains("2.1-active-issue");

      // Verify worktree path is included
      Path worktreePath = scope.getProjectCatDir().resolve("worktrees").resolve("2.1-active-issue");
      requireThat(reason, "reason").contains(worktreePath.toString());

      // Verify routing guidance
      requireThat(reason, "reason").contains("cd ");
      requireThat(reason, "reason").contains("git commit");

      // Verify impact language
      requireThat(reason, "reason").contains("pollute the main branch");
      requireThat(reason, "reason").contains("worktree isolation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that committing from inside a CAT worktree does NOT emit a warning.
   * <p>
   * This is the false-positive suppression case: the agent is correctly working in the worktree.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void commitFromInsideWorktreeIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-active-issue");
      try
      {
        createLockFile(scope,"2.1-active-issue", sessionId);

        WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: implement the feature\"",
            worktreeDir.toString(), sessionId));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
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
   * Verifies that committing in the main workspace with NO active worktree lock does NOT emit a warning.
   * <p>
   * This is the false-positive suppression case: the agent has no active issue worktree, so a main
   * workspace commit is legitimate.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void commitInMainWorkspaceWithNoActiveLockIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      // No lock file created — no active worktree lock for this session
      String sessionId = UUID.randomUUID().toString();

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"config: update settings\"", mainRepo.toString(), sessionId));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that a command with {@code cd <worktree>} is handled correctly.
   * <p>
   * When the command contains {@code cd /worktree && git commit} and the worktree is a CAT worktree,
   * no warning should be emitted.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void cdIntoWorktreeThenCommitIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-cd-issue");
      try
      {
        createLockFile(scope,"2.1-cd-issue", sessionId);

        WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
        // cwd is mainRepo but command cd's into the worktree
        String command = "cd " + worktreeDir + " && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(TestUtils.bashInput(command, mainRepo.toString(), sessionId));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
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
   * Verifies that {@code git commit --amend} in the main workspace with an active worktree lock emits
   * a warning.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void gitCommitAmendInMainWorkspaceWithActiveLockEmitsWarning() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "2.1-amend-issue", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit --amend --no-edit", mainRepo.toString(), sessionId));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("MAIN WORKSPACE COMMIT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that git push (not commit) in the main workspace with an active worktree lock is allowed.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void gitPushInMainWorkspaceWithActiveLockIsAllowed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "2.1-push-issue", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git push origin v2.1", mainRepo.toString(), sessionId));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that "git  commit" (with multiple spaces) is correctly recognized as a commit command.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void gitCommitWithMultipleSpacesEmitsWarning() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "2.1-spaces-issue", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git  commit -m \"feature: test\"", mainRepo.toString(), sessionId));  // Two spaces

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("MAIN WORKSPACE COMMIT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that "git commit" with multiple flags (--flag1 --flag2) is detected.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void gitCommitWithMultipleFlagsEmitsWarning() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "2.1-flags-issue", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit --verify --sign-off -m \"feature: test\"", mainRepo.toString(), sessionId));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("MAIN WORKSPACE COMMIT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that "git commit -a" (shorthand) is detected.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void gitCommitWithShorthandFlagEmitsWarning() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      createLockFile(scope, "2.1-shorthand-issue", sessionId);

      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -a -m \"feature: test\"", mainRepo.toString(), sessionId));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("MAIN WORKSPACE COMMIT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that "cd path; git commit" (semicolon) is correctly parsed and commits from the cd target.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void cdWithSemicolonThenCommitIsHandled() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-semicolon-issue");
      try
      {
        createLockFile(scope,"2.1-semicolon-issue", sessionId);

        WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
        // Semicolon syntax instead of &&
        String command = "cd " + worktreeDir + "; git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(TestUtils.bashInput(command, mainRepo.toString(), sessionId));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
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
   * Verifies that "cd" with pipe operator "(|)" is handled correctly.
   * <p>
   * The regex pattern matches cd directives separated by pipes and other operators.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void cdWithPipeOperatorIsHandled() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-pipe-issue");
      try
      {
        createLockFile(scope,"2.1-pipe-issue", sessionId);

        WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
        // Using pipe operator
        String command = "cd " + worktreeDir + " | git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(TestUtils.bashInput(command, mainRepo.toString(), sessionId));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
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
   * Verifies that quoted paths in cd commands are handled correctly.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void cdWithQuotedPathsIsHandled() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-quoted-issue");
      try
      {
        createLockFile(scope,"2.1-quoted-issue", sessionId);

        WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
        // Double-quoted path
        String command = "cd \"" + worktreeDir + "\" && git commit -m \"feature: test\"";
        BashHandler.Result result = handler.check(TestUtils.bashInput(command, mainRepo.toString(), sessionId));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
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
   * Verifies that lock files are used correctly for detection.
   * <p>
   * Tests that when a lock file is created for an issue, the handler can detect it and emit a warning
   * when a commit is attempted from the main workspace.
   *
   * @throws IOException if an I/O error occurs during test setup
   */
  @Test
  public void lockFileDetectionInHandler() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      String sessionId = UUID.randomUUID().toString();
      String issueId = "2.1-lock-detection-issue";
      createLockFile(scope, issueId, sessionId);

      // Verify that handler can detect the lock and emit a warning
      WarnMainWorkspaceCommit handler = new WarnMainWorkspaceCommit(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git commit -m \"feature: test lock detection\"", mainRepo.toString(), sessionId));

      // If the lock is correctly created and detected, we should get a warning
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains(issueId);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }
}
