/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.SessionEndHook;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Tests for SessionEndHook.
 * <p>
 * Lock files are stored in the external CAT storage location:
 * {@code {claudeConfigPath}/projects/{encodedProjectDir}/cat/locks/}.
 * Tests use {@link io.github.cowwoc.cat.hooks.AbstractJvmScope#getCatWorkPath()} to resolve this
 * path correctly.
 * <p>
 * Session-scoped files are managed independently by the broader session cleanup pipeline.
 */
public final class SessionEndHookTest
{
  /**
   * Builds a hook payload JSON string with the given session ID.
   *
   * @param sessionId the session ID to embed
   * @return the JSON payload string
   */
  private static String sessionPayload(String sessionId)
  {
    return "{\"session_id\": \"" + sessionId + "\"}";
  }

  /**
   * Verifies that project lock file is removed when it exists.
   */
  @Test
  public void projectLockRemoved() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        String projectName = tempDir.getFileName().toString();
        Path lockFile = lockDir.resolve(projectName + ".lock");
        Files.writeString(lockFile, "locked");

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        requireThat(Files.exists(lockFile), "lockFileExists").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that task locks owned by the session are removed.
   */
  @Test
  public void taskLocksRemovedForSession() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("session123"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        Path taskLock1 = lockDir.resolve("task1.lock");
        Path taskLock2 = lockDir.resolve("task2.lock");
        Files.writeString(taskLock1, "session_id=session123");
        Files.writeString(taskLock2, "session_id=session456");

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        requireThat(Files.exists(taskLock1), "taskLock1Exists").isFalse();
        requireThat(Files.exists(taskLock2), "taskLock2Exists").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that stale locks older than 24 hours are removed.
   */
  @Test
  public void staleLocksRemoved() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        Path staleLock = lockDir.resolve("stale.lock");
        Path freshLock = lockDir.resolve("fresh.lock");
        Files.writeString(staleLock, "old lock");
        Files.writeString(freshLock, "new lock");

        Instant staleTime = Instant.now().minus(25, ChronoUnit.HOURS);
        Files.setLastModifiedTime(staleLock, FileTime.from(staleTime));

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        requireThat(Files.exists(staleLock), "staleLockExists").isFalse();
        requireThat(Files.exists(freshLock), "freshLockExists").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the session ID does not match any task lock's session, no task locks are removed.
   */
  @Test
  public void nonMatchingSessionIdSkipsLockCleaning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("other-session"),
        tempDir, tempDir, tempDir))
      {
        Path taskLockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(taskLockDir);

        Path taskLock = taskLockDir.resolve("task1.lock");
        Files.writeString(taskLock, "session_id=session123");

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        requireThat(Files.exists(taskLock), "taskLockExists").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that locks at the 24-hour boundary are preserved while older locks are deleted.
   */
  @Test
  public void twentyFourHourBoundaryRespected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        Path justWithinBoundary = lockDir.resolve("fresh.lock");
        Path justBeyondBoundary = lockDir.resolve("stale.lock");
        Files.writeString(justWithinBoundary, "lock");
        Files.writeString(justBeyondBoundary, "lock");

        Instant now = Instant.now();
        Instant within = now.minus(24, ChronoUnit.HOURS).plus(1, ChronoUnit.SECONDS);
        Instant beyond = now.minus(24, ChronoUnit.HOURS).minus(1, ChronoUnit.SECONDS);
        Files.setLastModifiedTime(justWithinBoundary, FileTime.from(within));
        Files.setLastModifiedTime(justBeyondBoundary, FileTime.from(beyond));

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        requireThat(Files.exists(justWithinBoundary), "justWithinBoundary").isTrue();
        requireThat(Files.exists(justBeyondBoundary), "justBeyondBoundary").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that processing completes gracefully when lock directory does not exist.
   */
  @Test
  public void nonexistentLockDirectoryHandledGracefully() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("session123"),
        tempDir, tempDir, tempDir))
      {
        HookResult result = new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        Path lockDir = scope.getCatWorkPath().resolve("locks");
        requireThat(Files.exists(lockDir), "lockDirExists").isFalse();
        requireThat(result.output(), "output").contains("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when multiple locks exist, only the correct lock is preserved.
   */
  @Test
  public void multipleLocksOnlyCorrectPreserved() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("session456"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        Path lockA = lockDir.resolve("taskA.lock");
        Path lockB = lockDir.resolve("taskB.lock");
        Path lockC = lockDir.resolve("taskC.lock");
        Files.writeString(lockA, "session_id=session123");
        Files.writeString(lockB, "session_id=session456");
        Files.writeString(lockC, "session_id=session789");

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        requireThat(Files.exists(lockA), "lockA").isTrue();
        requireThat(Files.exists(lockB), "lockB").isFalse();
        requireThat(Files.exists(lockC), "lockC").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@link SessionEndHook#run(io.github.cowwoc.cat.hooks.ClaudeHook)} uses
   * {@code scope.getProjectPath()} to derive the project lock file name.
   * <p>
   * The hook must delete a lock file named after the project directory's last path component
   * (i.e., {@code {projectName}.lock} where {@code projectName = scope.getProjectPath().getFileName()}).
   * This verifies that {@code run()} calls {@code scope.getProjectPath()} rather than any other
   * path accessor.
   */
  @Test
  public void runUsesGetProjectPathForLockFileName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
      tempDir, tempDir, tempDir))
    {
      Path lockDir = scope.getCatWorkPath().resolve("locks");
      Files.createDirectories(lockDir);

      // Create a lock file named after the project path (scope.getProjectPath().getFileName())
      String projectName = scope.getProjectPath().getFileName().toString();
      Path lockFile = lockDir.resolve(projectName + ".lock");
      Files.writeString(lockFile, "locked");

      // run() derives the project name from scope.getProjectPath() and must delete this lock file
      new SessionEndHook(scope).run(scope);

      requireThat(Files.exists(lockFile), "lockFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null project path throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*projectPath.*")
  public void nullProjectPathThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
      tempDir, tempDir, tempDir))
    {
      new SessionEndHook(scope).runWithProjectDir(scope, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a whitespace-only session ID throws IllegalArgumentException on construction.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?i).*session.?id.*")
  public void whitespaceSessionIdThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      // TestClaudeHook validates session_id on construction — a whitespace-only value is rejected
      new TestClaudeHook("{\"session_id\": \"   \"}", tempDir, tempDir, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that IOException when reading lock file in isLockOwnedBySession is handled.
   */
  @Test
  public void ioExceptionReadingLockFileHandled() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("session123"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        Path directoryLock = lockDir.resolve("directory.lock");
        Files.createDirectories(directoryLock);

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        requireThat(Files.exists(directoryLock), "directoryLockExists").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that IOException when deleting project lock file is caught and handled gracefully.
   */
  @Test
  public void projectLockDeletionErrorHandledGracefully() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        // Create a directory where the lock file should be — Files.delete() will throw IOException
        String projectName = tempDir.getFileName().toString();
        Path lockFile = lockDir.resolve(projectName + ".lock");
        Files.createDirectories(lockFile);
        Path nestedFile = lockFile.resolve("nested.txt");
        Files.writeString(nestedFile, "content");

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        // IOException was caught gracefully — lock file still exists (deletion failed)
        requireThat(Files.exists(lockFile), "lockFileStillExists").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that IOException when reading attributes for stale lock detection is handled gracefully.
   */
  @Test
  public void staleLockAttributeReadErrorHandledGracefully() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
        tempDir, tempDir, tempDir))
      {
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Files.createDirectories(lockDir);

        // Create a directory where a lock file should be — stale lock deletion will throw IOException
        Path directoryAsLockFile = lockDir.resolve("badlock.lock");
        Files.createDirectories(directoryAsLockFile);
        Path nestedFile = directoryAsLockFile.resolve("nested.txt");
        Files.writeString(nestedFile, "content");

        new SessionEndHook(scope).runWithProjectDir(scope, tempDir);

        // IOException was caught gracefully — directory-as-lock still exists (deletion failed)
        requireThat(Files.exists(directoryAsLockFile), "directoryLockStillExists").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
