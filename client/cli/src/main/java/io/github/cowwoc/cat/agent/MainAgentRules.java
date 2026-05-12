/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.List;

/**
 * Loads session-start rules for the main agent across supported runtimes.
 */
public final class MainAgentRules
{
  private MainAgentRules()
  {
  }

  /**
   * Loads the non-path-restricted rules for the scope's active runtime.
   *
   * @param scope the plugin scope
   * @param yamlMapper the YAML mapper used for rule frontmatter
   * @return the concatenated rule content, or an empty string if no rules apply
   * @throws NullPointerException if any parameter is null
   */
  public static String load(AgentPluginScope scope, YAMLMapper yamlMapper)
  {
    return load(scope, yamlMapper, false);
  }

  /**
   * Loads the main-agent rules for the scope's active runtime.
   *
   * @param scope the plugin scope
   * @param yamlMapper the YAML mapper used for rule frontmatter
   * @param includePathRestricted true if path-restricted rules must be injected at session start
   * @return the concatenated rule content, or an empty string if no rules apply
   * @throws NullPointerException if any parameter is null
   */
  public static String load(AgentPluginScope scope, YAMLMapper yamlMapper, boolean includePathRestricted)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(yamlMapper, "yamlMapper").isNotNull();
    if (includePathRestricted)
    {
      return RulesDiscovery.getCatRulesForAudience(scope.getRuleDirectories(), yamlMapper,
        RulesDiscovery::filterForMainAgentIgnoringPaths, List.of());
    }
    return RulesDiscovery.getCatRulesForAudience(scope.getRuleDirectories(), yamlMapper,
      RulesDiscovery::filterForMainAgent, List.of());
  }
}
