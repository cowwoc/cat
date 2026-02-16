/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.bash.BlockUnsafeRemoval;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockUnsafeRemoval}.
 */
public final class BlockUnsafeRemovalTest
{
  /**
   * Verifies that git worktree remove with --force flag correctly extracts the path.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveWithLongFlagExtractsPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path worktreePath = tempDir.resolve("worktree-to-remove");
    Files.createDirectories(worktreePath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = tempDir.toString();
      String command = "git worktree remove --force " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that git worktree remove with -f flag correctly extracts the path.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveWithShortFlagExtractsPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path worktreePath = tempDir.resolve("worktree-to-remove");
    Files.createDirectories(worktreePath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = tempDir.toString();
      String command = "git worktree remove -f " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that git worktree remove blocks when CWD is inside the target worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveBlocksWhenCwdInsideTarget() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path worktreePath = tempDir.resolve("worktree-to-remove");
    Files.createDirectories(worktreePath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = worktreePath.toString();
      String command = "git worktree remove --force " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + worktreePath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rm -rf blocks when CWD is inside the target directory.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmRfBlocksWhenCwdInsideTarget() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = targetPath.toString();
      String command = "rm -rf " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + targetPath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that git worktree remove allows when CWD is outside the target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveAllowsWhenCwdOutsideTarget() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path worktreePath = tempDir.resolve("worktree-to-remove");
    Files.createDirectories(worktreePath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = tempDir.toString();
      String command = "git worktree remove --force " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that removal blocks when main worktree would be deleted.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmBlocksWhenDeletingMainWorktree() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = subDir.toString();
      String command = "rm -rf " + tempDir;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + tempDir.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that removal blocks when a locked worktree would be deleted.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmBlocksWhenDeletingLockedWorktree() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-123");
    Files.createDirectories(worktreePath);

    // Create lock file
    Path lockFile = locksDir.resolve("task-123.lock");
    Files.writeString(lockFile, "locked");

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + worktreePath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that removal allows when no protected paths are affected.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmAllowsWhenNoProtectedPathsAffected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path safeDir = tempDir.resolve("safe-to-delete");
    Files.createDirectories(safeDir);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + safeDir;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rm with separate flags blocks when CWD is inside the target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmWithSeparateFlags() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = targetPath.toString();
      String command = "rm -r -f " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + targetPath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rm with options after path blocks when CWD is inside the target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmWithOptionsAfterPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = targetPath.toString();
      String command = "rm " + targetPath + " -rf";

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + targetPath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rm with interleaved flags and paths blocks when CWD is inside the target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmWithInterleavedFlagsAndPaths() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = targetPath.toString();
      String command = "rm -f " + targetPath + " -r";

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + targetPath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rm with long option blocks when CWD is inside the target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmWithLongOption() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = targetPath.toString();
      String command = "rm --recursive " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + targetPath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rm with uppercase R flag blocks when CWD is inside the target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmWithUppercaseR() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = targetPath.toString();
      String command = "rm -Rf " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("Protected: " + targetPath.toRealPath());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rm without recursive flag allows deletion.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmWithoutRecursiveAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval();
      String workingDirectory = targetPath.toString();
      String command = "rm -f " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
