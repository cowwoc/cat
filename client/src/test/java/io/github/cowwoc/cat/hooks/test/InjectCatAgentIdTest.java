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
  @Test
  public void getMainAgentContextRejectsBlankSessionId()
  {
    try
    {
      InjectCatAgentId.getMainAgentContext("");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("sessionId");
    }
  }

  /**
   * Verifies that getMainAgentContext() throws when the session ID is null.
   */
  @Test
  public void getMainAgentContextRejectsNullSessionId()
  {
    try
    {
      InjectCatAgentId.getMainAgentContext(null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("sessionId");
    }
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
  @Test
  public void getSubagentContextRejectsBlankSessionId()
  {
    try
    {
      InjectCatAgentId.getSubagentContext("", "agent-456");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("sessionId");
    }
  }

  /**
   * Verifies that getSubagentContext() throws when the agent ID is blank.
   */
  @Test
  public void getSubagentContextRejectsBlankAgentId()
  {
    try
    {
      InjectCatAgentId.getSubagentContext("session-123", "");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("agentId");
    }
  }
}
