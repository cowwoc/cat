/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.agent.RulesDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Injects audience-filtered rules from plugin-bundled and project-local rule directories into
 * subagent context.
 * <p>
 * Discovers rule files from portable {@code .cat/rules/common/} directories and Claude-specific
 * {@code .claude/rules/} directories, then filters using the {@code agents} frontmatter property.
 * Omitting {@code agents} reaches all subagents; {@code agents: ["main"]} excludes all subagents;
 * {@code agents: ["subagents"]} reaches all subagents without the main agent; specific types like
 * {@code agents: ["cat:work-execute"]} target only matching subagents.
 */
public final class InjectSubAgentRules implements SubagentStartHandler
{
  private final Logger log = LoggerFactory.getLogger(InjectSubAgentRules.class);
  private final ClaudeHook scope;

  /**
   * Creates a new InjectSubAgentRules handler.
   *
   * @param scope the hook scope
   * @throws NullPointerException if scope is null
   */
  public InjectSubAgentRules(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Discovers and injects CAT rules applicable to this subagent.
   * <p>
   * Reads from four sources in order:
   * <ol>
   *   <li>{@code ${CLAUDE_PLUGIN_ROOT}/rules/common/} — plugin-bundled portable rules</li>
   *   <li>{@code ${CLAUDE_PLUGIN_ROOT}/rules/claude/} — plugin-bundled Claude rules</li>
   *   <li>{@code ${projectPath}/.cat/rules/common/} — project-local portable rules</li>
   *   <li>{@code ${projectPath}/.claude/rules/} — project-local Claude rules</li>
   * </ol>
   * All sources are concatenated; no filename-based deduplication is performed.
   *
   * @return a result containing the filtered rule content, or an empty result if no rules apply
   */
  @Override
  public Result handle()
  {
    String subagentType = scope.getStringInput("subagent_type");
    if (subagentType.isBlank())
      log.debug("SubagentStart hook received blank subagent_type; rules requiring a specific " +
        "subagent type will not match");

    List<Path> rulesDirs = scope.getRuleDirectories();
    // Rules with paths: restrictions are injected dynamically by InjectPathRules (PreToolUse hook)
    // when matching files are accessed. For subagents, only non-paths rules are injected at start.
    String rules = RulesDiscovery.getCatRulesForAudience(rulesDirs, scope.getYamlMapper(),
      (r, activeFiles) -> RulesDiscovery.filterForAgent(r, subagentType, activeFiles),
      List.of());
    if (rules.isBlank())
      return Result.empty();
    return Result.context(rules);
  }
}
