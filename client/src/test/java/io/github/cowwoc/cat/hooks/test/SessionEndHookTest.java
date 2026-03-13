/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.SessionEndHook;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Tests for SessionEndHook.
 * <p>
 * Lock files are stored in the external CAT storage location:
 * {@code {claudeConfigDir}/projects/{encodedProjectDir}/cat/locks/}.
 * Tests use {@link JvmScope#getProjectCatDir()} to resolve this path correctly.
 * <p>
 * Session-scoped files are managed independently by the broader session cleanup pipeline.
 */
public final class SessionEndHookTest
{
  /**
   * Creates a SessionEndHook instance for testing.
   *
   * @param scope the JVM scope
   * @return a new SessionEndHook instance
   */
  private SessionEndHook createSessionEndHook(JvmScope scope)
  {
    return new SessionEndHook(scope);
  }

  /**
   * Verifies that project lock file is removed when it exists.
   */
  @Test
  public void projectLockRemoved() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(lockDir);

        String projectName = tempDir.getFileName().toString();
        Path lockFile = lockDir.resolve(projectName + ".lock");
        Files.writeString(lockFile, "locked");

        HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        requireThat(Files.exists(lockFile), "lockFileExists").isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that task locks owned by the session are removed.
   */
  @Test
  public void taskLocksRemovedForSession() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(lockDir);

        Path taskLock1 = lockDir.resolve("task1.lock");
        Path taskLock2 = lockDir.resolve("task2.lock");
        Files.writeString(taskLock1, "session_id=session123");
        Files.writeString(taskLock2, "session_id=session456");

        String json = "{\"session_id\": \"session123\"}";
        HookInput input = HookInput.readFrom(scope.getJsonMapper(),
          new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        requireThat(Files.exists(taskLock1), "taskLock1Exists").isFalse();
        requireThat(Files.exists(taskLock2), "taskLock2Exists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that stale locks older than 24 hours are removed.
   */
  @Test
  public void staleLocksRemoved() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(lockDir);

        Path staleLock = lockDir.resolve("stale.lock");
        Path freshLock = lockDir.resolve("fresh.lock");
        Files.writeString(staleLock, "old lock");
        Files.writeString(freshLock, "new lock");

        Instant staleTime = Instant.now().minus(25, ChronoUnit.HOURS);
        Files.setLastModifiedTime(staleLock, FileTime.from(staleTime));

        HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        requireThat(Files.exists(staleLock), "staleLockExists").isFalse();
        requireThat(Files.exists(freshLock), "freshLockExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that empty session ID does not clean task locks.
   */
  @Test
  public void emptySessionIdSkipsLockCleaning() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path taskLockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(taskLockDir);

        Path taskLock = taskLockDir.resolve("task1.lock");
        Files.writeString(taskLock, "session_id=session123");

        HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        requireThat(Files.exists(taskLock), "taskLockExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that locks at the 24-hour boundary are preserved while older locks are deleted.
   */
  @Test
  public void twentyFourHourBoundaryRespected() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
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

        HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        requireThat(Files.exists(justWithinBoundary), "justWithinBoundary").isTrue();
        requireThat(Files.exists(justBeyondBoundary), "justBeyondBoundary").isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that processing completes gracefully when lock directory does not exist.
   */
  @Test
  public void nonexistentLockDirectoryHandledGracefully() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        String json = "{\"session_id\": \"session123\"}";
        HookInput input = HookInput.readFrom(scope.getJsonMapper(),
          new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        HookOutput output = new HookOutput(scope);

        io.github.cowwoc.cat.hooks.HookResult result = createSessionEndHook(scope).
          runWithProjectDir(input, output, tempDir);

        Path lockDir = scope.getProjectCatDir().resolve("locks");
        requireThat(Files.exists(lockDir), "lockDirExists").isFalse();
        requireThat(result.output(), "output").contains("{}");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that when multiple locks exist, only the correct lock is preserved.
   */
  @Test
  public void multipleLocksOnlyCorrectPreserved() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(lockDir);

        Path lockA = lockDir.resolve("taskA.lock");
        Path lockB = lockDir.resolve("taskB.lock");
        Path lockC = lockDir.resolve("taskC.lock");
        Files.writeString(lockA, "session_id=session123");
        Files.writeString(lockB, "session_id=session456");
        Files.writeString(lockC, "session_id=session789");

        String json = "{\"session_id\": \"session456\"}";
        HookInput input = HookInput.readFrom(scope.getJsonMapper(),
          new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        requireThat(Files.exists(lockA), "lockA").isTrue();
        requireThat(Files.exists(lockB), "lockB").isFalse();
        requireThat(Files.exists(lockC), "lockC").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that null input throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*input.*")
  public void nullInputThrowsException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(null, output, tempDir);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that null output throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*output.*")
  public void nullOutputThrowsException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
        createSessionEndHook(scope).runWithProjectDir(input, null, tempDir);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that null project path throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*projectPath.*")
  public void nullProjectPathThrowsException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
      HookOutput output = new HookOutput(scope);

      createSessionEndHook(scope).runWithProjectDir(input, output, null);
    }
  }

  /**
   * Verifies that whitespace-only session ID throws IllegalStateException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void whitespaceSessionIdThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        String json = "{\"session_id\": \"   \"}";
        HookInput.readFrom(scope.getJsonMapper(),
          new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that IOException when reading lock file in isLockOwnedBySession is handled.
   */
  @Test
  public void ioExceptionReadingLockFileHandled() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(lockDir);

        Path directoryLock = lockDir.resolve("directory.lock");
        Files.createDirectories(directoryLock);

        String json = "{\"session_id\": \"session123\"}";
        HookInput input = HookInput.readFrom(scope.getJsonMapper(),
          new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        requireThat(Files.exists(directoryLock), "directoryLockExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that IOException when deleting project lock file is caught and handled gracefully.
   */
  @Test
  public void projectLockDeletionErrorHandledGracefully() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(lockDir);

        // Create a directory where the lock file should be — Files.delete() will throw IOException
        String projectName = tempDir.getFileName().toString();
        Path lockFile = lockDir.resolve(projectName + ".lock");
        Files.createDirectories(lockFile);
        Path nestedFile = lockFile.resolve("nested.txt");
        Files.writeString(nestedFile, "content");

        HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        // IOException was caught gracefully — lock file still exists (deletion failed)
        requireThat(Files.exists(lockFile), "lockFileStillExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that IOException when reading attributes for stale lock detection is handled gracefully.
   */
  @Test
  public void staleLockAttributeReadErrorHandledGracefully() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("session-end-hook-test");
      try
      {
        Path lockDir = scope.getProjectCatDir().resolve("locks");
        Files.createDirectories(lockDir);

        // Create a directory where a lock file should be — stale lock deletion will throw IOException
        Path directoryAsLockFile = lockDir.resolve("badlock.lock");
        Files.createDirectories(directoryAsLockFile);
        Path nestedFile = directoryAsLockFile.resolve("nested.txt");
        Files.writeString(nestedFile, "content");

        HookInput input = TestUtils.dummyInput(scope.getJsonMapper());
        HookOutput output = new HookOutput(scope);

        createSessionEndHook(scope).runWithProjectDir(input, output, tempDir);

        // IOException was caught gracefully — directory-as-lock still exists (deletion failed)
        requireThat(Files.exists(directoryAsLockFile), "directoryLockStillExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }
}
