/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.cat.hooks.util.RulesDiscovery;

import java.nio.file.Path;
import java.util.List;

/**
 * Injects audience-filtered rules from plugin-bundled and project-local rule directories into main
 * agent context.
 * <p>
 * Discovers all rule files from both {@code ${CLAUDE_PLUGIN_ROOT}/rules/} (plugin-bundled) and
 * {@code ${projectPath}/.cat/rules/} (project-local), concatenates them (plugin-bundled
 * first, project-local second), filters to those with {@code mainAgent: true}, applies any
 * {@code paths} restrictions, and injects matching content as additional context.
 */
public final class InjectMainAgentRules implements SessionStartHandler
{
  /**
   * Creates a new InjectMainAgentRules handler.
   */
  public InjectMainAgentRules()
  {
  }

  /**
   * Discovers and injects CAT rules applicable to the main agent.
   * <p>
   * Reads from two sources in order (plugin-bundled first, project-local second):
   * <ol>
   *   <li>{@code ${CLAUDE_PLUGIN_ROOT}/rules/} — plugin-bundled rules</li>
   *   <li>{@code ${projectPath}/.cat/rules/} — project-local rules</li>
   * </ol>
   * Both sources are concatenated; no filename-based deduplication is performed.
   *
   * @return a result with the injected rules content, or empty if no rules apply
   */
  @Override
  public Result handle(ClaudeHook scope)
  {
    Path pluginRulesDir = scope.getPluginRoot().resolve("rules");
    Path projectRulesDir = scope.getCatDir().resolve("rules");
    // Rules with paths: restrictions are injected dynamically by InjectPathRules (PreToolUse hook)
    // when matching files are accessed. Only non-paths rules are injected here at session start.
    String content = RulesDiscovery.getCatRulesForAudience(List.of(pluginRulesDir, projectRulesDir),
      scope.getYamlMapper(), RulesDiscovery::filterForMainAgent, List.of());
    if (content.isBlank())
      return Result.empty();

    return Result.context(content);
  }
}
