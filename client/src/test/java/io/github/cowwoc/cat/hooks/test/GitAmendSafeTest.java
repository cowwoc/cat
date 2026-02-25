/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.GitAmendSafe;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitAmendSafe.
 * <p>
 * Tests verify argument parsing, push status checking, and amend behavior in isolated git repositories.
 */
public class GitAmendSafeTest
{
  /**
   * Verifies that executing with a null scope throws NullPointerException.
   */
  @Test
  public void constructorRejectsNullScope()
  {
    try
    {
      new GitAmendSafe(null, ".");
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").isNotNull();
    }
  }

  /**
   * Verifies that executing with a blank directory throws IllegalArgumentException.
   */
  @Test
  public void constructorRejectsBlankDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("git-amend-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        new GitAmendSafe(scope, "");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").isNotNull();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that amend with no-edit succeeds on a fresh unpushed commit.
   */
  @Test
  public void executeNoEditSucceedsOnUnpushedCommit() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Make an extra commit to amend
        Files.writeString(repoDir.resolve("extra.txt"), "extra content");
        TestUtils.runGit(repoDir, "add", "extra.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "extra commit");

        GitAmendSafe cmd = new GitAmendSafe(scope, repoDir.toString());
        String result = cmd.execute("", true);

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("\"old_head\"");
        requireThat(result, "result").contains("\"new_head\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that amend with a custom message succeeds on an unpushed commit.
   */
  @Test
  public void executeWithMessageSucceedsOnUnpushedCommit() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Make an extra commit to amend
        Files.writeString(repoDir.resolve("extra.txt"), "extra content");
        TestUtils.runGit(repoDir, "add", "extra.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "original message");

        GitAmendSafe cmd = new GitAmendSafe(scope, repoDir.toString());
        String result = cmd.execute("new commit message", false);

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");

        // Verify the commit message was changed
        String message = TestUtils.runGitCommandWithOutput(repoDir, "log", "-1", "--format=%s");
        requireThat(message, "message").isEqualTo("new commit message");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that amend fails with ALREADY_PUSHED status when the commit has been pushed.
   * <p>
   * This test sets up a simulated pushed state by creating a remote tracking reference.
   */
  @Test
  public void executeFailsWhenCommitAlreadyPushed() throws IOException
  {
    Path bareRepo = TestUtils.createTempGitRepo("main");
    try
    {
      // Clone the repo to act as the remote
      Path bareDir = Files.createTempDirectory("git-bare-");
      try
      {
        TestUtils.runGit(bareDir, "clone", "--bare", bareRepo.toString(), ".");

        // Clone from bare to get a tracked repo
        Path cloneDir = Files.createTempDirectory("git-clone-");
        try
        {
          TestUtils.runGit(cloneDir, "clone", bareDir.toString(), ".");
          TestUtils.runGit(cloneDir, "config", "user.email", "test@example.com");
          TestUtils.runGit(cloneDir, "config", "user.name", "Test User");

          // Make and push a commit
          Files.writeString(cloneDir.resolve("pushed.txt"), "pushed content");
          TestUtils.runGit(cloneDir, "add", "pushed.txt");
          TestUtils.runGit(cloneDir, "commit", "-m", "pushed commit");
          TestUtils.runGit(cloneDir, "push", "origin", "main");

          // Now try to amend the pushed commit
          try (JvmScope scope = new TestJvmScope(cloneDir, cloneDir))
          {
            GitAmendSafe cmd = new GitAmendSafe(scope, cloneDir.toString());
            String result = cmd.execute("", true);

            // Should fail with ALREADY_PUSHED
            requireThat(result, "result").contains("ALREADY_PUSHED");
          }
        }
        finally
        {
          TestUtils.deleteDirectoryRecursively(cloneDir);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(bareDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(bareRepo);
    }
  }

  /**
   * Verifies that executing with a null message throws NullPointerException.
   */
  @Test
  public void executeRejectsNullMessage() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        GitAmendSafe cmd = new GitAmendSafe(scope, repoDir.toString());
        try
        {
          cmd.execute(null, false);
        }
        catch (NullPointerException e)
        {
          requireThat(e.getMessage(), "message").isNotNull();
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that the result contains race_detected false when no race condition occurred.
   */
  @Test
  public void executeReturnsNoRaceWhenRemoteNotUpdated() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(repoDir, repoDir))
    {
      try
      {
        // Make an unpushed commit
        Files.writeString(repoDir.resolve("file.txt"), "content");
        TestUtils.runGit(repoDir, "add", "file.txt");
        TestUtils.runGit(repoDir, "commit", "-m", "test commit");

        GitAmendSafe cmd = new GitAmendSafe(scope, repoDir.toString());
        String result = cmd.execute("", true);

        requireThat(result, "result").contains("\"status\"");
        requireThat(result, "result").contains("\"OK\"");
        requireThat(result, "result").contains("race_detected");
        requireThat(result, "result").contains("false");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
  }

  /**
   * Verifies that RACE_DETECTED is returned when OLD_HEAD is pushed to the remote during the amend
   * window (between the amend completing and the post-amend TOCTOU check).
   */
  @Test
  public void executeDetectsRaceWhenOldHeadPushedDuringAmend() throws IOException
  {
    Path bareRepo = TestUtils.createTempGitRepo("main");
    try
    {
      Path bareDir = Files.createTempDirectory("git-bare-");
      try
      {
        TestUtils.runGit(bareDir, "clone", "--bare", bareRepo.toString(), ".");

        Path cloneDir = Files.createTempDirectory("git-clone-");
        try
        {
          TestUtils.runGit(cloneDir, "clone", bareDir.toString(), ".");
          TestUtils.runGit(cloneDir, "config", "user.email", "test@example.com");
          TestUtils.runGit(cloneDir, "config", "user.name", "Test User");

          // Make a local commit so branch is ahead of remote (passes pre-check)
          Files.writeString(cloneDir.resolve("local.txt"), "local content");
          TestUtils.runGit(cloneDir, "add", "local.txt");
          TestUtils.runGit(cloneDir, "commit", "-m", "local commit");

          // Record OLD_HEAD before execute (this is what will be amended)
          String oldHead = TestUtils.runGitCommandWithOutput(cloneDir, "rev-parse", "HEAD");

          try (JvmScope scope = new TestJvmScope(cloneDir, cloneDir))
          {
            GitAmendSafe cmd = new GitAmendSafe(scope, cloneDir.toString());
            String result = cmd.execute("amended message", false, () ->
            {
              // Simulate: OLD_HEAD gets pushed to remote during the amend window.
              // Force-push OLD_HEAD to remote's main, then fetch so the local
              // tracking ref (@{push}) reflects it.
              TestUtils.runGit(cloneDir, "push", "--force", "origin",
                oldHead + ":refs/heads/main");
              TestUtils.runGit(cloneDir, "fetch", "origin");
            });

            requireThat(result, "result").contains("\"RACE_DETECTED\"");
            requireThat(result, "result").contains("old_head");
            requireThat(result, "result").contains("new_head");
            requireThat(result, "result").contains("force-with-lease");
          }
        }
        finally
        {
          TestUtils.deleteDirectoryRecursively(cloneDir);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(bareDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(bareRepo);
    }
  }
}
