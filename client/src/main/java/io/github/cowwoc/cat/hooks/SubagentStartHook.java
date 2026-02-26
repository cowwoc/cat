/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.RulesDiscovery;
import io.github.cowwoc.cat.hooks.util.SkillDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * SubagentStart hook handler.
 * <p>
 * Injects the full model-invocable skill listing and audience-filtered CAT rules into subagent context
 * when a subagent starts. This ensures subagents know which skills are available and receive the
 * appropriate rules for their type without relying on static frontmatter preloading.
 * <p>
 * Each skill entry uses the format {@code "- name: description"}, matching Claude Code's native skill
 * listing. The header instructs subagents to use the Skill tool for invoking skills and includes
 * behavioral instructions about when to invoke them.
 * <p>
 * CAT rules are filtered using the {@code subAgents} frontmatter property: omitting {@code subAgents}
 * (or not providing frontmatter) reaches all subagents, {@code subAgents: []} excludes all subagents,
 * and specific types like {@code subAgents: ["cat:work-execute"]} target only matching subagents.
 */
public final class SubagentStartHook implements HookHandler
{
  private final JvmScope scope;

  /**
   * Creates a new SubagentStartHook.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SubagentStartHook(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Entry point for the SubagentStart hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    HookRunner.execute(SubagentStartHook::new, args);
  }

  /**
   * Processes the SubagentStart hook by injecting the skill listing and CAT rules as additional context.
   *
   * @param input  the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output with the skill listing and rules
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    StringBuilder combinedContext = new StringBuilder();

    String skillListing = SkillDiscovery.getSubagentSkillListing(scope);
    if (!skillListing.isEmpty())
      combinedContext.append(skillListing);

    String catRules = getCatRules(input);
    if (!catRules.isEmpty())
    {
      if (!combinedContext.isEmpty())
        combinedContext.append("\n\n");
      combinedContext.append(catRules);
    }

    if (combinedContext.isEmpty())
      return HookResult.withoutWarnings(output.empty());
    return HookResult.withoutWarnings(output.additionalContext("SubagentStart",
      combinedContext.toString()));
  }

  /**
   * Discovers and returns CAT rules applicable to this subagent.
   *
   * @param input the hook input containing the subagent type
   * @return the filtered rule content, or empty string if no rules apply
   */
  private String getCatRules(HookInput input)
  {
    String subagentType = input.getString("subagent_type", "");
    if (subagentType.isBlank())
    {
      Logger log = LoggerFactory.getLogger(SubagentStartHook.class);
      log.debug("SubagentStart hook received blank subagent_type; rules requiring a specific " +
        "subagent type will not match");
    }

    Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
    // Rules with paths: restrictions are injected dynamically by InjectPathRules (PreToolUse hook)
    // when matching files are accessed. For subagents, only non-paths rules are injected at start.
    return RulesDiscovery.getCatRulesForAudience(rulesDir, scope.getYamlMapper(),
      (rules, activeFiles) -> RulesDiscovery.filterForSubagent(rules, subagentType, activeFiles),
      List.of());
  }
}
