/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.SkillLoader;

/**
 * Builds the CAT agent ID context message for injection into agent context.
 * <p>
 * Agents must pass the CAT agent ID as the first argument ({@code $0}) when invoking any skill via the
 * Skill tool, so SkillLoader can maintain per-agent marker files.
 * <p>
 * Called by {@code SessionStartHook} for the main agent and by {@code SubagentStartHook} for
 * subagents.
 */
public final class InjectCatAgentId
{
  /**
   * Not instantiable.
   */
  private InjectCatAgentId()
  {
  }

  /**
   * Returns a context message telling the main agent its CAT agent ID and how to use it.
   * <p>
   * The main agent's CAT agent ID is the session ID itself.
   *
   * @param sessionId the session ID
   * @return the CAT agent ID context string
   * @throws NullPointerException     if {@code sessionId} is null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  public static String getMainAgentContext(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return "Your CAT agent ID is: `" + sessionId + "`. You MUST pass this as the first " +
      "argument when invoking any skill via the Skill tool.";
  }

  /**
   * Returns a context message telling a subagent its CAT agent ID and how to use it.
   * <p>
   * The subagent's CAT agent ID is composed of the session ID and the native {@code agent_id}:
   * {@code {sessionId}/subagents/{agent_id}}.
   *
   * @param sessionId the session ID
   * @param agentId   the subagent's native agent ID (not the composite ID)
   * @return the CAT agent ID context string
   * @throws NullPointerException     if {@code sessionId} or {@code agentId} are null
   * @throws IllegalArgumentException if {@code sessionId} or {@code agentId} are blank
   */
  public static String getSubagentContext(String sessionId, String agentId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(agentId, "agentId").isNotBlank();
    String catAgentId = sessionId + "/" + SkillLoader.SUBAGENTS_DIR + "/" + agentId;
    return "Your CAT agent ID is: `" + catAgentId + "`. You MUST pass this as the first argument " +
      "when invoking any skill via the Skill tool.";
  }
}
