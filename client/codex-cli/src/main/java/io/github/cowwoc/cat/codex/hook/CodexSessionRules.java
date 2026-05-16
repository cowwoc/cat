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
 * Selects Codex SessionStart rules for main-agent and subagent sessions.
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
    if (!sessionContext.subagent())
      return MainAgentRules.load(scope, scope.getYamlMapper());
    return RulesDiscovery.getCatRulesForAudience(scope.getRuleDirectories(), scope.getYamlMapper(),
      (rules, activeFiles) -> RulesDiscovery.filterForSubagent(rules,
        sessionContext.subagentName(), activeFiles),
      List.of());
  }

  /**
   * The Codex session audience derived from native SessionStart input.
   *
   * @param subagent true if the session belongs to a subagent
   * @param subagentName the top-level Codex subagent role used for rule matching
   */
  private record CodexSessionContext(boolean subagent, String subagentName)
  {
    /**
     * Extracts the SessionStart audience from the native Codex payload.
     *
     * @param input the native Codex hook payload
     * @return the session context
     */
    private static CodexSessionContext from(JsonNode input)
    {
      boolean subagent = "subagent".equals(text(input, "thread_source")) ||
        !input.at("/source/subagent").isMissingNode();
      if (!subagent)
        return new CodexSessionContext(false, "");
      String subagentName = text(input, "agent_role");
      if (subagentName.isEmpty())
      {
        throw new IllegalArgumentException("Codex subagent SessionStart payload is missing top-level " +
          "agent_role");
      }
      return new CodexSessionContext(true, subagentName);
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
