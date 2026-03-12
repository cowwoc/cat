/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.RulesDiscovery;

import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Injects audience-filtered rules from plugin-bundled and project-local rule directories into main
 * agent context.
 * <p>
 * Discovers all rule files from both {@code ${CLAUDE_PLUGIN_ROOT}/rules/} (plugin-bundled) and
 * {@code ${projectDir}/.cat/rules/} (project-local), concatenates them (plugin-bundled
 * first, project-local second), filters to those with {@code mainAgent: true}, applies any
 * {@code paths} restrictions, and injects matching content as additional context.
 */
public final class InjectMainAgentRules implements SessionStartHandler
{
  private final JvmScope scope;

  /**
   * Creates a new InjectMainAgentRules handler.
   *
   * @param scope the JVM scope providing environment paths
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectMainAgentRules(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Discovers and injects CAT rules applicable to the main agent.
   * <p>
   * Reads from two sources in order (plugin-bundled first, project-local second):
   * <ol>
   *   <li>{@code ${CLAUDE_PLUGIN_ROOT}/rules/} — plugin-bundled rules</li>
   *   <li>{@code ${projectDir}/.cat/rules/} — project-local rules</li>
   * </ol>
   * Both sources are concatenated; no filename-based deduplication is performed.
   *
   * @param input the hook input
   * @return a result with the injected rules content, or empty if no rules apply
   * @throws NullPointerException if {@code input} is null
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();

    Path pluginRulesDir = scope.getClaudePluginRoot().resolve("rules");
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
