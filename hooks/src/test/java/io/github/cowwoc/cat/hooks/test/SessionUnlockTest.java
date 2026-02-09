package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.session.SessionUnlock;

import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Tests for SessionUnlock.
 */
public final class SessionUnlockTest
{
  /**
   * Verifies that project lock file is removed when it exists.
   */
  @Test
  public void projectLockRemoved() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Files.createDirectories(lockDir);

      String projectName = tempDir.getFileName().toString();
      Path lockFile = lockDir.resolve(projectName + ".lock");
      Files.writeString(lockFile, "locked");

      HookInput input = HookInput.empty();
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(lockFile), "lockFileExists").isFalse();
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Files.createDirectories(lockDir);

      Path taskLock1 = lockDir.resolve("task1.lock");
      Path taskLock2 = lockDir.resolve("task2.lock");
      Files.writeString(taskLock1, "session_id=session123");
      Files.writeString(taskLock2, "session_id=session456");

      String json = "{\"session_id\": \"session123\"}";
      HookInput input = HookInput.readFrom(new java.io.ByteArrayInputStream(json.getBytes()));
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(taskLock1), "taskLock1Exists").isFalse();
      requireThat(Files.exists(taskLock2), "taskLock2Exists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that legacy worktree locks owned by the session are removed.
   */
  @Test
  public void legacyWorktreeLocksRemoved() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/worktree-locks");
      Files.createDirectories(lockDir);

      Path worktreeLock1 = lockDir.resolve("worktree1.lock");
      Path worktreeLock2 = lockDir.resolve("worktree2.lock");
      Files.writeString(worktreeLock1, "session123");
      Files.writeString(worktreeLock2, "session456");

      String json = "{\"session_id\": \"session123\"}";
      HookInput input = HookInput.readFrom(new java.io.ByteArrayInputStream(json.getBytes()));
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(worktreeLock1), "worktreeLock1Exists").isFalse();
      requireThat(Files.exists(worktreeLock2), "worktreeLock2Exists").isTrue();
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Files.createDirectories(lockDir);

      Path staleLock = lockDir.resolve("stale.lock");
      Path freshLock = lockDir.resolve("fresh.lock");
      Files.writeString(staleLock, "old lock");
      Files.writeString(freshLock, "new lock");

      Instant staleTime = Instant.now().minus(25, ChronoUnit.HOURS);
      Files.setLastModifiedTime(staleLock, FileTime.from(staleTime));

      HookInput input = HookInput.empty();
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(staleLock), "staleLockExists").isFalse();
      requireThat(Files.exists(freshLock), "freshLockExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that empty session ID does not clean task or worktree locks.
   */
  @Test
  public void emptySessionIdSkipsLockCleaning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path taskLockDir = tempDir.resolve(".claude/cat/locks");
      Path worktreeLockDir = tempDir.resolve(".claude/cat/worktree-locks");
      Files.createDirectories(taskLockDir);
      Files.createDirectories(worktreeLockDir);

      Path taskLock = taskLockDir.resolve("task1.lock");
      Path worktreeLock = worktreeLockDir.resolve("worktree1.lock");
      Files.writeString(taskLock, "session_id=session123");
      Files.writeString(worktreeLock, "session123");

      HookInput input = HookInput.empty();
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(taskLock), "taskLockExists").isTrue();
      requireThat(Files.exists(worktreeLock), "worktreeLockExists").isTrue();
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
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

      HookInput input = HookInput.empty();
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(justWithinBoundary), "justWithinBoundary").isTrue();
      requireThat(Files.exists(justBeyondBoundary), "justBeyondBoundary").isFalse();
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      String json = "{\"session_id\": \"session123\"}";
      HookInput input = HookInput.readFrom(new java.io.ByteArrayInputStream(json.getBytes()));
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Path worktreeLockDir = tempDir.resolve(".claude/cat/worktree-locks");
      requireThat(Files.exists(lockDir), "lockDirExists").isFalse();
      requireThat(Files.exists(worktreeLockDir), "worktreeLockDirExists").isFalse();
      requireThat(outBytes.toString(), "output").contains("{}");
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Files.createDirectories(lockDir);

      Path lockA = lockDir.resolve("taskA.lock");
      Path lockB = lockDir.resolve("taskB.lock");
      Path lockC = lockDir.resolve("taskC.lock");
      Files.writeString(lockA, "session_id=session123");
      Files.writeString(lockB, "session_id=session456");
      Files.writeString(lockC, "session_id=session789");

      String json = "{\"session_id\": \"session456\"}";
      HookInput input = HookInput.readFrom(new java.io.ByteArrayInputStream(json.getBytes()));
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(lockA), "lockA").isTrue();
      requireThat(Files.exists(lockB), "lockB").isFalse();
      requireThat(Files.exists(lockC), "lockC").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null input throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullInputThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(null, output, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null output throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullOutputThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      HookInput input = HookInput.empty();
      new SessionUnlock().runWithProjectDir(input, null, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null project path throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullProjectPathThrowsException() throws IOException
  {
    HookInput input = HookInput.empty();
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes);
    HookOutput output = new HookOutput(out);

    new SessionUnlock().runWithProjectDir(input, output, null);
  }

  /**
   * Verifies that whitespace-only session ID skips lock cleaning.
   */
  @Test
  public void whitespaceSessionIdSkipsLockCleaning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path taskLockDir = tempDir.resolve(".claude/cat/locks");
      Path worktreeLockDir = tempDir.resolve(".claude/cat/worktree-locks");
      Files.createDirectories(taskLockDir);
      Files.createDirectories(worktreeLockDir);

      Path taskLock = taskLockDir.resolve("task1.lock");
      Path worktreeLock = worktreeLockDir.resolve("worktree1.lock");
      Files.writeString(taskLock, "session_id=session123");
      Files.writeString(worktreeLock, "session123");

      String json = "{\"session_id\": \"   \"}";
      HookInput input = HookInput.readFrom(new java.io.ByteArrayInputStream(json.getBytes()));
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(taskLock), "taskLockExists").isTrue();
      requireThat(Files.exists(worktreeLock), "worktreeLockExists").isTrue();
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Files.createDirectories(lockDir);

      Path directoryLock = lockDir.resolve("directory.lock");
      Files.createDirectories(directoryLock);

      String json = "{\"session_id\": \"session123\"}";
      HookInput input = HookInput.readFrom(new java.io.ByteArrayInputStream(json.getBytes()));
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      requireThat(Files.exists(directoryLock), "directoryLockExists").isTrue();
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Files.createDirectories(lockDir);

      // Create a directory where the lock file should be — Files.delete() will throw IOException
      String projectName = tempDir.getFileName().toString();
      Path lockFile = lockDir.resolve(projectName + ".lock");
      Files.createDirectories(lockFile);
      Path nestedFile = lockFile.resolve("nested.txt");
      Files.writeString(nestedFile, "content");

      HookInput input = HookInput.empty();
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      // IOException was caught gracefully — lock file still exists (deletion failed)
      requireThat(Files.exists(lockFile), "lockFileStillExists").isTrue();
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
    Path tempDir = Files.createTempDirectory("session-unlock-test");
    try
    {
      Path lockDir = tempDir.resolve(".claude/cat/locks");
      Files.createDirectories(lockDir);

      // Create a directory where a lock file should be — stale lock deletion will throw IOException
      Path directoryAsLockFile = lockDir.resolve("badlock.lock");
      Files.createDirectories(directoryAsLockFile);
      Path nestedFile = directoryAsLockFile.resolve("nested.txt");
      Files.writeString(nestedFile, "content");

      HookInput input = HookInput.empty();
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(outBytes);
      HookOutput output = new HookOutput(out);

      new SessionUnlock().runWithProjectDir(input, output, tempDir);

      // IOException was caught gracefully — directory-as-lock still exists (deletion failed)
      requireThat(Files.exists(directoryAsLockFile), "directoryLockStillExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
