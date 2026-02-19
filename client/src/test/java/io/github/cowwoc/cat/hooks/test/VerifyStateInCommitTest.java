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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      BashHandler.Result result = handler.check("git status", tempDir.toString(),
        toolInput, null, "test-session");

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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      BashHandler.Result result = handler.check(
        "git commit -m \"refactor: clean up code\"", tempDir.toString(),
        toolInput, null, "test-session");

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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      BashHandler.Result result = handler.check(
        "git commit --amend -m \"feature: updated feature\"", tempDir.toString(),
        toolInput, null, "test-session");

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
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Create .git/cat-base to mark as CAT worktree
      Path gitDir = tempDir.resolve(".git");
      Files.writeString(gitDir.resolve("cat-base"), "v2.1");

      // Stage a file that is NOT STATE.md
      Files.writeString(tempDir.resolve("some-file.java"), "class Foo {}");
      TestUtils.runGitCommand(tempDir, "add", "some-file.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      BashHandler.Result result = handler.check(
        "git commit -m \"bugfix: fix the thing\"", tempDir.toString(),
        toolInput, null, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("STATE.md not included");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a warning is issued when STATE.md is staged but does not contain "completed" status.
   */
  @Test
  public void warnsWhenStateMdNotCompleted() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Create .git/cat-base to mark as CAT worktree
      Path gitDir = tempDir.resolve(".git");
      Files.writeString(gitDir.resolve("cat-base"), "v2.1");

      // Create and stage STATE.md with "in-progress" status
      Path issueDir = tempDir.resolve(".claude").resolve("cat").resolve("issues").resolve("test-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("STATE.md"), "status: in-progress\n");
      TestUtils.runGitCommand(tempDir, "add", issueDir.resolve("STATE.md").toString());

      // Also stage a source file
      Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
      TestUtils.runGitCommand(tempDir, "add", "Foo.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      BashHandler.Result result = handler.check(
        "git commit -m \"feature: add new feature\"", tempDir.toString(),
        toolInput, null, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("does not contain 'completed'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
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
    Path mainRepo = TestUtils.createTempGitRepo("test-branch");
    // worktreeDir is a separate CAT worktree with .git/cat-base marker
    Path worktreeDir = TestUtils.createTempGitRepo("worktree-branch");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Set up worktreeDir as a CAT worktree by adding .git/cat-base
      Path gitDir = worktreeDir.resolve(".git");
      Files.writeString(gitDir.resolve("cat-base"), "v2.1");

      // Stage a file in worktreeDir that is NOT STATE.md
      Files.writeString(worktreeDir.resolve("some-file.java"), "class Foo {}");
      TestUtils.runGitCommand(worktreeDir, "add", "some-file.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      // workingDirectory is mainRepo (not a CAT worktree), but command has "cd worktreeDir"
      // The handler should detect worktreeDir as the effective directory via cd extraction
      String command = "cd " + worktreeDir + " && git commit -m \"bugfix: fix something\"";
      BashHandler.Result result = handler.check(command, mainRepo.toString(), toolInput, null, "test-session");

      // Since worktreeDir is a CAT worktree and STATE.md is not staged, it should be blocked
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("STATE.md not included");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(worktreeDir);
    }
  }

  /**
   * Verifies that when a command has multiple cd statements, the last cd path is used as the effective directory.
   */
  @Test
  public void lastCdPathUsedWhenMultipleCdStatements() throws IOException
  {
    // firstDir is a CAT worktree (but is not the last cd target)
    Path firstDir = TestUtils.createTempGitRepo("first-branch");
    // secondDir is NOT a CAT worktree (it is the last cd target)
    Path secondDir = TestUtils.createTempGitRepo("second-branch");
    try (JvmScope scope = new TestJvmScope(firstDir, firstDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Set up firstDir as a CAT worktree
      Path firstGitDir = firstDir.resolve(".git");
      Files.writeString(firstGitDir.resolve("cat-base"), "v2.1");

      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      // Command cd's to firstDir (CAT worktree) then to secondDir (not a worktree)
      // The last cd (secondDir) should be used, so not in a CAT worktree → allowed
      String command = "cd " + firstDir + " && cd " + secondDir +
        " && git commit -m \"bugfix: fix something\"";
      BashHandler.Result result = handler.check(command, firstDir.toString(), toolInput, null, "test-session");

      // secondDir is not a CAT worktree, so no STATE.md check → allowed
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(firstDir);
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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // No .git/cat-base → not a CAT worktree → should allow
      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      // No cd in command — working directory is used
      BashHandler.Result result = handler.check(
        "git commit -m \"bugfix: fix something\"", tempDir.toString(),
        toolInput, null, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that commits in the main workspace (which has a .claude/cat directory but no .git/cat-base)
   * are not blocked by the STATE.md check.
   */
  @Test
  public void allowsMainWorkspaceCommitsWithClaudeCatDirectory() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Create .claude/cat directory (present in main workspace) but NOT .git/cat-base
      // The main workspace has .claude/cat for retrospectives/issues but is not a worktree
      Path claudeCat = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(claudeCat);

      Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
      TestUtils.runGitCommand(tempDir, "add", "Foo.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      BashHandler.Result result = handler.check(
        "git commit -m \"bugfix: fix something\"", tempDir.toString(),
        toolInput, null, "test-session");

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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // No .git/cat-base means not a CAT worktree
      Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
      TestUtils.runGitCommand(tempDir, "add", "Foo.java");

      VerifyStateInCommit handler = new VerifyStateInCommit();
      JsonNode toolInput = mapper.readTree("{}");

      BashHandler.Result result = handler.check(
        "git commit -m \"feature: add feature\"", tempDir.toString(),
        toolInput, null, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
