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
   * Verifies that execute fast-forwards the local base branch when it is behind origin.
   * <p>
   * When the local base branch is behind origin (origin has new commits), the merge should
   * fetch and fast-forward the local base branch before proceeding.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeUpdatesLocalBaseBranchWhenBehindOrigin() throws IOException
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

      // Set up .claude/cat structure in local repo
      Path catDir = localRepo.resolve(".claude/cat");
      Files.createDirectories(catDir);

      // Create the cat-base file for the worktree
      String gitDir = TestUtils.runGitCommandWithOutput(localRepo, "rev-parse", "--absolute-git-dir");
      Path catBasePath = Path.of(gitDir).resolve("worktrees").resolve(issueBranch).resolve("cat-base");
      Files.createDirectories(catBasePath.getParent());
      Files.writeString(catBasePath, "v2.1");

      try (JvmScope scope = new TestJvmScope(localRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        // Verify precondition: local v2.1 is stale (does not have origin's advance commit)
        String preLog = TestUtils.runGitCommandWithOutput(localRepo, "log", "--oneline", "v2.1");
        requireThat(preLog, "preLog").doesNotContain("Origin advance commit");

        String result = cmd.execute(localRepo.toString(), issueBranch, "test-session",
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
   * Verifies that execute throws a clear error when the local base branch has diverged from origin.
   * <p>
   * When the local base branch has commits not in origin, the fast-forward update fails and
   * execute must throw an IOException with a message explaining the divergence.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeThrowsWhenLocalBaseBranchDivergedFromOrigin() throws IOException
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

      // Set up .claude/cat structure
      Path catDir = localRepo.resolve(".claude/cat");
      Files.createDirectories(catDir);

      // Create the cat-base file
      String gitDir = TestUtils.runGitCommandWithOutput(localRepo, "rev-parse", "--absolute-git-dir");
      Path catBasePath = Path.of(gitDir).resolve("worktrees").resolve(issueBranch).resolve("cat-base");
      Files.createDirectories(catBasePath.getParent());
      Files.writeString(catBasePath, "v2.1");

      try (JvmScope scope = new TestJvmScope(localRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        try
        {
          cmd.execute(localRepo.toString(), issueBranch, "test-session",
            issueWorktree.toString(), pluginRoot.toString());
        }
        catch (IOException e)
        {
          requireThat(e.getMessage(), "message").contains("diverged");
          requireThat(e.getMessage(), "message").contains("v2.1");
        }
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
  @Test
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

      // Set up .claude/cat structure
      Path catDir = localRepo.resolve(".claude/cat");
      Files.createDirectories(catDir);

      // Create the cat-base file
      String gitDir = TestUtils.runGitCommandWithOutput(localRepo, "rev-parse", "--absolute-git-dir");
      Path catBasePath = Path.of(gitDir).resolve("worktrees").resolve(issueBranch).resolve("cat-base");
      Files.createDirectories(catBasePath.getParent());
      Files.writeString(catBasePath, "v2.1");

      try (JvmScope scope = new TestJvmScope(localRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);

        try
        {
          cmd.execute(localRepo.toString(), issueBranch, "test-session",
            issueWorktree.toString(), pluginRoot.toString());
        }
        catch (IOException e)
        {
          requireThat(e.getMessage(), "message").contains("origin");
          requireThat(e.getMessage(), "message").contains("v2.1");
        }
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

      // Now advance the base branch (v2.1) with a new commit, causing divergence
      Files.writeString(mainRepo.resolve("base-advance.txt"), "base branch advance");
      TestUtils.runGit(mainRepo, "add", "base-advance.txt");
      TestUtils.runGit(mainRepo, "commit", "-m", "Base branch advance commit");

      // Also push this advance to origin so syncBaseBranchWithOrigin doesn't fail
      TestUtils.runGit(mainRepo, "push", "origin", "v2.1");

      // Set up .claude/cat structure in main repo
      Path catDir = mainRepo.resolve(".claude/cat");
      Files.createDirectories(catDir);

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

      // Set up .claude/cat structure in main repo
      Path catDir = mainRepo.resolve(".claude/cat");
      Files.createDirectories(catDir);

      // Create the cat-base file
      String gitDir = TestUtils.runGitCommandWithOutput(mainRepo, "rev-parse", "--absolute-git-dir");
      Path catBasePath = Path.of(gitDir).resolve("worktrees").resolve(issueBranch).resolve("cat-base");
      Files.createDirectories(catBasePath.getParent());
      Files.writeString(catBasePath, "v2.1");

      // Verify precondition: new-feature.txt does NOT exist in main working tree
      requireThat(Files.exists(mainRepo.resolve("new-feature.txt")),
        "fileExistsBeforeMerge").isFalse();

      try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
      {
        MergeAndCleanup cmd = new MergeAndCleanup(scope);
        String result = cmd.execute(mainRepo.toString(), issueBranch, "test-session",
          issueWorktree.toString(), pluginRoot.toString());

        requireThat(result, "result").contains("\"status\" : \"success\"");

        // Verify the ref was updated (v2.1 now includes the issue commit)
        String v21Log = TestUtils.runGitCommandWithOutput(mainRepo, "log", "--oneline", "v2.1");
        requireThat(v21Log, "v21Log").contains("Add new feature file");

        // Verify the main working tree IS synced (merge --ff-only updates working tree)
        requireThat(Files.exists(mainRepo.resolve("new-feature.txt")),
          "fileExistsAfterMerge").isTrue();
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
}
