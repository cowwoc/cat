/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.ClearSkillMarker;
import io.github.cowwoc.cat.hooks.session.InjectCatAgentId;
import io.github.cowwoc.cat.hooks.session.InjectSubAgentRules;
import io.github.cowwoc.cat.hooks.session.SubagentStartHandler;
import io.github.cowwoc.cat.hooks.util.SkillDiscovery;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * SubagentStart hook dispatcher.
 * <p>
 * Consolidates all subagent start handlers into a single Java dispatcher. Each handler contributes
 * additional context for Claude and/or stderr messages for the user. The combined additional context from
 * all handlers is output as a single hookSpecificOutput JSON response.
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
  private final List<SubagentStartHandler> handlers;

  /**
   * Creates a new SubagentStartHook with the default handler list.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SubagentStartHook(JvmScope scope)
  {
    this(scope, List.of(
      input -> SubagentStartHandler.Result.context(
        InjectCatAgentId.getSubagentContext(input.getSessionId(), input.getAgentId())),
      input -> SubagentStartHandler.Result.ofStderr(
        new ClearSkillMarker(scope).clearSubagentMarker(
          input.getSessionId(), input.getAgentId())),
      input -> SubagentStartHandler.Result.ofContext(
        SkillDiscovery.getSubagentSkillListing(scope)),
      new InjectSubAgentRules(scope)));
  }

  /**
   * Creates a new SubagentStartHook with custom handlers (for testing).
   *
   * @param scope    the JVM scope providing environment configuration
   * @param handlers the handlers to run
   * @throws NullPointerException if {@code scope} or {@code handlers} are null
   */
  public SubagentStartHook(JvmScope scope, List<SubagentStartHandler> handlers)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = handlers;
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
   * Processes the SubagentStart hook by running all subagent start handlers and combining their output.
   *
   * @param input  the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output with the combined context and warnings
   * @throws NullPointerException     if {@code input} or {@code output} are null
   * @throws IllegalArgumentException if {@code session_id} or {@code agent_id} are blank
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String sessionId = input.getSessionId();
    if (sessionId.isBlank())
    {
      throw new IllegalArgumentException(
        "session_id is blank. SubagentStart hook requires a valid session ID.");
    }
    String agentId = input.getAgentId();
    if (agentId.isBlank())
    {
      throw new IllegalArgumentException(
        "agent_id is blank. SubagentStart hook requires a valid agent ID.");
    }

    StringJoiner combinedContext = new StringJoiner("\n\n");
    List<String> warnings = new ArrayList<>();

    for (SubagentStartHandler handler : handlers)
    {
      SubagentStartHandler.Result result = handler.handle(input);
      if (!result.stderr().isEmpty())
        warnings.add(result.stderr());
      if (!result.additionalContext().isEmpty())
        combinedContext.add(result.additionalContext());
    }

    if (combinedContext.length() == 0)
      return new HookResult(output.empty(), warnings);
    return new HookResult(output.additionalContext("SubagentStart",
      combinedContext.toString()), warnings);
  }
}
