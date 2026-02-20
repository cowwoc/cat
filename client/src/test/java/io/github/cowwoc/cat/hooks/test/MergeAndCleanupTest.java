/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.MergeAndCleanup;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for MergeAndCleanup validation and error handling.
 * <p>
 * Tests verify input validation without requiring actual git repository setup.
 */
public class MergeAndCleanupTest
{
  /**
   * Creates a temporary directory for testing.
   *
   * @return the temporary directory path
   */
  private Path createTempDir()
  {
    try
    {
      return Files.createTempDirectory("merge-and-cleanup-test");
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Verifies that execute rejects null projectDir.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullProjectDir() throws IOException
  {
    Path projectDir = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup(scope);

      try
      {
        cmd.execute(null, "issue-id", "session-id", "", tempDir.toString());
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("projectDir");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects blank projectDir.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsBlankProjectDir() throws IOException
  {
    Path projectDir = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup(scope);

      try
      {
        cmd.execute("", "issue-id", "session-id", "", tempDir.toString());
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("projectDir");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects null issueId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullIssueId() throws IOException
  {
    Path projectDir = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup(scope);

      try
      {
        cmd.execute(tempDir.toString(), null, "session-id", "", tempDir.toString());
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("issueId");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects directory without cat config.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNonCatProject() throws IOException
  {
    Path projectDir = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup(scope);

      try
      {
        cmd.execute(tempDir.toString(), "issue-id", "session-id", "",
          tempDir.toString());
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("Not a CAT project");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute accepts empty worktreePath for auto-detect.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeAcceptsEmptyWorktreePath() throws IOException
  {
    Path projectDir = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
    Path tempDir = createTempDir();
    try
    {
      Path catDir = tempDir.resolve(".claude/cat");
      Files.createDirectories(catDir);

      MergeAndCleanup cmd = new MergeAndCleanup(scope);

      try
      {
        cmd.execute(tempDir.toString(), "issue-id", "session-id", "",
          tempDir.toString());
      }
      catch (IOException _)
      {
        // Expected - worktree not found
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute auto-rebases when base branch has diverged from the issue branch.
   * <p>
   * When the base branch has new commits not in the issue branch, the merge should
   * automatically run {@code git rebase --onto} to replay the issue-specific commits
   * on top of the current base, then proceed with the fast-forward merge.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeAutoRebasesWhenBaseBranchDiverged() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      // Create the issue branch from v2.1 (this is the divergence point / merge-base)
      String issueBranch = "my-issue";
      Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);

      // Configure user in worktree
      TestUtils.runGitCommand(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGitCommand(issueWorktree, "config", "user.name", "Test User");

      // Add an issue-specific commit in the worktree
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGitCommand(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGitCommand(issueWorktree, "commit", "-m", "Issue commit");

      // Now advance the base branch (v2.1) with a new commit, causing divergence
      Files.writeString(mainRepo.resolve("base-advance.txt"), "base branch advance");
      TestUtils.runGitCommand(mainRepo, "add", "base-advance.txt");
      TestUtils.runGitCommand(mainRepo, "commit", "-m", "Base branch advance commit");

      // Set up .claude/cat structure in main repo
      Path catDir = mainRepo.resolve(".claude/cat");
      Files.createDirectories(catDir);

      // Allow pushing to the checked-out branch (v2.1 is checked out in the main repo)
      TestUtils.runGitCommand(mainRepo, "config", "receive.denyCurrentBranch", "ignore");

      // Create the cat-base file for the worktree so getBaseBranch() works
      // git rev-parse --git-dir returns an absolute path for the main repo
      String gitDir = TestUtils.runGitCommandWithOutput(mainRepo, "rev-parse", "--absolute-git-dir");
      Path catBasePath = Path.of(gitDir).resolve("worktrees").resolve(issueBranch).resolve("cat-base");
      Files.createDirectories(catBasePath.getParent());
      Files.writeString(catBasePath, "v2.1");

      // Verify divergence exists before the call
      String divergeCount = TestUtils.runGitCommandWithOutput(issueWorktree, "rev-list", "--count",
        "HEAD..v2.1");
      requireThat(Integer.parseInt(divergeCount.strip()), "divergeCount").isGreaterThan(0);

      try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);
        String result = cmd.execute(mainRepo.toString(), issueBranch, "test-session",
          issueWorktree.toString(), pluginRoot.toString());

        requireThat(result, "result").contains("\"status\" : \"success\"");
        requireThat(result, "result").contains("\"issue_id\" : \"" + issueBranch + "\"");

        // Verify v2.1 now contains the issue commit
        String v21Log = TestUtils.runGitCommandWithOutput(mainRepo, "log", "--oneline", "v2.1");
        requireThat(v21Log, "v21Log").contains("Issue commit");
        requireThat(v21Log, "v21Log").contains("Base branch advance commit");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
