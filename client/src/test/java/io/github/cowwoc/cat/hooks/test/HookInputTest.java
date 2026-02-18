/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for HookInput.
 */
public final class HookInputTest
{
  /**
   * Verifies that a session ID containing path traversal characters throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void invalidSessionIdThrowsException()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = """
      {"session_id": "../etc/passwd", "tool_name": "Bash"}
      """;
    ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput.readFrom(mapper, inputStream);
  }

  /**
   * Verifies that a valid UUID-style session ID is accepted.
   */
  @Test
  public void validSessionIdIsReturned()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String sessionId = "550e8400-e29b-41d4-a716-446655440000";
    String json = "{\"session_id\": \"" + sessionId + "\", \"tool_name\": \"Bash\"}";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput input = HookInput.readFrom(mapper, inputStream);

    requireThat(input.getSessionId(), "sessionId").isEqualTo(sessionId);
  }

  /**
   * Verifies that a session ID with slash characters throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void sessionIdWithSlashThrowsException()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = """
      {"session_id": "valid/but/slashes", "tool_name": "Bash"}
      """;
    ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput.readFrom(mapper, inputStream);
  }

  /**
   * Verifies that a missing session ID returns empty string.
   */
  @Test
  public void missingSessionIdReturnsEmpty()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = """
      {"tool_name": "Bash"}
      """;
    ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput input = HookInput.readFrom(mapper, inputStream);

    requireThat(input.getSessionId(), "sessionId").isEmpty();
  }

  /**
   * Verifies that a session ID with underscores and hyphens is accepted.
   */
  @Test
  public void sessionIdWithUnderscoresAndHyphensIsAccepted()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String sessionId = "test-session_abc123";
    String json = "{\"session_id\": \"" + sessionId + "\", \"tool_name\": \"Bash\"}";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput input = HookInput.readFrom(mapper, inputStream);

    requireThat(input.getSessionId(), "sessionId").isEqualTo(sessionId);
  }

  /**
   * Verifies that an empty session ID returns empty string.
   */
  @Test
  public void emptySessionIdReturnsEmpty() throws IOException
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = "{\"session_id\": \"\"}";
    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput input = HookInput.readFrom(mapper, stream);
    requireThat(input.getSessionId(), "sessionId").isEmpty();
  }

  /**
   * Verifies that a whitespace-only session ID returns empty string.
   */
  @Test
  public void whitespaceSessionIdReturnsEmpty() throws IOException
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = "{\"session_id\": \"   \"}";
    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput input = HookInput.readFrom(mapper, stream);
    requireThat(input.getSessionId(), "sessionId").isEmpty();
  }

  /**
   * Verifies that a session ID containing a dollar sign throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void sessionIdWithDollarSignThrowsException()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = "{\"session_id\": \"test$id\"}";
    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput.readFrom(mapper, stream);
  }

  /**
   * Verifies that a session ID containing a space throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void sessionIdWithSpaceThrowsException()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = "{\"session_id\": \"test id\"}";
    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    HookInput.readFrom(mapper, stream);
  }
}
