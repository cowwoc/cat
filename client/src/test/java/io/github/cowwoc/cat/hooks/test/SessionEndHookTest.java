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
import java.util.UUID;

/**
 * Tests for SessionEndHook.
 * <p>
 * SessionEndHook delegates session work directory cleanup to {@code SessionEndHandler}.
 * Lock files are NOT deleted by SessionEndHook — lock management is exclusively the responsibility
 * of the {@code cat:work} cleanup phase which releases locks when work is explicitly completed.
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
   * Verifies that non-UUID-named directories under the sessions directory are not deleted.
   * <p>
   * {@code SessionEndHandler.clean()} uses a UUID pattern guard to reject directory names that
   * do not match the standard session ID format. This prevents path-traversal attacks where a
   * malicious directory name like {@code ../../../etc} could cause unintended deletion.
   */
  @Test
  public void nonUuidSessionDirectorySkippedDuringCleanup() throws IOException
  {
    String currentSessionId = UUID.randomUUID().toString();
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload(currentSessionId),
        tempDir, tempDir, tempDir))
      {
        Path sessionsDir = scope.getCatWorkPath().resolve("sessions");

        // Create directories with non-UUID names that should survive cleanup
        Path dotDir = sessionsDir.resolve(".hidden");
        Path traversalDir = sessionsDir.resolve("..%2F..%2Fetc");
        Path plainNameDir = sessionsDir.resolve("not-a-uuid");
        Files.createDirectories(dotDir);
        Files.createDirectories(traversalDir);
        Files.createDirectories(plainNameDir);

        HookResult result = new SessionEndHook(scope).run(scope);

        requireThat(Files.exists(dotDir), "dotDirExists").isTrue();
        requireThat(Files.exists(traversalDir), "traversalDirExists").isTrue();
        requireThat(Files.exists(plainNameDir), "plainNameDirExists").isTrue();
        requireThat(result.output(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a non-current session work directory whose corresponding Claude session
   * directory still exists is preserved during cleanup.
   * <p>
   * {@code SessionEndHandler.clean()} only deletes a session work directory when the
   * corresponding Claude session directory does NOT exist. This test ensures the inverse:
   * when the Claude session directory still exists, the work directory is preserved.
   */
  @Test
  public void activeNonCurrentSessionWorkDirectoryPreserved() throws IOException
  {
    String currentSessionId = UUID.randomUUID().toString();
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload(currentSessionId),
        tempDir, tempDir, tempDir))
      {
        Path sessionsDir = scope.getCatWorkPath().resolve("sessions");

        // Create a non-current session work directory
        String otherSessionId = UUID.randomUUID().toString();
        Path otherSessionWorkDir = sessionsDir.resolve(otherSessionId);
        Files.createDirectories(otherSessionWorkDir);
        Files.writeString(otherSessionWorkDir.resolve("session.cwd"), "/workspace");

        // Create the corresponding Claude session directory so it looks active
        Path claudeSessionDir = scope.getClaudeSessionPath(otherSessionId);
        Files.createDirectories(claudeSessionDir);

        HookResult result = new SessionEndHook(scope).run(scope);

        // Work directory preserved because its Claude session directory still exists
        requireThat(Files.exists(otherSessionWorkDir), "otherSessionWorkDirExists").isTrue();
        requireThat(result.output(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code SessionEndHook.run()} invokes session work directory cleanup in addition
   * to stale lock removal. A stale session work directory (with no corresponding Claude session
   * directory) should be deleted.
   */
  @Test
  public void sessionEndHookCleansStaleSessionWorkDirectories() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload("test-session"),
        tempDir, tempDir, tempDir))
      {
        // Create a stale session work directory with no matching Claude session directory
        String staleSessionId = UUID.randomUUID().toString();
        Path sessionsDir = scope.getCatWorkPath().resolve("sessions");
        Path staleSessionWorkDir = sessionsDir.resolve(staleSessionId);
        Files.createDirectories(staleSessionWorkDir);
        Files.writeString(staleSessionWorkDir.resolve("session.cwd"), "/workspace");

        HookResult result = new SessionEndHook(scope).run(scope);

        requireThat(Files.exists(staleSessionWorkDir), "staleSessionWorkDirExists").isFalse();
        requireThat(result.output(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the current session's work directory is preserved during cleanup, even when
   * other stale session work directories are deleted.
   * <p>
   * The {@code SessionEndHandler.clean()} method skips the directory whose name matches the
   * current session ID. This test ensures that the current session's work files are not
   * deleted mid-session.
   */
  @Test
  public void currentSessionWorkDirectoryPreservedDuringCleanup() throws IOException
  {
    String currentSessionId = UUID.randomUUID().toString();
    Path tempDir = Files.createTempDirectory("session-end-hook-test");
    try
    {
      try (TestClaudeHook scope = new TestClaudeHook(sessionPayload(currentSessionId),
        tempDir, tempDir, tempDir))
      {
        Path sessionsDir = scope.getCatWorkPath().resolve("sessions");

        // Create the current session's work directory — should be preserved
        Path currentSessionWorkDir = sessionsDir.resolve(currentSessionId);
        Files.createDirectories(currentSessionWorkDir);
        Files.writeString(currentSessionWorkDir.resolve("session.cwd"), "/workspace");

        // Create a stale session work directory — should be deleted
        String staleSessionId = UUID.randomUUID().toString();
        Path staleSessionWorkDir = sessionsDir.resolve(staleSessionId);
        Files.createDirectories(staleSessionWorkDir);
        Files.writeString(staleSessionWorkDir.resolve("session.cwd"), "/workspace");

        HookResult result = new SessionEndHook(scope).run(scope);

        requireThat(Files.exists(currentSessionWorkDir), "currentSessionWorkDirExists").isTrue();
        requireThat(Files.exists(staleSessionWorkDir), "staleSessionWorkDirExists").isFalse();
        requireThat(result.output(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
