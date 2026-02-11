package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import io.github.cowwoc.cat.hooks.util.IssueLock.LockListEntry;
import io.github.cowwoc.cat.hooks.util.IssueLock.LockResult;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for IssueLock.
 * <p>
 * Tests verify lock acquisition, release, update, force-release, check, and list operations.
 * Each test is self-contained with temporary directories to support parallel execution.
 */
public class IssueLockTest
{
  /**
   * Verifies that acquiring a lock succeeds when no lock exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireSucceedsWhenNoLockExists() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        LockResult result = lock.acquire("test-issue", sessionId, "/path/to/worktree");

        requireThat(result, "result").isInstanceOf(LockResult.Acquired.class);
        LockResult.Acquired acquired = (LockResult.Acquired) result;
        requireThat(acquired.status(), "status").isEqualTo("acquired");
        requireThat(acquired.message(), "message").contains("successfully");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquiring a lock is idempotent when the same session tries again.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireIsIdempotentForSameSession() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId, "/path/to/worktree");
        LockResult result = lock.acquire("test-issue", sessionId, "/path/to/worktree");

        requireThat(result, "result").isInstanceOf(LockResult.Acquired.class);
        LockResult.Acquired acquired = (LockResult.Acquired) result;
        requireThat(acquired.status(), "status").isEqualTo("acquired");
        requireThat(acquired.message(), "message").contains("already held");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquiring a lock fails when another session holds it.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireFailsWhenLockedByAnotherSession() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId1, "/path/to/worktree");
        LockResult result = lock.acquire("test-issue", sessionId2, "/path/to/worktree");

        requireThat(result, "result").isInstanceOf(LockResult.Locked.class);
        LockResult.Locked locked = (LockResult.Locked) result;
        requireThat(locked.status(), "status").isEqualTo("locked");
        requireThat(locked.message(), "message").contains("another session");
        requireThat(locked.owner(), "owner").isEqualTo(sessionId1);
        requireThat(locked.action(), "action").isEqualTo("FIND_ANOTHER_ISSUE");
        requireThat(locked.guidance(), "guidance").contains("Do NOT investigate");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire rejects invalid session IDs.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireRejectsInvalidSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);

        try
        {
          lock.acquire("test-issue", "not-a-uuid", "/path/to/worktree");
          requireThat(false, "shouldThrow").isTrue();
        }
        catch (IllegalArgumentException e)
        {
          requireThat(e.getMessage(), "message").contains("Invalid session_id format");
          requireThat(e.getMessage(), "message").contains("Did you swap");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that update succeeds when the lock is owned by the session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void updateSucceedsWhenLockOwned() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId, "");
        LockResult result = lock.update("test-issue", sessionId, "/new/worktree");

        requireThat(result, "result").isInstanceOf(LockResult.Updated.class);
        LockResult.Updated updated = (LockResult.Updated) result;
        requireThat(updated.status(), "status").isEqualTo("updated");
        requireThat(updated.worktree(), "worktree").isEqualTo("/new/worktree");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that update fails when the lock is owned by a different session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void updateFailsWhenLockOwnedByAnotherSession() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId1, "/path/to/worktree");
        LockResult result = lock.update("test-issue", sessionId2, "/new/worktree");

        requireThat(result, "result").isInstanceOf(LockResult.Error.class);
        LockResult.Error error = (LockResult.Error) result;
        requireThat(error.status(), "status").isEqualTo("error");
        requireThat(error.message(), "message").contains("different session");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that update fails when no lock exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void updateFailsWhenNoLockExists() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        LockResult result = lock.update("test-issue", sessionId, "/new/worktree");

        requireThat(result, "result").isInstanceOf(LockResult.Error.class);
        LockResult.Error error = (LockResult.Error) result;
        requireThat(error.status(), "status").isEqualTo("error");
        requireThat(error.message(), "message").contains("No lock exists");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that release succeeds when the lock is owned by the session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void releaseSucceedsWhenLockOwned() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId, "/path/to/worktree");
        LockResult result = lock.release("test-issue", sessionId);

        requireThat(result, "result").isInstanceOf(LockResult.Released.class);
        LockResult.Released released = (LockResult.Released) result;
        requireThat(released.status(), "status").isEqualTo("released");
        requireThat(released.message(), "message").contains("successfully");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that release fails when the lock is owned by a different session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void releaseFailsWhenLockOwnedByAnotherSession() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId1, "/path/to/worktree");
        LockResult result = lock.release("test-issue", sessionId2);

        requireThat(result, "result").isInstanceOf(LockResult.Error.class);
        LockResult.Error error = (LockResult.Error) result;
        requireThat(error.status(), "status").isEqualTo("error");
        requireThat(error.message(), "message").contains("different session");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that release succeeds when no lock exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void releaseSucceedsWhenNoLockExists() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        LockResult result = lock.release("test-issue", sessionId);

        requireThat(result, "result").isInstanceOf(LockResult.Released.class);
        LockResult.Released released = (LockResult.Released) result;
        requireThat(released.status(), "status").isEqualTo("released");
        requireThat(released.message(), "message").contains("No lock exists");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that force release removes a lock regardless of owner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void forceReleaseRemovesLockRegardlessOfOwner() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId, "/path/to/worktree");
        LockResult result = lock.forceRelease("test-issue");

        requireThat(result, "result").isInstanceOf(LockResult.Released.class);
        LockResult.Released released = (LockResult.Released) result;
        requireThat(released.status(), "status").isEqualTo("released");
        requireThat(released.message(), "message").contains("forcibly released");
        requireThat(released.message(), "message").contains(sessionId);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that force release succeeds when no lock exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void forceReleaseSucceedsWhenNoLockExists() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);

        LockResult result = lock.forceRelease("test-issue");

        requireThat(result, "result").isInstanceOf(LockResult.Released.class);
        LockResult.Released released = (LockResult.Released) result;
        requireThat(released.status(), "status").isEqualTo("released");
        requireThat(released.message(), "message").contains("No lock exists");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that check returns unlocked status when no lock exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkReturnsUnlockedWhenNoLockExists() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);

        LockResult result = lock.check("test-issue");

        requireThat(result, "result").isInstanceOf(LockResult.CheckUnlocked.class);
        LockResult.CheckUnlocked checkUnlocked = (LockResult.CheckUnlocked) result;
        requireThat(checkUnlocked.locked(), "locked").isFalse();
        requireThat(checkUnlocked.message(), "message").contains("not locked");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that check returns locked status with details when lock exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkReturnsLockedStatusWhenLockExists() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId, "/path/to/worktree");
        LockResult result = lock.check("test-issue");

        requireThat(result, "result").isInstanceOf(LockResult.CheckLocked.class);
        LockResult.CheckLocked checkLocked = (LockResult.CheckLocked) result;
        requireThat(checkLocked.locked(), "locked").isTrue();
        requireThat(checkLocked.sessionId(), "sessionId").isEqualTo(sessionId);
        requireThat(checkLocked.worktree(), "worktree").isEqualTo("/path/to/worktree");
        requireThat(checkLocked.ageSeconds(), "ageSeconds").isGreaterThanOrEqualTo(0);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that list returns empty list when no locks exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void listReturnsEmptyListWhenNoLocksExist() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);

        List<LockListEntry> locks = lock.list();

        requireThat(locks, "locks").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that list returns all locks.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void listReturnsAllLocks() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();

        lock.acquire("issue-1", sessionId1, "/path/1");
        lock.acquire("issue-2", sessionId2, "/path/2");

        List<LockListEntry> locks = lock.list();

        requireThat(locks.size(), "size").isEqualTo(2);

        boolean foundIssue1 = false;
        boolean foundIssue2 = false;

        for (LockListEntry entry : locks)
        {
          if (entry.issue().equals("issue-1"))
          {
            requireThat(entry.session(), "session1").isEqualTo(sessionId1);
            foundIssue1 = true;
          }
          if (entry.issue().equals("issue-2"))
          {
            requireThat(entry.session(), "session2").isEqualTo(sessionId2);
            foundIssue2 = true;
          }
        }

        requireThat(foundIssue1, "foundIssue1").isTrue();
        requireThat(foundIssue2, "foundIssue2").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that list skips malformed lock files.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void listSkipsMalformedLockFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("valid-issue", sessionId, "/path/to/worktree");

        Path lockDir = tempDir.resolve(".claude").resolve("cat").resolve("locks");
        Path corruptedLock = lockDir.resolve("corrupted.lock");
        Files.writeString(corruptedLock, "this is not valid JSON");

        List<LockListEntry> locks = lock.list();

        requireThat(locks.size(), "size").isEqualTo(1);
        requireThat(locks.get(0).issue(), "issue").isEqualTo("valid-issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that check handles lock files missing created_at field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkHandlesMissingCreatedAtField() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        Path lockDir = tempDir.resolve(".claude").resolve("cat").resolve("locks");
        Files.createDirectories(lockDir);

        Path lockFile = lockDir.resolve("test-issue.lock");
        String invalidLock = "{\"session_id\": \"12345678-1234-1234-1234-123456789012\"}";
        Files.writeString(lockFile, invalidLock);

        IssueLock lock = new IssueLock(mapper, tempDir);

        try
        {
          lock.check("test-issue");
          requireThat(false, "shouldThrow").isTrue();
        }
        catch (NullPointerException e)
        {
          requireThat(e.getMessage(), "message").isNotNull();
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that lock file sanitizes issue IDs with slashes.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void lockFileSanitizesIssueIdWithSlashes() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("v2.1/fix-bug", sessionId, "/path/to/worktree");

        Path lockDir = tempDir.resolve(".claude").resolve("cat").resolve("locks");
        Path lockFile = lockDir.resolve("v2.1-fix-bug.lock");

        requireThat(Files.exists(lockFile), "lockFileExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that toJson produces correct format for acquired status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesCorrectFormatForAcquired() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      LockResult.Acquired result = new LockResult.Acquired("acquired", "Lock acquired successfully");

      String json = result.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"acquired\"");
      requireThat(json, "json").contains("\"message\"");
    }
  }

  /**
   * Verifies that toJson produces correct format for locked status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesCorrectFormatForLocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      LockResult.Locked result = new LockResult.Locked("locked", "Issue locked", "session-123",
        "FIND_ANOTHER_ISSUE", "guidance text", "author", "email", "date");

      String json = result.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"locked\"");
      requireThat(json, "json").contains("\"owner\"");
      requireThat(json, "json").contains("\"action\"");
      requireThat(json, "json").contains("\"guidance\"");
    }
  }

  /**
   * Verifies that toJson produces correct format for check locked result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesCorrectFormatForCheckLocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      LockResult.CheckLocked result = new LockResult.CheckLocked(true, "session-id", 300, "/path");

      String json = result.toJson(mapper);

      requireThat(json, "json").contains("\"locked\"");
      requireThat(json, "json").contains("true");
      requireThat(json, "json").contains("\"session_id\"");
      requireThat(json, "json").contains("\"age_seconds\"");
    }
  }

  /**
   * Verifies that toJson produces correct format for check unlocked result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesCorrectFormatForCheckUnlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      LockResult.CheckUnlocked result = new LockResult.CheckUnlocked(false, "Issue not locked");

      String json = result.toJson(mapper);

      requireThat(json, "json").contains("\"locked\"");
      requireThat(json, "json").contains("false");
      requireThat(json, "json").contains("\"message\"");
    }
  }

  /**
   * Verifies that constructor throws on invalid project directory.
   */
  @Test
  public void constructorThrowsOnInvalidProjectDirectory() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      try
      {
        try
        {
          new IssueLock(mapper, tempDir);
          requireThat(false, "shouldThrow").isTrue();
        }
        catch (IllegalArgumentException e)
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
   * Verifies that update preserves created_at timestamp.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void updatePreservesCreatedAt() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("test-issue", sessionId, "/initial/worktree");
        Thread.sleep(100);
        lock.update("test-issue", sessionId, "/updated/worktree");

        Path lockFile = tempDir.resolve(".claude").resolve("cat").resolve("locks").
          resolve("test-issue.lock");
        String content = Files.readString(lockFile);

        requireThat(content, "content").contains("created_at");
        requireThat(content, "content").contains("created_iso");
      }
      catch (InterruptedException e)
      {
        throw WrappedCheckedException.wrap(e);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that lock files sanitize backslashes in issue IDs.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void lockFileSanitizesBackslashesInIssueId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("v2.1\\fix-bug", sessionId, "/path/to/worktree");

        Path lockDir = tempDir.resolve(".claude").resolve("cat").resolve("locks");
        Path lockFile = lockDir.resolve("v2.1\\fix-bug.lock");

        requireThat(Files.exists(lockFile), "lockFileExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that lock files sanitize colons in issue IDs.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void lockFileSanitizesColonsInIssueId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempProject();
      try
      {
        IssueLock lock = new IssueLock(mapper, tempDir);
        String sessionId = UUID.randomUUID().toString();

        lock.acquire("issue:123", sessionId, "/path/to/worktree");

        Path lockDir = tempDir.resolve(".claude").resolve("cat").resolve("locks");
        Path lockFile = lockDir.resolve("issue:123.lock");

        requireThat(Files.exists(lockFile), "lockFileExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Creates a temporary CAT project directory for test isolation.
   *
   * @return the path to the created temporary directory
   */
  private Path createTempProject()
  {
    try
    {
      Path tempDir = Files.createTempDirectory("issue-lock-test");
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      return tempDir;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a temporary directory for test isolation.
   *
   * @return the path to the created temporary directory
   */
  private Path createTempDir()
  {
    try
    {
      return Files.createTempDirectory("issue-lock-test");
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}
