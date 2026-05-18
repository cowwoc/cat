/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.util.MergeAndCleanup;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
   * Verifies that execute rejects null projectPath.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*projectPath.*")
  public void executeRejectsNullProjectDir() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      Path tempDir = TestUtils.createTempDir("merge-cleanup-test");
      try
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        cmd.execute(null, "issue-id", "session-id", "v2.1", "", tempDir.toString());
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that execute rejects blank projectPath.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*projectPath.*")
  public void executeRejectsBlankProjectDir() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      Path tempDir = TestUtils.createTempDir("merge-cleanup-test");
      try
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        cmd.execute("", "issue-id", "session-id", "v2.1", "", tempDir.toString());
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
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*issueId.*")
  public void executeRejectsNullIssueId() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      Path tempDir = TestUtils.createTempDir("merge-cleanup-test");
      try
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        cmd.execute(tempDir.toString(), null, "session-id", "v2.1", "", tempDir.toString());
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
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Not a CAT project.*")
  public void executeRejectsNonCatProject() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      Path tempDir = TestUtils.createTempDir("merge-cleanup-test");
      try
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        cmd.execute(tempDir.toString(), "issue-id", "session-id", "v2.1", "",
          tempDir.toString());
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
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      Path tempDir = TestUtils.createTempDir("merge-cleanup-test");
      try
      {
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);

        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        try
        {
          cmd.execute(tempDir.toString(), "issue-id", "session-id", "v2.1", "",
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
   * Verifies that execute fast-forwards the local target branch when it is behind origin.
   * <p>
   * When the local target branch is behind origin (origin has new commits), the merge should
   * fetch and fast-forward the local target branch before proceeding.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeUpdatesLocalTargetBranchWhenBehindOrigin() throws IOException
  {
    // Create a bare "origin" repo
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path localRepo = Files.createTempDirectory("local-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      // Initialize bare origin
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      // Create local repo and clone from origin
      TestUtils.runGit(localRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(localRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(localRepo, "config", "user.name", "Test User");

      // Create initial commit in local
      Files.writeString(localRepo.resolve("README.md"), "initial");
      TestUtils.runGit(localRepo, "add", "README.md");
      TestUtils.runGit(localRepo, "commit", "-m", "Initial commit");

      // Add origin remote and push
      TestUtils.runGit(localRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(localRepo, "push", "-u", "origin", "v2.1");

      // Add a new commit to origin (simulating another developer pushing)
      // We create a separate temp repo to push to origin
      Path tempClone = Files.createTempDirectory("temp-clone-");
      try
      {
        TestUtils.runGit(tempClone, "clone", originRepo.toString(), ".");
        TestUtils.runGit(tempClone, "config", "user.email", "test@example.com");
        TestUtils.runGit(tempClone, "config", "user.name", "Test User");
        Files.writeString(tempClone.resolve("origin-advance.txt"), "from origin");
        TestUtils.runGit(tempClone, "add", "origin-advance.txt");
        TestUtils.runGit(tempClone, "commit", "-m", "Origin advance commit");
        TestUtils.runGit(tempClone, "push", "origin", "v2.1");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempClone);
      }

      // Create the issue branch from v2.1 in the local repo
      String issueBranch = "my-sync-issue";
      Path issueWorktree = TestUtils.createWorktree(localRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");

      // Add an issue-specific commit in the worktree
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

      // Set up .cat structure in local repo
      Path catDir = localRepo.resolve(".cat");
      Files.createDirectories(catDir);

      try (TestClaudeTool scope = new TestClaudeTool(localRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        // Verify precondition: local v2.1 is stale (does not have origin's advance commit)
        String preLog = TestUtils.runGitCommandWithOutput(localRepo, "log", "--oneline", "v2.1");
        requireThat(preLog, "preLog").doesNotContain("Origin advance commit");

        String result = cmd.execute(localRepo.toString(), issueBranch, "test-session", "v2.1",
          issueWorktree.toString(), pluginRoot.toString());

        requireThat(result, "result").contains("\"status\" : \"success\"");

        // Verify local v2.1 now contains the origin advance commit
        String v21Log = TestUtils.runGitCommandWithOutput(localRepo, "log", "--oneline", "v2.1");
        requireThat(v21Log, "v21Log").contains("Origin advance commit");
        requireThat(v21Log, "v21Log").contains("Issue commit");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(localRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that execute throws a clear error when the local target branch has diverged from origin.
   * <p>
   * When the local target branch has commits not in origin, the fast-forward update fails and
   * execute must throw an IOException with a message explaining the divergence.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*diverged.*")
  public void executeThrowsWhenLocalTargetBranchDivergedFromOrigin() throws IOException
  {
    // Create a bare "origin" repo
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path localRepo = Files.createTempDirectory("local-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      // Initialize bare origin
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      // Create local repo
      TestUtils.runGit(localRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(localRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(localRepo, "config", "user.name", "Test User");

      // Create initial commit in local
      Files.writeString(localRepo.resolve("README.md"), "initial");
      TestUtils.runGit(localRepo, "add", "README.md");
      TestUtils.runGit(localRepo, "commit", "-m", "Initial commit");

      // Add origin remote and push initial state
      TestUtils.runGit(localRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(localRepo, "push", "-u", "origin", "v2.1");

      // Add a divergent commit to local v2.1 that is NOT in origin (local diverged)
      Files.writeString(localRepo.resolve("local-only.txt"), "local only");
      TestUtils.runGit(localRepo, "add", "local-only.txt");
      TestUtils.runGit(localRepo, "commit", "-m", "Local-only divergent commit");

      // Add a different commit to origin (via tempClone), making them truly divergent
      Path tempClone = Files.createTempDirectory("temp-clone-");
      try
      {
        TestUtils.runGit(tempClone, "clone", originRepo.toString(), ".");
        TestUtils.runGit(tempClone, "config", "user.email", "test@example.com");
        TestUtils.runGit(tempClone, "config", "user.name", "Test User");
        Files.writeString(tempClone.resolve("origin-only.txt"), "origin only");
        TestUtils.runGit(tempClone, "add", "origin-only.txt");
        TestUtils.runGit(tempClone, "commit", "-m", "Origin-only divergent commit");
        TestUtils.runGit(tempClone, "push", "origin", "v2.1");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempClone);
      }

      // Create the issue branch from local v2.1
      String issueBranch = "my-diverged-issue";
      Path issueWorktree = TestUtils.createWorktree(localRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");

      // Add an issue commit
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

      // Set up .cat structure
      Path catDir = localRepo.resolve(".cat");
      Files.createDirectories(catDir);

      try (TestClaudeTool scope = new TestClaudeTool(localRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        cmd.execute(localRepo.toString(), issueBranch, "test-session", "v2.1",
          issueWorktree.toString(), pluginRoot.toString());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(localRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that execute throws a clear error when fetch fails due to an invalid remote.
   * <p>
   * When the origin remote is unreachable or does not exist, the fetch must fail immediately
   * with an IOException describing the network/remote issue.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*origin.*")
  public void executeThrowsWhenFetchFailsDueToInvalidRemote() throws IOException
  {
    Path localRepo = Files.createTempDirectory("local-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      // Create a local repo with an invalid/missing origin remote
      TestUtils.runGit(localRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(localRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(localRepo, "config", "user.name", "Test User");

      Files.writeString(localRepo.resolve("README.md"), "initial");
      TestUtils.runGit(localRepo, "add", "README.md");
      TestUtils.runGit(localRepo, "commit", "-m", "Initial commit");

      // Add a broken/nonexistent origin remote
      TestUtils.runGit(localRepo, "remote", "add", "origin",
        "/nonexistent/path/that/does/not/exist");

      // Create the issue branch
      String issueBranch = "my-fetch-fail-issue";
      Path issueWorktree = TestUtils.createWorktree(localRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");

      // Add an issue commit
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

      // Set up .cat structure
      Path catDir = localRepo.resolve(".cat");
      Files.createDirectories(catDir);

      try (TestClaudeTool scope = new TestClaudeTool(localRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        cmd.execute(localRepo.toString(), issueBranch, "test-session", "v2.1",
          issueWorktree.toString(), pluginRoot.toString());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(localRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that execute auto-rebases when target branch has diverged from the issue branch.
   * <p>
   * When the target branch has new commits not in the issue branch, the merge should
   * automatically run {@code git rebase --onto} to replay the issue-specific commits
   * on top of the current target, then proceed with the fast-forward merge.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeAutoRebasesWhenTargetBranchDiverged() throws IOException
  {
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path mainRepo = Files.createTempDirectory("main-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      // Initialize bare origin
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      // Create main repo and set up initial commit
      TestUtils.runGit(mainRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(mainRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(mainRepo, "config", "user.name", "Test User");
      Files.writeString(mainRepo.resolve("README.md"), "test");
      TestUtils.runGit(mainRepo, "add", "README.md");
      TestUtils.runGit(mainRepo, "commit", "-m", "Initial commit");

      // Add origin remote and push initial state
      TestUtils.runGit(mainRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(mainRepo, "push", "-u", "origin", "v2.1");

      // Create the issue branch from v2.1 (this is the divergence point / merge-base)
      String issueBranch = "my-issue";
      Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);

      // Configure user in worktree
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");

      // Add an issue-specific commit in the worktree
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

      // Now advance the target branch (v2.1) with a new commit, causing divergence
      Files.writeString(mainRepo.resolve("target-advance.txt"), "target branch advance");
      TestUtils.runGit(mainRepo, "add", "target-advance.txt");
      TestUtils.runGit(mainRepo, "commit", "-m", "Target branch advance commit");

      // Also push this advance to origin so syncTargetBranchWithOrigin doesn't fail
      TestUtils.runGit(mainRepo, "push", "origin", "v2.1");

      // Set up .cat structure in main repo
      Path catDir = mainRepo.resolve(".cat");
      Files.createDirectories(catDir);

      // Verify divergence exists before the call
      String divergeCount = TestUtils.runGitCommandWithOutput(issueWorktree, "rev-list", "--count",
        "HEAD..v2.1");
      requireThat(Integer.parseInt(divergeCount.strip()), "divergeCount").isGreaterThan(0);

      try (TestClaudeTool scope = new TestClaudeTool(mainRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);
        String result = cmd.execute(mainRepo.toString(), issueBranch, "test-session", "v2.1",
          issueWorktree.toString(), pluginRoot.toString());

        requireThat(result, "result").contains("\"status\" : \"success\"");
        requireThat(result, "result").contains("\"issue_id\" : \"" + issueBranch + "\"");

        // Verify v2.1 now contains the issue commit
        String v21Log = TestUtils.runGitCommandWithOutput(mainRepo, "log", "--oneline", "v2.1");
        requireThat(v21Log, "v21Log").contains("Issue commit");
        requireThat(v21Log, "v21Log").contains("Target branch advance commit");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that execute throws IOException when projectPath has uncommitted changes.
   * <p>
   * An untracked file in the main workspace causes {@code git status --porcelain} to return
   * non-empty output, which must be detected before the merge begins.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = "(?s).*uncommitted changes.*dirty-file\\.txt.*")
  public void executeThrowsWhenMainWorkspaceIsDirty() throws IOException
  {
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path mainRepo = Files.createTempDirectory("main-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try
    {
      // Initialize bare origin
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      // Create main repo with initial commit
      TestUtils.runGit(mainRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(mainRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(mainRepo, "config", "user.name", "Test User");
      Files.writeString(mainRepo.resolve("README.md"), "initial");
      TestUtils.runGit(mainRepo, "add", "README.md");
      TestUtils.runGit(mainRepo, "commit", "-m", "Initial commit");

      // Add origin remote and push
      TestUtils.runGit(mainRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(mainRepo, "push", "-u", "origin", "v2.1");

      // Create the issue branch via worktree
      String issueBranch = "dirty-workspace-issue";
      Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

      // Set up .cat structure in main repo
      Files.createDirectories(mainRepo.resolve(".cat"));

      // Introduce an uncommitted (untracked) file in the main workspace to make it dirty
      Files.writeString(mainRepo.resolve("dirty-file.txt"), "uncommitted content");

      try (TestClaudeTool scope = new TestClaudeTool(mainRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);
        cmd.execute(mainRepo.toString(), issueBranch, "test-session", "v2.1",
          issueWorktree.toString(), pluginRoot.toString());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that after execute completes, the main working tree is synced with the merged
   * commits.
   * <p>
   * The merge uses {@code git merge --ff-only} which atomically updates the ref, index, and
   * working tree. Files added in the issue branch should appear in the main working tree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeSyncsMainWorkingTree() throws IOException
  {
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path mainRepo = Files.createTempDirectory("main-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      // Initialize bare origin
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      // Create main repo with initial commit
      TestUtils.runGit(mainRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(mainRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(mainRepo, "config", "user.name", "Test User");
      Files.writeString(mainRepo.resolve("README.md"), "initial");
      TestUtils.runGit(mainRepo, "add", "README.md");
      TestUtils.runGit(mainRepo, "commit", "-m", "Initial commit");

      // Add origin remote and push
      TestUtils.runGit(mainRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(mainRepo, "push", "-u", "origin", "v2.1");

      // Create the issue branch via worktree
      String issueBranch = "my-wt-sync-issue";
      Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");

      // Add a new file in the worktree issue branch
      Files.writeString(issueWorktree.resolve("new-feature.txt"), "new feature content");
      TestUtils.runGit(issueWorktree, "add", "new-feature.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Add new feature file");

      // Set up .cat structure in main repo
      Path catDir = mainRepo.resolve(".cat");
      Files.createDirectories(catDir);

      try (TestClaudeTool scope = new TestClaudeTool(mainRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);
        String result = cmd.execute(mainRepo.toString(), issueBranch, "test-session", "v2.1",
          issueWorktree.toString(), pluginRoot.toString());

        requireThat(result, "result").contains("\"status\" : \"success\"");

        // Verify the ref was updated (v2.1 now includes the issue commit)
        String v21Log = TestUtils.runGitCommandWithOutput(mainRepo, "log", "--oneline", "v2.1");
        requireThat(v21Log, "v21Log").contains("Add new feature file");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the worktree dirtiness check prevents merge when the issue worktree has uncommitted
   * changes.
   * <p>
   * The worktree isolation model ensures that each issue worktree is independent. Before merge, the
   * worktree must be clean (no uncommitted changes) to guarantee the merge state is consistent with
   * what the user sees on their file system. This test validates the pre-merge safety check.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*uncommitted changes.*")
  public void executeThrowsWhenWorktreeIsDirty() throws IOException
  {
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path localRepo = Files.createTempDirectory("local-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      // Initialize bare origin
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      // Create local repo
      TestUtils.runGit(localRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(localRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(localRepo, "config", "user.name", "Test User");

      // Create initial commit in local
      Files.writeString(localRepo.resolve("README.md"), "initial");
      TestUtils.runGit(localRepo, "add", "README.md");
      TestUtils.runGit(localRepo, "commit", "-m", "Initial commit");

      // Add origin remote and push
      TestUtils.runGit(localRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(localRepo, "push", "-u", "origin", "v2.1");

      // Create the issue worktree and make a commit
      String issueBranch = "my-dirty-issue";
      Path issueWorktree = TestUtils.createWorktree(localRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");

      // Add and commit issue work
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

      // Make the worktree dirty by adding an uncommitted file
      Files.writeString(issueWorktree.resolve("uncommitted.txt"), "dirty change");

      // Set up .cat structure in local repo
      Path catDir = localRepo.resolve(".cat");
      Files.createDirectories(catDir);

      try (TestClaudeTool scope = new TestClaudeTool(localRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        // This should throw because the worktree has uncommitted changes
        cmd.execute(localRepo.toString(), issueBranch, "test-session", "v2.1",
          issueWorktree.toString(), pluginRoot.toString());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(localRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a monolithic merge-and-cleanup refuses to run when the plugin root is inside the
   * worktree that cleanup would remove.
   * <p>
   * The failure must occur before the target branch is merged so an unsafe invocation cannot leave a
   * partially merged issue behind.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsPluginRootInsideDeletedWorktreeBeforeMerge() throws IOException
  {
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path mainRepo = Files.createTempDirectory("main-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");

    try
    {
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      TestUtils.runGit(mainRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(mainRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(mainRepo, "config", "user.name", "Test User");
      Files.writeString(mainRepo.resolve("README.md"), "initial");
      TestUtils.runGit(mainRepo, "add", "README.md");
      TestUtils.runGit(mainRepo, "commit", "-m", "Initial commit");
      TestUtils.runGit(mainRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(mainRepo, "push", "-u", "origin", "v2.1");

      String issueBranch = "unsafe-plugin-root";
      Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

      Files.createDirectories(mainRepo.resolve(".cat"));
      Path unsafePluginRoot = issueWorktree.resolve("client/distribution/target/engine/codex");
      Files.createDirectories(unsafePluginRoot);

      try (TestClaudeTool scope = new TestClaudeTool(mainRepo, unsafePluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);
        try
        {
          cmd.execute(mainRepo.toString(), issueBranch, "test-session", "v2.1",
            issueWorktree.toString(), unsafePluginRoot.toString());
          throw new AssertionError("Expected unsafe plugin root to be rejected");
        }
        catch (IOException e)
        {
          requireThat(e.getMessage(), "message").contains("Refusing");
          requireThat(e.getMessage(), "message").contains("inside the worktree");
        }
      }

      String v21Log = TestUtils.runGitCommandWithOutput(mainRepo, "log", "--oneline", "v2.1");
      requireThat(v21Log, "v21Log").doesNotContain("Issue commit");
      requireThat(Files.isDirectory(issueWorktree), "worktreeStillExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
    }
  }

  /**
   * Verifies that merge and cleanup can run as separate, retryable phases.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runSupportsMergeOnlyThenCleanupOnly() throws IOException
  {
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path mainRepo = Files.createTempDirectory("main-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path pluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      TestUtils.runGit(mainRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(mainRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(mainRepo, "config", "user.name", "Test User");
      Files.writeString(mainRepo.resolve("README.md"), "initial");
      TestUtils.runGit(mainRepo, "add", "README.md");
      TestUtils.runGit(mainRepo, "commit", "-m", "Initial commit");
      TestUtils.runGit(mainRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(mainRepo, "push", "-u", "origin", "v2.1");

      String issueBranch = "split-phase-issue";
      Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");
      String expectedCommit = TestUtils.runGitCommandWithOutput(issueWorktree, "rev-parse", "HEAD").strip();

      Files.createDirectories(mainRepo.resolve(".cat"));

      try (TestClaudeTool scope = new TestClaudeTool(mainRepo, pluginRoot))
      {
        ByteArrayOutputStream mergeBuffer = new ByteArrayOutputStream();
        MergeAndCleanup.run(scope, new String[]{
          mainRepo.toString(),
          issueBranch,
          "test-session",
          "v2.1",
          "--worktree",
          issueWorktree.toString(),
          "--merge-only"
        }, new PrintStream(mergeBuffer, true, StandardCharsets.UTF_8));
        JsonNode mergeJson = scope.getJsonMapper().readTree(mergeBuffer.toString(StandardCharsets.UTF_8));
        requireThat(mergeJson.get("status").asString(), "mergeStatus").isEqualTo("success");
        requireThat(mergeJson.get("phase").asString(), "mergePhase").isEqualTo("merge");
        requireThat(Files.isDirectory(issueWorktree), "worktreeStillExistsAfterMerge").isTrue();
        requireThat(TestUtils.runGitCommandWithOutput(mainRepo, "branch", "--list", issueBranch),
          "branchList").contains(issueBranch);

        ByteArrayOutputStream failedCleanupBuffer = new ByteArrayOutputStream();
        MergeAndCleanup.run(scope, new String[]{
          mainRepo.toString(),
          issueBranch,
          "test-session",
          "v2.1",
          "--worktree",
          issueWorktree.toString(),
          "--cleanup-only",
          "--expected-commit",
          "0000000"
        }, new PrintStream(failedCleanupBuffer, true, StandardCharsets.UTF_8));
        String failedCleanupOutput = failedCleanupBuffer.toString(StandardCharsets.UTF_8);
        requireThat(failedCleanupOutput, "failedCleanupOutput").contains("Refusing cleanup");
        requireThat(failedCleanupOutput, "failedCleanupOutput").contains("not expected merged commit");
        requireThat(Files.isDirectory(issueWorktree), "worktreeStillExistsAfterFailedCleanup").isTrue();
        requireThat(TestUtils.runGitCommandWithOutput(mainRepo, "branch", "--list", issueBranch),
          "branchExistsAfterFailedCleanup").contains(issueBranch);

        ByteArrayOutputStream cleanupBuffer = new ByteArrayOutputStream();
        MergeAndCleanup.run(scope, new String[]{
          mainRepo.toString(),
          issueBranch,
          "test-session",
          "v2.1",
          "--worktree",
          issueWorktree.toString(),
          "--cleanup-only",
          "--expected-commit",
          expectedCommit
        }, new PrintStream(cleanupBuffer, true, StandardCharsets.UTF_8));
        JsonNode cleanupJson = scope.getJsonMapper().readTree(cleanupBuffer.toString(StandardCharsets.UTF_8));
        requireThat(cleanupJson.get("status").asString(), "cleanupStatus").isEqualTo("success");
        requireThat(cleanupJson.get("phase").asString(), "cleanupPhase").isEqualTo("cleanup");

        ByteArrayOutputStream retryCleanupBuffer = new ByteArrayOutputStream();
        MergeAndCleanup.run(scope, new String[]{
          mainRepo.toString(),
          issueBranch,
          "test-session",
          "v2.1",
          "--worktree",
          issueWorktree.toString(),
          "--cleanup-only",
          "--expected-commit",
          expectedCommit
        }, new PrintStream(retryCleanupBuffer, true, StandardCharsets.UTF_8));
        JsonNode retryCleanupJson = scope.getJsonMapper().
          readTree(retryCleanupBuffer.toString(StandardCharsets.UTF_8));
        requireThat(retryCleanupJson.get("status").asString(), "retryCleanupStatus").isEqualTo("success");
        requireThat(retryCleanupJson.get("phase").asString(), "retryCleanupPhase").isEqualTo("cleanup");
      }

      String v21Log = TestUtils.runGitCommandWithOutput(mainRepo, "log", "--oneline", "v2.1");
      requireThat(v21Log, "v21Log").contains("Issue commit");
      requireThat(Files.notExists(issueWorktree), "worktreeRemoved").isTrue();
      requireThat(TestUtils.runGitCommandWithOutput(mainRepo, "branch", "--list", issueBranch).strip(),
        "branchRemoved").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that cleanup-only refuses to run when the plugin root is inside the worktree that
   * cleanup would remove.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runCleanupOnlyRejectsPluginRootInsideDeletedWorktree() throws IOException
  {
    Path originRepo = Files.createTempDirectory("origin-repo-");
    Path mainRepo = Files.createTempDirectory("main-repo-");
    Path worktreesDir = Files.createTempDirectory("worktrees-");
    Path stablePluginRoot = Files.createTempDirectory("test-plugin");

    try
    {
      TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

      TestUtils.runGit(mainRepo, "init", "--initial-branch=v2.1");
      TestUtils.runGit(mainRepo, "config", "user.email", "test@example.com");
      TestUtils.runGit(mainRepo, "config", "user.name", "Test User");
      Files.writeString(mainRepo.resolve("README.md"), "initial");
      TestUtils.runGit(mainRepo, "add", "README.md");
      TestUtils.runGit(mainRepo, "commit", "-m", "Initial commit");
      TestUtils.runGit(mainRepo, "remote", "add", "origin", originRepo.toString());
      TestUtils.runGit(mainRepo, "push", "-u", "origin", "v2.1");

      String issueBranch = "unsafe-cleanup-only";
      Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);
      TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
      TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");
      Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
      TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
      TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");
      String expectedCommit = TestUtils.runGitCommandWithOutput(issueWorktree, "rev-parse", "HEAD").strip();

      Files.createDirectories(mainRepo.resolve(".cat"));

      try (TestClaudeTool scope = new TestClaudeTool(mainRepo, stablePluginRoot))
      {
        ByteArrayOutputStream mergeBuffer = new ByteArrayOutputStream();
        MergeAndCleanup.run(scope, new String[]{
          mainRepo.toString(),
          issueBranch,
          "test-session",
          "v2.1",
          "--worktree",
          issueWorktree.toString(),
          "--merge-only"
        }, new PrintStream(mergeBuffer, true, StandardCharsets.UTF_8));
        JsonNode mergeJson = scope.getJsonMapper().readTree(mergeBuffer.toString(StandardCharsets.UTF_8));
        requireThat(mergeJson.get("status").asString(), "mergeStatus").isEqualTo("success");
      }

      Path unsafePluginRoot = issueWorktree.resolve("client/distribution/target/engine/codex");
      Files.createDirectories(unsafePluginRoot);

      try (TestClaudeTool scope = new TestClaudeTool(mainRepo, unsafePluginRoot))
      {
        ByteArrayOutputStream cleanupBuffer = new ByteArrayOutputStream();
        MergeAndCleanup.run(scope, new String[]{
          mainRepo.toString(),
          issueBranch,
          "test-session",
          "v2.1",
          "--worktree",
          issueWorktree.toString(),
          "--cleanup-only",
          "--expected-commit",
          expectedCommit
        }, new PrintStream(cleanupBuffer, true, StandardCharsets.UTF_8));
        String cleanupOutput = cleanupBuffer.toString(StandardCharsets.UTF_8);
        requireThat(cleanupOutput, "cleanupOutput").contains("Refusing");
        requireThat(cleanupOutput, "cleanupOutput").contains("inside the worktree");
      }

      requireThat(Files.isDirectory(issueWorktree), "worktreeStillExists").isTrue();
      requireThat(TestUtils.runGitCommandWithOutput(mainRepo, "branch", "--list", issueBranch),
        "branchStillExists").contains(issueBranch);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktreesDir);
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(originRepo);
      TestUtils.deleteDirectoryRecursively(stablePluginRoot);
    }
  }
}
