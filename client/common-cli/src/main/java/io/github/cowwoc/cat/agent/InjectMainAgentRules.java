/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;


/**
 * Injects audience-filtered rules from plugin-bundled and project-local rule directories into main
 * agent context.
 * <p>
 * Discovers rule files from portable and engine-specific rule directories, filters to those with
 * {@code mainAgent: true}, applies any {@code paths} restrictions, and injects matching content as
 * additional context.
 */
public final class InjectMainAgentRules implements SessionStartHandler
{
  private final AgentPluginScope scope;

  /**
   * Creates a new InjectMainAgentRules handler.
   *
   * @param scope the hook scope
   * @throws NullPointerException if scope is null
   */
  public InjectMainAgentRules(AgentPluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Discovers and injects CAT rules applicable to the main agent.
   * <p>
   * Reads from the engine-specific scope's ordered rule directories.
   * All sources are concatenated; no filename-based deduplication is performed.
   *
   * @return a result with the injected rules content, or empty if no rules apply
   */
  @Override
  public Result handle()
  {
    String content = MainAgentRules.load(scope, scope.getYamlMapper());
    if (content.isBlank())
      return Result.empty();

    return Result.context(content);
  }
}
