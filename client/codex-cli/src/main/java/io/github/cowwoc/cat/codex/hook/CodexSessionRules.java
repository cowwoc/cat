/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AgentPluginScope;
import io.github.cowwoc.cat.agent.MainAgentRules;
import io.github.cowwoc.cat.agent.RulesDiscovery;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Selects Codex SessionStart rules for main-agent and agent sessions.
 */
final class CodexSessionRules
{
  private CodexSessionRules()
  {
  }

  /**
   * Loads rule context for a Codex SessionStart event.
   *
   * @param scope the active Codex hook scope
   * @param nativeInput the native Codex hook payload
   * @return matching rule content, or an empty string if no rules match
   * @throws NullPointerException if any parameter is null
   */
  static String load(AgentPluginScope scope, JsonNode nativeInput)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(nativeInput, "nativeInput").isNotNull();
    CodexSessionContext sessionContext = CodexSessionContext.from(nativeInput);
    if (!sessionContext.agent())
      return MainAgentRules.load(scope, scope.getYamlMapper());
    return RulesDiscovery.getCatRulesForAudience(scope.getRuleDirectories(), scope.getYamlMapper(),
      (rules, activeFiles) -> RulesDiscovery.filterForAgent(rules,
        sessionContext.agentName(), activeFiles),
      List.of());
  }

  /**
   * The Codex session audience derived from native SessionStart input.
   *
   * @param agent true if the session belongs to an agent
   * @param agentName the top-level Codex agent type used for rule matching
   */
  private record CodexSessionContext(boolean agent, String agentName)
  {
    /**
     * Extracts the SessionStart audience from the native Codex payload.
     *
     * @param input the native Codex hook payload
     * @return the session context
     */
    private static CodexSessionContext from(JsonNode input)
    {
      boolean nestedAgentSession = "subagent".equals(text(input, "thread_source")) ||
        "SubagentStart".equals(text(input, "hook_event_name")) ||
        !text(input, "agent_type").isEmpty() ||
        !input.at("/source/subagent").isMissingNode();
      if (!nestedAgentSession)
        return new CodexSessionContext(false, "");
      String agentName = text(input, "agent_type");
      if (agentName.isEmpty())
      {
        throw new IllegalArgumentException("Codex agent SessionStart payload is missing top-level " +
          "agent_type");
      }
      return new CodexSessionContext(true, agentName);
    }

    /**
     * Returns a stripped string field from a JSON object.
     *
     * @param node the JSON object
     * @param fieldName the field name
     * @return the stripped field value, or an empty string if missing
     */
    private static String text(JsonNode node, String fieldName)
    {
      JsonNode value = node.get(fieldName);
      if (value == null || !value.isString())
        return "";
      return value.asString().strip();
    }
  }
}
