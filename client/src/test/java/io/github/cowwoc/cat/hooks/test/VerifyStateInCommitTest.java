/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git status", tempDir.toString(), "test-session", scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-bugfix/feature commits are allowed without index.json checks.
   */
  @Test
  public void allowsNonBugfixFeatureCommits() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit -m \"refactor: clean up code\"", tempDir.toString(), "test-session", scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that --amend commits are allowed without index.json checks.
   */
  @Test
  public void allowsAmendCommits() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit --amend -m \"feature: updated feature\"",
          tempDir.toString(), "test-session", scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that bugfix commits in a CAT worktree are blocked when index.json is not staged.
   */
  @Test
  public void blocksWhenIndexJsonNotStaged() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-fix-thing");
      try
      {
        // Stage a file that is NOT index.json
        Files.writeString(worktreeDir.resolve("some-file.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "some-file.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"bugfix: fix the thing\"",
            worktreeDir.toString(), "test-session", scope));

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("index.json not included");
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
   * Verifies that a warning is issued when index.json is staged but does not contain "closed" status.
   */
  @Test
  public void warnsWhenIndexJsonNotClosed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-new-feature");
      try
      {
        // Create and stage index.json with "open" status (not "closed")
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add new feature\"",
            worktreeDir.toString(), "test-session", scope));

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
   * Verifies that no warning is issued when index.json is staged with "closed" status.
   */
  @Test
  public void noWarnWhenIndexJsonIsClosed() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-closed-feature");
      try
      {
        // Create and stage index.json with "closed" status
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"closed\"}");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add new feature\"",
            worktreeDir.toString(), "test-session", scope));

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
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // worktreeDir is a real CAT worktree with git dir ending in "worktrees/<branch>"
      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-fix-something");
      try
      {
        // Stage a file in worktreeDir that is NOT index.json
        Files.writeString(worktreeDir.resolve("some-file.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "some-file.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        // workingDirectory is mainRepo (not a CAT worktree), but command has "cd worktreeDir"
        // The handler should detect worktreeDir as the effective directory via cd extraction
        String command = "cd " + worktreeDir + " && git commit -m \"bugfix: fix something\"";
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, mainRepo.toString(), "test-session", scope));

        // Since worktreeDir is a CAT worktree and index.json is not staged, it should be blocked
        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("index.json not included");
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
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
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
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, firstDir.toString(), "test-session", scope));

        // secondDir is not a CAT worktree, so no index.json check → allowed
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Regular repo: git dir parent is not "worktrees" → not a CAT worktree → should allow
      VerifyStateInCommit handler = new VerifyStateInCommit();

      // No cd in command — working directory is used
      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit -m \"bugfix: fix something\"", tempDir.toString(), "test-session", scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that commits in the main workspace (which has a .cat directory) are not blocked
   * by the index.json check.
   */
  @Test
  public void allowsMainWorkspaceCommitsWithClaudeCatDirectory() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
        TestUtils.bashHook("git commit -m \"bugfix: fix something\"", tempDir.toString(), "test-session", scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that feature commits outside a CAT worktree are allowed without index.json checks.
   */
  @Test
  public void allowsNonWorktreeCommits() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Regular repo: git dir parent is not "worktrees" → not a CAT worktree
      Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
      TestUtils.runGit(tempDir, "add", "Foo.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit -m \"feature: add feature\"", tempDir.toString(), "test-session", scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when index.json is staged but empty, validation is silently skipped and commit is allowed.
   */
  @Test
  public void allowsWhenIndexJsonIsEmpty() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-empty-state");
      try
      {
        // Stage an empty index.json — validation should be skipped (empty content guard)
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add feature\"",
            worktreeDir.toString(), "test-session", scope));

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
   * Verifies that when index.json contains invalid JSON, validation is silently skipped and commit is allowed.
   * The isClosedStatus() method catches the IOException from JSON parsing and returns false,
   * but since the content guard only invokes it when content is non-empty, it falls through to allow.
   * <p>
   * Wait — the content is non-empty so isClosedStatus() IS called, IOException is caught, returns false,
   * and a warning is issued (not-closed). This tests that invalid JSON produces a warning.
   */
  @Test
  public void warnsWhenIndexJsonHasInvalidJson() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-invalid-json");
      try
      {
        // Stage index.json with invalid JSON content
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "not valid json");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add feature\"",
            worktreeDir.toString(), "test-session", scope));

        // Invalid JSON: isClosedStatus() catches IOException, returns false → warning issued
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
   * Verifies that when index.json has a missing status field, a warning is issued because
   * the status node is null, causing isClosedStatus() to return false.
   */
  @Test
  public void warnsWhenIndexJsonHasMissingStatusField() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-missing-status");
      try
      {
        // Stage index.json with no "status" field
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "{}");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add feature\"",
            worktreeDir.toString(), "test-session", scope));

        // Missing status field: statusNode is null → isClosedStatus() returns false → warning issued
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
   * Verifies that when index.json has a valid format but an unrecognized status value, a warning is issued.
   */
  @Test
  public void warnsWhenIndexJsonHasInvalidStatus() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-invalid-status");
      try
      {
        // Stage index.json with valid format but an invalid/unknown status value
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"unknown\"}");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        // Also stage a source file
        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add feature\"",
            worktreeDir.toString(), "test-session", scope));

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
   * Verifies that a warning is issued when index.json has "in-progress" status (not closed).
   */
  @Test
  public void warnsWhenIndexJsonHasInProgressStatus() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-in-progress-status");
      try
      {
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"in-progress\"}");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add feature\"",
            worktreeDir.toString(), "test-session", scope));

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
   * Verifies that a warning is issued when index.json has "blocked" status (not closed).
   */
  @Test
  public void warnsWhenIndexJsonHasBlockedStatus() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-blocked-status");
      try
      {
        Path issueDir = worktreeDir.resolve(".cat").resolve("issues").resolve("test-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"blocked\"}");
        TestUtils.runGit(worktreeDir, "add", issueDir.resolve("index.json").toString());

        Files.writeString(worktreeDir.resolve("Foo.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "Foo.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();

        BashHandler.Result result = handler.check(
          TestUtils.bashHook("git commit -m \"feature: add feature\"",
            worktreeDir.toString(), "test-session", scope));

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
    Path tempDir = Files.createTempDirectory("vsic-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyStateInCommit handler = new VerifyStateInCommit();

      BashHandler.Result result = handler.check(
        TestUtils.bashHook("git commit -m \"bugfix: fix something\"",
          nonExistentDir.toString(), "test-session", scope));

      // When git command fails, isInCatWorktree() returns false → allow
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when a commit is blocked for missing index.json, the suggested git add command
   * references the specific issue path derived from the branch name, not a glob pattern.
   */
  @Test
  public void blockMessageContainsSpecificIssuePath() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (TestClaudeTool scope = new TestClaudeTool(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-my-test-issue");
      try
      {
        Files.writeString(worktreeDir.resolve("some-file.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "some-file.java");

        VerifyStateInCommit handler = new VerifyStateInCommit();
        String command = "git commit -m \"bugfix: fix the thing\"";
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, worktreeDir.toString(), "test-session", scope));

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").
          contains(".cat/issues/v2/v2.1/my-test-issue/index.json");
        requireThat(result.reason(), "reason").doesNotContain("**/index.json");
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
