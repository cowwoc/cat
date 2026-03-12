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
   * Verifies that a warning is issued when STATE.md is staged but does not contain "closed" status.
   */
  @Test
  public void warnsWhenStateMdNotClosed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-new-feature");
      try
      {
        // Create and stage STATE.md with "open" status (not "closed")
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "- **Status:** open\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add new feature\"", worktreeDir.toString(), "test-session"));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("closed");
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
   * Verifies that no warning is issued when STATE.md is staged with "closed" status.
   */
  @Test
  public void noWarnWhenStateMdIsClosed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-closed-feature");
      try
      {
        // Create and stage STATE.md with "closed" status
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "- **Status:** closed\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add new feature\"", worktreeDir.toString(), "test-session"));

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
   * Verifies that commits in the main workspace (which has a .cat directory) are not blocked
   * by the STATE.md check.
   */
  @Test
  public void allowsMainWorkspaceCommitsWithClaudeCatDirectory() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try
    {
      // Create .cat directory (present in main workspace but not a CAT worktree)
      // The main workspace has .cat for retrospectives/issues but its git dir parent
      // is not "worktrees", so it is not treated as a CAT worktree
      Path claudeCat = tempDir.resolve(".cat");
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

  /**
   * Verifies that when STATE.md is staged but empty, validation is silently skipped and commit is allowed.
   */
  @Test
  public void allowsWhenStateMdIsEmpty() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-empty-state");
      try
      {
        // Stage an empty STATE.md — validation should be skipped (empty content guard)
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add feature\"", worktreeDir.toString(), "test-session"));

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
   * Verifies that when STATE.md has a malformed status format (e.g., "Status: closed" instead of
   * "- **Status:** closed"), a warning is issued because the STATUS_PATTERN does not match,
   * causing isClosed to be false.
   */
  @Test
  public void warnsWhenStateMdHasMalformedFormat() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-malformed-state");
      try
      {
        // Stage STATE.md with malformed format — STATUS_PATTERN won't match, so validation is skipped
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "Status: closed\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add feature\"", worktreeDir.toString(), "test-session"));

        // Malformed format: pattern doesn't match, isClosed=false, so a warning is issued
        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("closed");
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
   * Verifies that when STATE.md has an empty status value (e.g., "- **Status:** \n"), a warning is issued
   * because the empty string is not IssueStatus.CLOSED, causing isClosed to be false.
   */
  @Test
  public void warnsWhenStateMdHasEmptyStatusValue() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-empty-status-value");
      try
      {
        // Stage STATE.md with an empty status value — empty string is not IssueStatus.CLOSED
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "- **Status:** \n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add feature\"", worktreeDir.toString(), "test-session"));

        // Empty string is not IssueStatus.CLOSED, so a warning is expected
        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("closed");
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
   * Verifies that a warning is issued when STATE.md has the Status key but no value at all
   * (e.g., "- **Status:**\n" with no trailing space and no value), so the STATUS_PATTERN
   * {@code (.+)$} fails to match because there is no character after the colon.
   */
  @Test
  public void warnsWhenStateMdHasStatusKeyWithNoValue() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-no-value-status");
      try
      {
        // Stage STATE.md with status key but truly no value — pattern requires (.+) so no match occurs
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "- **Status:**\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add feature\"", worktreeDir.toString(), "test-session"));

        // Pattern fails to match (no value after colon), isClosed=false, so a warning is expected
        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("closed");
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
   * Verifies that when STATE.md has a valid format but an unrecognized status value, a warning is issued.
   */
  @Test
  public void warnsWhenStateMdHasInvalidStatus() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-invalid-status");
      try
      {
        // Stage STATE.md with valid format but an invalid/unknown status value
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "- **Status:** unknown\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add feature\"", worktreeDir.toString(), "test-session"));

        // "unknown" is not IssueStatus.CLOSED, so warning is expected
        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("closed");
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
   * Verifies that a warning is issued when STATE.md has "in-progress" status (not closed).
   */
  @Test
  public void warnsWhenStateMdHasInProgressStatus() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-in-progress-status");
      try
      {
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "- **Status:** in-progress\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add feature\"", worktreeDir.toString(), "test-session"));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("closed");
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
   * Verifies that a warning is issued when STATE.md has "blocked" status (not closed).
   */
  @Test
  public void warnsWhenStateMdHasBlockedStatus() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getProjectCatDir().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-blocked-status");
      try
      {
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("STATE.md"), "- **Status:** blocked\n");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("STATE.md").toString());

        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashInput("git commit -m \"feature: add feature\"", worktreeDir.toString(), "test-session"));

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").contains("closed");
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
   * Verifies that when git commands fail (IOException), the hook silently allows the commit.
   * This tests the IOException catch block in the check() method (lines handling git failures).
   */
  @Test
  public void allowsWhenGitCommandFails() throws IOException
  {
    // Use a non-existent directory to simulate a git command failure (git rev-parse will fail)
    // The isInCatWorktree() method catches IOException and returns false → Result.allow()
    Path nonExistentDir = Path.of("/tmp/non-existent-directory-" + System.nanoTime());

    VerifyStateInCommit handler = new VerifyStateInCommit();

    BashHandler.Result result = handler.check(
      TestUtils.bashInput("git commit -m \"bugfix: fix something\"", nonExistentDir.toString(), "test-session"));

    // When git command fails, isInCatWorktree() returns false → allow
    requireThat(result.blocked(), "blocked").isFalse();
    requireThat(result.reason(), "reason").isEmpty();
  }
}
