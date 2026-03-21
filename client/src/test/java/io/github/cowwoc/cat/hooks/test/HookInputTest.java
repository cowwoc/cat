/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for HookInput.
 */
public final class HookInputTest
{
  /**
   * Verifies that a session ID containing path traversal characters throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void invalidSessionIdThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = """
        {"session_id": "../etc/passwd", "tool_name": "Bash"}
        """;
      ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput.readFrom(scope.getJsonMapper(), inputStream);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a valid UUID-style session ID is accepted.
   */
  @Test
  public void validSessionIdIsReturned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String sessionId = "550e8400-e29b-41d4-a716-446655440000";
      String json = "{\"session_id\": \"" + sessionId + "\", \"tool_name\": \"Bash\"}";
      ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(scope.getJsonMapper(), inputStream);

      requireThat(input.getSessionId(), "sessionId").isEqualTo(sessionId);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a session ID with slash characters throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void sessionIdWithSlashThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = """
        {"session_id": "valid/but/slashes", "tool_name": "Bash"}
        """;
      ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput.readFrom(scope.getJsonMapper(), inputStream);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a missing session ID throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id.*")
  public void missingSessionIdThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = """
        {"tool_name": "Bash"}
        """;
      ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput.readFrom(scope.getJsonMapper(), inputStream);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a session ID with underscores and hyphens is accepted.
   */
  @Test
  public void sessionIdWithUnderscoresAndHyphensIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String sessionId = "test-session_abc123";
      String json = "{\"session_id\": \"" + sessionId + "\", \"tool_name\": \"Bash\"}";
      ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(scope.getJsonMapper(), inputStream);

      requireThat(input.getSessionId(), "sessionId").isEqualTo(sessionId);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an empty session ID throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void emptySessionIdThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = "{\"session_id\": \"\"}";
      InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput.readFrom(scope.getJsonMapper(), stream);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a whitespace-only session ID throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void whitespaceSessionIdThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = "{\"session_id\": \"   \"}";
      InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput.readFrom(scope.getJsonMapper(), stream);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a session ID containing a dollar sign throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void sessionIdWithDollarSignThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = "{\"session_id\": \"test$id\"}";
      InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput.readFrom(scope.getJsonMapper(), stream);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a session ID containing a space throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void sessionIdWithSpaceThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = "{\"session_id\": \"test id\"}";
      InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      HookInput.readFrom(scope.getJsonMapper(), stream);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
