/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.SessionEndHandler;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for SessionEndHandler stale session work file cleanup.
 * <p>
 * Session work files are stored at {@code {claudeProjectDir}/.cat/work/sessions/{sessionId}/}.
 * The handler deletes these directories when the corresponding Claude session directory at
 * {@code {claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/} no longer exists.
 */
public final class SessionEndHandlerTest
{
  /**
   * Verifies that a stale session work directory is deleted when the corresponding Claude session
   * directory no longer exists.
   */
  @Test
  public void sessionEndDeletesStaleSessionWorkFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create a stale session work directory (no matching Claude session directory)
      String staleSessionId = "11111111-1111-1111-1111-111111111111";
      Path sessionWorkDir = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId);
      Files.createDirectories(sessionWorkDir);
      Path markerFile = sessionWorkDir.resolve("session.cwd");
      Files.writeString(markerFile, "/workspace");

      // The corresponding Claude session directory does NOT exist
      // (scope.getSessionDirectory() is for the current session, not staleSessionId)

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      new SessionEndHandler(scope).clean(input);

      requireThat(Files.exists(sessionWorkDir), "sessionWorkDirExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the current session's work directory is NOT deleted during cleanup.
   */
  @Test
  public void sessionEndSkipsCurrentSessionWorkFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String currentSessionId = scope.getClaudeSessionId();

      // Create the current session's Claude directory (marking it as active)
      Path currentClaudeSessionDir = scope.getSessionDirectory();
      Files.createDirectories(currentClaudeSessionDir);

      // Create the current session's work directory
      Path currentSessionWorkDir = scope.getProjectCatDir().resolve("sessions").resolve(currentSessionId);
      Files.createDirectories(currentSessionWorkDir);
      Files.writeString(currentSessionWorkDir.resolve("session.cwd"), "/workspace");

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      new SessionEndHandler(scope).clean(input);

      // Current session work directory must NOT be deleted
      requireThat(Files.exists(currentSessionWorkDir), "currentSessionWorkDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler completes successfully when the sessions directory does not exist.
   */
  @Test
  public void sessionEndHandlesNonExistentWorkDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Sessions directory does not exist — no error should occur
      Path sessionsDir = scope.getProjectCatDir().resolve("sessions");
      requireThat(Files.exists(sessionsDir), "sessionsDirExists").isFalse();

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      // Should complete without throwing
      new SessionEndHandler(scope).clean(input);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler completes successfully when the stale directory is absent before the
   * handler runs (e.g., it was already cleaned up or never created).
   */
  @Test
  public void sessionEndSkipsWhenStaleDirectoryMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create the sessions directory but no subdirectories.
      // The stale directory is absent before the handler runs.
      Path sessionsDir = scope.getProjectCatDir().resolve("sessions");
      Files.createDirectories(sessionsDir);

      String staleSessionId = "already-deleted-session";
      Path staleSessionWorkDir = sessionsDir.resolve(staleSessionId);

      // dummyInput() uses a fixed session ID that differs from the scope's session ID,
      // ensuring the stale directory (if it existed) would not be skipped as "current session".
      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      // Should complete without throwing even though there are no stale directories to delete
      new SessionEndHandler(scope).clean(input);

      requireThat(Files.exists(staleSessionWorkDir), "staleSessionWorkDirExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an active session's work directory (where the Claude session directory exists)
   * is preserved even when it is not the current session.
   */
  @Test
  public void sessionEndPreservesActiveSessionWorkFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Simulate another active session: create its Claude session directory
      String activeOtherSessionId = "other-active-session-id";
      String encodedProjectDir = scope.getEncodedProjectDir();
      Path activeClaudeSessionDir = scope.getClaudeConfigDir().resolve("projects").
        resolve(encodedProjectDir).resolve(activeOtherSessionId);
      Files.createDirectories(activeClaudeSessionDir);

      // Create the other active session's work directory
      Path activeSessionWorkDir = scope.getProjectCatDir().resolve("sessions").
        resolve(activeOtherSessionId);
      Files.createDirectories(activeSessionWorkDir);
      Files.writeString(activeSessionWorkDir.resolve("session.cwd"), "/workspace");

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      new SessionEndHandler(scope).clean(input);

      // Active session work directory must NOT be deleted
      requireThat(Files.exists(activeSessionWorkDir), "activeSessionWorkDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when multiple stale sessions and one active session exist, only stale sessions
   * are cleaned up and the active session is preserved.
   */
  @Test
  public void sessionEndCleansMixedStaleSessions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String encodedProjectDir = scope.getEncodedProjectDir();

      // Active session: create its Claude session directory
      String activeSessionId = "active-session-id";
      Path activeClaudeDir = scope.getClaudeConfigDir().resolve("projects").
        resolve(encodedProjectDir).resolve(activeSessionId);
      Files.createDirectories(activeClaudeDir);
      Path activeWorkDir = scope.getProjectCatDir().resolve("sessions").resolve(activeSessionId);
      Files.createDirectories(activeWorkDir);

      // Stale session 1: no Claude session directory
      String staleSessionId1 = "22222222-2222-2222-2222-222222222222";
      Path staleWorkDir1 = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId1);
      Files.createDirectories(staleWorkDir1);

      // Stale session 2: no Claude session directory
      String staleSessionId2 = "33333333-3333-3333-3333-333333333333";
      Path staleWorkDir2 = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId2);
      Files.createDirectories(staleWorkDir2);

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      new SessionEndHandler(scope).clean(input);

      requireThat(Files.exists(activeWorkDir), "activeWorkDirExists").isTrue();
      requireThat(Files.exists(staleWorkDir1), "staleWorkDir1Exists").isFalse();
      requireThat(Files.exists(staleWorkDir2), "staleWorkDir2Exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the current session's work directory with files is preserved even when the
   * Claude session directory does not yet exist (e.g., race condition at session end).
   */
  @Test
  public void sessionEndSkipsCurrentSessionEvenWithoutClaudeDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String currentSessionId = scope.getClaudeSessionId();

      // Current session work directory exists but Claude session directory does NOT
      // This simulates the race condition where Claude deletes its session dir before
      // CAT has a chance to clean up
      Path currentSessionWorkDir = scope.getProjectCatDir().resolve("sessions").
        resolve(currentSessionId);
      Files.createDirectories(currentSessionWorkDir);
      Files.writeString(currentSessionWorkDir.resolve("session.cwd"), "/workspace");

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      new SessionEndHandler(scope).clean(input);

      // Current session directory is skipped regardless of Claude dir existence
      requireThat(Files.exists(currentSessionWorkDir), "currentSessionWorkDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler completes gracefully when a stale session directory is deleted
   * concurrently (e.g., by another Claude instance) while scanning. The IOException from the walk
   * attempt is caught and logged; cleanup continues without throwing.
   */
  @Test
  public void sessionEndContinuesOnConcurrentDeletion() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create a stale session work directory
      String staleSessionId = "44444444-4444-4444-4444-444444444444";
      Path staleWorkDir = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId);
      Files.createDirectories(staleWorkDir);

      // Pre-delete the directory to simulate concurrent deletion:
      // another process deleted it between the directory scan and deletion attempt
      Files.delete(staleWorkDir);

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      // Handler must complete without throwing even if the stale directory is gone
      new SessionEndHandler(scope).clean(input);

      requireThat(Files.exists(staleWorkDir), "staleWorkDirExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies end-to-end cleanup: creates 5 session directories with only 2 having active Claude
   * sessions, invokes the handler, and verifies that only the 3 stale sessions are deleted while
   * the 2 active sessions are preserved.
   */
  @Test
  public void sessionEndCleansMultipleStaleSessionsLeavingOnlyActive() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String encodedProjectDir = scope.getEncodedProjectDir();

      // Active session 1: has Claude session directory
      String activeSessionId1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path activeClaudeDir1 = scope.getClaudeConfigDir().resolve("projects").
        resolve(encodedProjectDir).resolve(activeSessionId1);
      Files.createDirectories(activeClaudeDir1);
      Path activeWorkDir1 = scope.getProjectCatDir().resolve("sessions").resolve(activeSessionId1);
      Files.createDirectories(activeWorkDir1);

      // Active session 2: has Claude session directory
      String activeSessionId2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
      Path activeClaudeDir2 = scope.getClaudeConfigDir().resolve("projects").
        resolve(encodedProjectDir).resolve(activeSessionId2);
      Files.createDirectories(activeClaudeDir2);
      Path activeWorkDir2 = scope.getProjectCatDir().resolve("sessions").resolve(activeSessionId2);
      Files.createDirectories(activeWorkDir2);

      // Stale session 1: no Claude session directory
      String staleSessionId1 = "55555555-5555-5555-5555-555555555555";
      Path staleWorkDir1 = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId1);
      Files.createDirectories(staleWorkDir1);

      // Stale session 2: no Claude session directory
      String staleSessionId2 = "66666666-6666-6666-6666-666666666666";
      Path staleWorkDir2 = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId2);
      Files.createDirectories(staleWorkDir2);

      // Stale session 3: no Claude session directory
      String staleSessionId3 = "77777777-7777-7777-7777-777777777777";
      Path staleWorkDir3 = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId3);
      Files.createDirectories(staleWorkDir3);

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      new SessionEndHandler(scope).clean(input);

      // Active sessions must be preserved
      requireThat(Files.exists(activeWorkDir1), "activeWorkDir1Exists").isTrue();
      requireThat(Files.exists(activeWorkDir2), "activeWorkDir2Exists").isTrue();

      // Stale sessions must be deleted
      requireThat(Files.exists(staleWorkDir1), "staleWorkDir1Exists").isFalse();
      requireThat(Files.exists(staleWorkDir2), "staleWorkDir2Exists").isFalse();
      requireThat(Files.exists(staleWorkDir3), "staleWorkDir3Exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler reads the current session ID from scope and uses it to skip the current
   * session's work directory.
   */
  @Test
  public void sessionEndUsesScopeSessionIdToSkipCurrentSession() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String sessionId = scope.getClaudeSessionId();
      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      // Create a work directory for the scope's session
      Path currentWorkDir = scope.getProjectCatDir().resolve("sessions").resolve(sessionId);
      Files.createDirectories(currentWorkDir);

      new SessionEndHandler(scope).clean(input);

      // The session identified by the scope's session ID must NOT be deleted
      requireThat(Files.exists(currentWorkDir), "currentWorkDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that directory entries with non-UUID names under the sessions directory are skipped
   * without error or deletion. Only standard UUID names are processed.
   */
  @Test
  public void sessionEndRejectsNonUuidSessionIds() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create sessions directory with a non-UUID entry
      Path sessionsDir = scope.getProjectCatDir().resolve("sessions");
      Files.createDirectories(sessionsDir);

      String nonUuidName = "not-a-uuid-name";
      Path nonUuidWorkDir = sessionsDir.resolve(nonUuidName);
      Files.createDirectories(nonUuidWorkDir);
      Files.writeString(nonUuidWorkDir.resolve("session.cwd"), "/workspace");

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      // Handler must complete without throwing
      new SessionEndHandler(scope).clean(input);

      // Non-UUID directory must NOT be deleted — the handler skips it
      requireThat(Files.exists(nonUuidWorkDir), "nonUuidWorkDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler completes without throwing when deletion of a stale session directory
   * fails due to a permission error. The IOException is caught and logged; cleanup continues.
   */
  @Test
  public void sessionEndHandlesPermissionErrors() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-handler-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create a stale session work directory with a child file
      String staleSessionId = "88888888-8888-8888-8888-888888888888";
      Path staleWorkDir = scope.getProjectCatDir().resolve("sessions").resolve(staleSessionId);
      Files.createDirectories(staleWorkDir);
      Path markerFile = staleWorkDir.resolve("session.cwd");
      Files.writeString(markerFile, "/workspace");

      // Make the directory non-writable so deletion of its contents fails
      staleWorkDir.toFile().setWritable(false);

      HookInput input = TestUtils.dummyInput(scope.getJsonMapper());

      try
      {
        // Handler must complete without throwing even when deletion fails due to permissions
        new SessionEndHandler(scope).clean(input);
      }
      finally
      {
        // Restore write permission so cleanup in the finally block can succeed
        staleWorkDir.toFile().setWritable(true);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
