/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.BlockUnsafeRemoval;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockUnsafeRemoval}.
 */
public final class BlockUnsafeRemovalTest
{
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = worktreePath.toString();
      String command = "git worktree remove --force " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = targetPath.toString();
      String command = "rm -rf " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
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
   * Verifies that removal blocks when the current working directory is inside the deletion target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmBlocksWhenCwdInDeletionTarget() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = subDir.toString();
      String command = "rm -rf " + tempDir;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that removal blocks when the target is the main git worktree root.
   * <p>
   * The current working directory is outside the target, demonstrating that the block is due to
   * the MAIN_WORKTREE protection, not the CURRENT_WORKING_DIRECTORY protection.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmBlocksWhenDeletingMainWorktree() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path safeDir = Files.createTempDirectory("safe-");

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = safeDir.toString();  // CWD is outside the deletion target
      String command = "rm -rf " + tempDir;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("main git worktree");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(safeDir);
    }
  }

  /**
   * Verifies that removal blocks when a fresh locked worktree (< 4 hours old) would be deleted.
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

    // Create lock file owned by a different session
    // Use a clock fixed 1 hour after lock creation so the lock appears fresh
    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("task-123.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)), ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("locked by another agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the owning session can remove its own locked worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void owningSessionCanRemoveLockedWorktree() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-456");
    Files.createDirectories(worktreePath);

    // Create lock file owned by the SAME session
    Path lockFile = locksDir.resolve("task-456.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "my-session",
        "worktrees": {"%s": ""},
        "created_at": 1771266833
      }""".formatted(worktreePath.toString()));

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = tempDir.toString();
      String command = "git worktree remove " + worktreePath + " --force";

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a different session cannot remove another session's fresh locked worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void differentSessionCannotRemoveLockedWorktree() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-789");
    Files.createDirectories(worktreePath);

    // Create lock file owned by a different session
    // Use a clock fixed 1 hour after lock creation so the lock appears fresh
    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("task-789.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)), ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      String command = "git worktree remove " + worktreePath + " --force";

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
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
   * Verifies that a stale lock (older than 4 hours) from another session does not block removal.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void staleLockFromOtherSessionAllowsRemoval() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("stale-task");
    Files.createDirectories(worktreePath);

    // Create lock file owned by a different session with created_at in the past
    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("stale-task.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      // Clock is fixed 5 hours after lock creation, making the lock stale
      Clock staleClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(5)), ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, staleClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a fresh lock (less than 4 hours old) from another session blocks removal.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void freshLockFromOtherSessionBlocksRemoval() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("fresh-task");
    Files.createDirectories(worktreePath);

    // Create lock file owned by a different session
    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("fresh-task.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      // Clock is fixed 1 hour after lock creation, making the lock fresh (< 4 hours old)
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)), ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("locked by another agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a stale lock from the current session still allows removal (already excluded by session
   * check).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void staleLockFromCurrentSessionAllowsRemoval() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("my-stale-task");
    Files.createDirectories(worktreePath);

    // Create lock file owned by the SAME session with a stale timestamp
    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("my-stale-task.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "my-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      // Clock is fixed 5 hours after lock creation (stale), but session matches so already excluded
      Clock staleClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(5)), ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, staleClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = targetPath.toString();
      String command = "rm -r -f " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = targetPath.toString();
      String command = "rm " + targetPath + " -rf";

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = targetPath.toString();
      String command = "rm -f " + targetPath + " -r";

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = targetPath.toString();
      String command = "rm --recursive " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = targetPath.toString();
      String command = "rm -Rf " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("working directory is inside the deletion target");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a lock exactly 4 hours old is stale (allowed), since staleness requires age > 4 hours exactly.
   * The threshold is strictly greater than 4 hours, so a lock at exactly 14400 seconds is NOT stale.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lockAtExactly4HoursIsNotStale() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("boundary-task");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("boundary-task.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      // Clock is fixed exactly 14400 seconds (4 hours) after lock creation
      // age.compareTo(threshold) == 0, so isStale() returns false → lock is protected
      Clock boundaryClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(4)), ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, boundaryClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a lock at 4 hours minus 1 second is fresh (blocked).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lockAtJustUnder4HoursIsFresh() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("fresh-boundary-task");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("fresh-boundary-task.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      // Clock is fixed 14399 seconds (4 hours minus 1 second) after lock creation
      Instant boundaryInstant = Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(4)).minusSeconds(1);
      Clock boundaryClock = Clock.fixed(boundaryInstant, ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, boundaryClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a lock at 4 hours plus 1 second is stale (allowed).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lockAtJustOver4HoursIsStale() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("stale-boundary-task");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("stale-boundary-task.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      // Clock is fixed 14401 seconds (4 hours plus 1 second) after lock creation
      Instant boundaryInstant = Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(4)).plusSeconds(1);
      Clock boundaryClock = Clock.fixed(boundaryInstant, ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, boundaryClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a lock file without created_at field is treated as protected (fail-safe behavior).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void staleLockWithMissingCreatedAtIsProtected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("no-created-at-task");
    Files.createDirectories(worktreePath);

    // Lock file without created_at field
    Path lockFile = locksDir.resolve("no-created-at-task.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "other-session",
        "worktrees": {"%s": ""}
      }""".formatted(worktreePath.toString()));

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      // No created_at means isStale() returns false, so the lock is treated as fresh and the removal is blocked
      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a lock file without session_id is treated as not owned by the current session,
   * and since the lock is fresh, the removal is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lockWithMissingSessionIdFromOtherSessionBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("no-session-id-task");
    Files.createDirectories(worktreePath);

    // Lock file without session_id (only has created_at with a fresh timestamp)
    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("no-session-id-task.lock");
    Files.writeString(lockFile, """
      {
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      // Clock is fixed 1 hour after lock creation so the lock appears fresh (< 4 hours old)
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)), ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "my-session");

      // Missing session_id means isOwnedBySession() returns false (not mine),
      // and since the lock is fresh, isStale() returns false, so the removal is blocked.
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("locked by another agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that deletion via a symlink path is blocked when the real path is protected.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmBlocksWhenSymlinkPointsToProtectedPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path realTarget = tempDir.resolve("real-target");
    Files.createDirectories(realTarget);
    Path symlink = tempDir.resolve("symlink-to-target");
    Files.createSymbolicLink(symlink, realTarget);

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      // CWD is inside the real directory, but deletion is via symlink path
      String workingDirectory = realTarget.toString();
      String command = "rm -rf " + symlink;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("UNSAFE");
      requireThat(result.reason(), "reason").contains("UNSAFE DIRECTORY REMOVAL BLOCKED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that deletion via a symlink path is allowed when the real path is not protected.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void rmAllowsWhenSymlinkPointsToNonProtectedPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path safeTarget = tempDir.resolve("safe-target");
    Files.createDirectories(safeTarget);
    Path symlink = tempDir.resolve("symlink-to-safe");
    Files.createSymbolicLink(symlink, safeTarget);

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      // CWD is the tempDir (the main worktree root), not the symlink target
      String workingDirectory = tempDir.toString();
      String command = "rm -rf " + symlink;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the error message includes the current working directory.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void errorMessageIncludesCwd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path targetPath = tempDir.resolve("target-to-remove");
    Files.createDirectories(targetPath);

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = targetPath.toString();
      String command = "rm -rf " + targetPath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      // Verify working directory label is present in the error message (with the working directory value)
      String reason = result.reason();
      int workingDirIndex = reason.indexOf("Working directory:");
      requireThat(workingDirIndex, "workingDirIndex").isGreaterThanOrEqualTo(0);
      String workingDirLine = reason.substring(workingDirIndex, reason.indexOf('\n', workingDirIndex));
      requireThat(workingDirLine, "workingDirLine").contains(workingDirectory);
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

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
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

  /**
   * Test A: Verifies that a lock with agent_id blocks removal when no CAT_AGENT_ID is in the command
   * (fail-safe: agent must self-identify).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveBlockedWhenNoAgentId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentA");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    Path lockFile = locksDir.resolve("task-agentA.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "aaaaaaaa-0000-0000-0000-000000000001",
        "worktrees": {"%s": "aaaaaaaa-0000-0000-0000-000000000001/subagents/xyz"},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)),
        ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      // No CAT_AGENT_ID prefix in command
      String command = "git worktree remove " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null,
        "aaaaaaaa-0000-0000-0000-000000000001");

      requireThat(result.blocked(), "blocked").isTrue();
      String reason = result.reason();
      requireThat(reason, "reason").contains("locked by another agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Test B: Verifies that removal is allowed when CAT_AGENT_ID in the command matches the lock's agent_id
   * (owner deleting own worktree).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveAllowedWhenAgentIdMatchesLock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentB");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    String ownerAgentId = "aaaaaaaa-0000-0000-0000-000000000001/subagents/abc123";
    Path lockFile = locksDir.resolve("task-agentB.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "aaaaaaaa-0000-0000-0000-000000000001",
        "worktrees": {"%s": "%s"},
        "created_at": %d
      }""".formatted(worktreePath.toString(), ownerAgentId, lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)),
        ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      // Matching CAT_AGENT_ID prefix
      String command = "CAT_AGENT_ID=" + ownerAgentId + " git worktree remove " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null,
        "aaaaaaaa-0000-0000-0000-000000000001");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Test C: Verifies that removal is blocked when CAT_AGENT_ID in the command does not match the lock's
   * agent_id (sibling agent protection).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveBlockedWhenAgentIdMismatch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentC");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    String lockOwnerAgentId = "aaaaaaaa-0000-0000-0000-000000000001/subagents/ownerX";
    String commandAgentId = "aaaaaaaa-0000-0000-0000-000000000001/subagents/siblingY";
    Path lockFile = locksDir.resolve("task-agentC.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "aaaaaaaa-0000-0000-0000-000000000001",
        "worktrees": {"%s": "%s"},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockOwnerAgentId, lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)),
        ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      // Different agent_id in command than in lock
      String command = "CAT_AGENT_ID=" + commandAgentId + " git worktree remove " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null,
        "aaaaaaaa-0000-0000-0000-000000000001");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("locked by another agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Test D: Verifies that removal is allowed when no lock exists and no CAT_AGENT_ID (unowned worktree).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveAllowedWhenNoLock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentD");
    Files.createDirectories(worktreePath);

    try (JvmScope scope = new TestJvmScope())
    {
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope);
      String workingDirectory = tempDir.toString();
      // No lock file, no CAT_AGENT_ID
      String command = "git worktree remove " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Test E: Verifies that CWD-based block shows CWD-specific message, even when CAT_AGENT_ID matches.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveBlockedByCwdShowsCwdMessage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentE");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    String ownerAgentId = "aaaaaaaa-0000-0000-0000-000000000001/subagents/abc";
    Path lockFile = locksDir.resolve("task-agentE.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "aaaaaaaa-0000-0000-0000-000000000001",
        "worktrees": {"%s": "%s"},
        "created_at": %d
      }""".formatted(worktreePath.toString(), ownerAgentId, lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)),
        ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      // CWD is INSIDE the target (shell corruption scenario)
      String workingDirectory = worktreePath.toString();
      // Matching CAT_AGENT_ID but CWD is inside target
      String command = "CAT_AGENT_ID=" + ownerAgentId + " git worktree remove " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null,
        "aaaaaaaa-0000-0000-0000-000000000001");

      requireThat(result.blocked(), "blocked").isTrue();
      String reason = result.reason();
      requireThat(reason, "reason").contains("working directory is inside");
      // Ensure only CWD message is shown, not lock owner message
      requireThat(reason, "reason").doesNotContain("locked by another agent");
      requireThat(reason, "reason").doesNotContain("Lock owner");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Test F: Verifies that legacy locks without agent_id fall back to session_id comparison (backward
   * compatibility).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeRemoveFallsBackToSessionIdForLegacyLock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentF");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    String mySessionId = "bbbbbbbb-0000-0000-0000-000000000002";
    Path lockFile = locksDir.resolve("task-agentF.lock");
    // Lock with worktrees map but no agent IDs: falls back to session_id comparison
    Files.writeString(lockFile, """
      {
        "session_id": "%s",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(mySessionId, worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)),
        ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      String command = "git worktree remove " + worktreePath;

      // Same session as lock → allowed (backward compat fallback)
      BashHandler.Result result = handler.check(command, workingDirectory, null, null, mySessionId);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Test G: Verifies that lock-based blocks show "locked by another agent" message with actionable
   * guidance.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lockBlockedShowsLockMessage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentG");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    String lockOwnerAgentId = "aaaaaaaa-0000-0000-0000-000000000001/subagents/owner";
    Path lockFile = locksDir.resolve("task-agentG.lock");
    Files.writeString(lockFile, """
      {
        "session_id": "aaaaaaaa-0000-0000-0000-000000000001",
        "worktrees": {"%s": "%s"},
        "created_at": %d
      }""".formatted(worktreePath.toString(), lockOwnerAgentId, lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)),
        ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      // No CAT_AGENT_ID in command
      String command = "rm -rf " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null,
        "aaaaaaaa-0000-0000-0000-000000000001");

      requireThat(result.blocked(), "blocked").isTrue();
      String reason = result.reason();
      requireThat(reason, "reason").contains("locked by another agent");
      requireThat(reason, "reason").contains("CAT_AGENT_ID=");
      requireThat(reason, "reason").contains("issue-lock force-release");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Test H: Verifies that a legacy lock (no agent_id field) from a different session BLOCKS removal.
   * This proves backward compatibility: the fallback to session_id prevents removal from other sessions.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void legacyLockFromDifferentSessionBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectory(gitDir);
    Path locksDir = tempDir.resolve(".claude/cat/locks");
    Files.createDirectories(locksDir);
    Path worktreesDir = tempDir.resolve(".claude/cat/worktrees");
    Files.createDirectories(worktreesDir);
    Path worktreePath = worktreesDir.resolve("task-agentH");
    Files.createDirectories(worktreePath);

    long lockCreatedAt = 1_771_266_833L;
    String lockSessionId = "aaaaaaaa-0000-0000-0000-000000000001";
    Path lockFile = locksDir.resolve("task-agentH.lock");
    // Lock with no agent IDs in worktrees map: falls back to session_id comparison
    Files.writeString(lockFile, """
      {
        "session_id": "%s",
        "worktrees": {"%s": ""},
        "created_at": %d
      }""".formatted(lockSessionId, worktreePath.toString(), lockCreatedAt));

    try (JvmScope scope = new TestJvmScope())
    {
      Clock freshClock = Clock.fixed(Instant.ofEpochSecond(lockCreatedAt).plus(Duration.ofHours(1)),
        ZoneOffset.UTC);
      BlockUnsafeRemoval handler = new BlockUnsafeRemoval(scope, freshClock);
      String workingDirectory = tempDir.toString();
      // No CAT_AGENT_ID prefix in command, and called from DIFFERENT session
      String command = "git worktree remove " + worktreePath;

      BashHandler.Result result = handler.check(command, workingDirectory, null, null,
        "bbbbbbbb-1111-1111-1111-111111111111");

      requireThat(result.blocked(), "blocked").isTrue();
      String reason = result.reason();
      requireThat(reason, "reason").contains("locked by another agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
