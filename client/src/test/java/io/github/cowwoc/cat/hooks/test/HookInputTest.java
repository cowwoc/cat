/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for session_id validation in hook input parsing.
 */
public final class HookInputTest
{
  /**
   * Verifies that a session ID containing path traversal characters throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void invalidSessionIdThrowsException()
  {
    new TestClaudeHook("{\"session_id\": \"../etc/passwd\", \"tool_name\": \"Bash\"}");
  }

  /**
   * Verifies that a valid UUID-style session ID is accepted.
   */
  @Test
  public void validSessionIdIsReturned()
  {
    String sessionId = "550e8400-e29b-41d4-a716-446655440000";
    try (TestClaudeHook scope = new TestClaudeHook("{\"session_id\": \"" + sessionId + "\"}"))
    {
      requireThat(scope.getSessionId(), "sessionId").isEqualTo(sessionId);
    }
  }

  /**
   * Verifies that a session ID with slash characters throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void sessionIdWithSlashThrowsException()
  {
    new TestClaudeHook("{\"session_id\": \"valid/but/slashes\", \"tool_name\": \"Bash\"}");
  }

  /**
   * Verifies that a missing session ID throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id.*")
  public void missingSessionIdThrows()
  {
    new TestClaudeHook("{\"tool_name\": \"Bash\"}");
  }

  /**
   * Verifies that a session ID with underscores and hyphens is accepted.
   */
  @Test
  public void sessionIdWithUnderscoresAndHyphensIsAccepted()
  {
    String sessionId = "test-session_abc123";
    try (TestClaudeHook scope = new TestClaudeHook("{\"session_id\": \"" + sessionId + "\"}"))
    {
      requireThat(scope.getSessionId(), "sessionId").isEqualTo(sessionId);
    }
  }

  /**
   * Verifies that an empty session ID throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void emptySessionIdThrows()
  {
    new TestClaudeHook("{\"session_id\": \"\"}");
  }

  /**
   * Verifies that a whitespace-only session ID throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void whitespaceSessionIdThrows()
  {
    new TestClaudeHook("{\"session_id\": \"   \"}");
  }

  /**
   * Verifies that a session ID containing a dollar sign throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void sessionIdWithDollarSignThrowsException()
  {
    new TestClaudeHook("{\"session_id\": \"test$id\"}");
  }

  /**
   * Verifies that a session ID containing a space throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid session_id format.*")
  public void sessionIdWithSpaceThrowsException()
  {
    new TestClaudeHook("{\"session_id\": \"test id\"}");
  }
}
