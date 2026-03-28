/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.cat.hooks.util.RulesDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Injects audience-filtered rules from plugin-bundled and project-local rule directories into
 * subagent context.
 * <p>
 * Discovers all rule files from both {@code ${CLAUDE_PLUGIN_ROOT}/rules/} (plugin-bundled) and
 * {@code ${projectPath}/.cat/rules/} (project-local), concatenates them (plugin-bundled
 * first, project-local second), then filters using the {@code subAgents} frontmatter property.
 * Omitting {@code subAgents} (or providing no frontmatter) reaches all subagents;
 * {@code subAgents: []} excludes all subagents; specific types like
 * {@code subAgents: ["cat:work-execute"]} target only matching subagents.
 */
public final class InjectSubAgentRules implements SubagentStartHandler
{
  private final Logger log = LoggerFactory.getLogger(InjectSubAgentRules.class);

  /**
   * Creates a new InjectSubAgentRules handler.
   */
  public InjectSubAgentRules()
  {
  }

  /**
   * Discovers and injects CAT rules applicable to this subagent.
   * <p>
   * Reads from two sources in order (plugin-bundled first, project-local second):
   * <ol>
   *   <li>{@code ${CLAUDE_PLUGIN_ROOT}/rules/} — plugin-bundled rules</li>
   *   <li>{@code ${projectPath}/.cat/rules/} — project-local rules</li>
   * </ol>
   * Both sources are concatenated; no filename-based deduplication is performed.
   *
   * @return a result containing the filtered rule content, or an empty result if no rules apply
   */
  @Override
  public Result handle(ClaudeHook scope)
  {
    String subagentType = scope.getString("subagent_type");
    if (subagentType.isBlank())
      log.debug("SubagentStart hook received blank subagent_type; rules requiring a specific " +
        "subagent type will not match");

    Path pluginRulesDir = scope.getPluginRoot().resolve("rules");
    Path projectRulesDir = scope.getCatDir().resolve("rules");
    // Rules with paths: restrictions are injected dynamically by InjectPathRules (PreToolUse hook)
    // when matching files are accessed. For subagents, only non-paths rules are injected at start.
    String rules = RulesDiscovery.getCatRulesForAudience(List.of(pluginRulesDir, projectRulesDir),
      scope.getYamlMapper(),
      (r, activeFiles) -> RulesDiscovery.filterForSubagent(r, subagentType, activeFiles),
      List.of());
    if (rules.isBlank())
      return Result.empty();
    return Result.context(rules);
  }
}
