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
 * Injects audience-filtered rules from {@code .claude/cat/rules/} into main agent context.
 * <p>
 * Discovers all rule files, filters to those with {@code mainAgent: true}, applies any {@code paths}
 * restrictions, and injects matching content as additional context.
 */
public final class InjectCatRules implements SessionStartHandler
{
  private final JvmScope scope;

  /**
   * Creates a new InjectCatRules handler.
   *
   * @param scope the JVM scope providing environment paths
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectCatRules(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Discovers and injects CAT rules applicable to the main agent.
   *
   * @param input the hook input
   * @return a result with the injected rules content, or empty if no rules apply
   * @throws NullPointerException if {@code input} is null
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();

    Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
    // Rules with paths: restrictions are injected dynamically by InjectPathRules (PreToolUse hook)
    // when matching files are accessed. Only non-paths rules are injected here at session start.
    String content = RulesDiscovery.getCatRulesForAudience(rulesDir, scope.getYamlMapper(),
      RulesDiscovery::filterForMainAgent, List.of());
    if (content.isBlank())
      return Result.empty();

    return Result.context(content);
  }
}
