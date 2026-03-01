/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.InjectCatAgentId;
import org.testng.annotations.Test;

/**
 * Tests for InjectCatAgentId.
 */
public class InjectCatAgentIdTest
{
  /**
   * Verifies that getMainAgentContext() returns a context containing the session ID and the instruction
   * to pass it as the first argument when invoking skills.
   */
  @Test
  public void getMainAgentContextReturnsContextWithSessionId()
  {
    String result = InjectCatAgentId.getMainAgentContext("test-session-id-12345");

    requireThat(result, "result").contains("test-session-id-12345");
    requireThat(result, "result").contains("You MUST pass this as the first");
  }

  /**
   * Verifies that getMainAgentContext() throws when the session ID is blank.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void getMainAgentContextRejectsBlankSessionId()
  {
    InjectCatAgentId.getMainAgentContext("");
  }

  /**
   * Verifies that getMainAgentContext() throws when the session ID is null.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void getMainAgentContextRejectsNullSessionId()
  {
    InjectCatAgentId.getMainAgentContext(null);
  }

  /**
   * Verifies that getSubagentContext() returns a composite agent ID containing the session ID and
   * agent ID.
   */
  @Test
  public void getSubagentContextReturnsCompositeAgentId()
  {
    String result = InjectCatAgentId.getSubagentContext("session-123", "agent-456");

    requireThat(result, "result").contains("session-123/subagents/agent-456");
    requireThat(result, "result").contains("You MUST pass this as the first argument");
  }

  /**
   * Verifies that getSubagentContext() throws when the session ID is blank.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void getSubagentContextRejectsBlankSessionId()
  {
    InjectCatAgentId.getSubagentContext("", "agent-456");
  }

  /**
   * Verifies that getSubagentContext() throws when the agent ID is blank.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*agentId.*")
  public void getSubagentContextRejectsBlankAgentId()
  {
    InjectCatAgentId.getSubagentContext("session-123", "");
  }
}
