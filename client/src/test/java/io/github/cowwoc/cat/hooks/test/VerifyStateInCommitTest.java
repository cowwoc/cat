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
   * Verifies that feature commits outside a CAT worktree are allowed without STATE.md checks.
   */
  @Test
  public void allowsNonWorktreeCommits() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("test-branch");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // No .git/cat-base and no .claude/cat directory means not a CAT worktree
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
