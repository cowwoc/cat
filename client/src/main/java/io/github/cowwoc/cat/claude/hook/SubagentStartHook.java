/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.session.ClearAgentMarkers;
import io.github.cowwoc.cat.claude.hook.session.InjectCatAgentId;
import io.github.cowwoc.cat.claude.hook.session.InjectSubAgentRules;
import io.github.cowwoc.cat.claude.hook.session.SubagentStartHandler;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
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
 * listing. Behavioral instructions about when and how to invoke skills are provided separately via
 * {@code plugin/rules/subagent-skill-instructions.md}.
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
  public SubagentStartHook(ClaudeHook scope)
  {
    this(scope, List.of(
      s -> SubagentStartHandler.Result.context(
        InjectCatAgentId.getSubagentContext(s.getSessionId(), s.getAgentId())),
      s -> SubagentStartHandler.Result.ofStderr(
        new ClearAgentMarkers(scope).clearSubagentMarker(
          s.getSessionId(), s.getAgentId())),
      s -> SubagentStartHandler.Result.ofContext(
        SkillDiscovery.getSubagentSkillListing(scope)),
      new InjectSubAgentRules()));
  }

  /**
   * Creates a new SubagentStartHook with custom handlers (for testing).
   *
   * @param scope    the hook scope providing environment configuration
   * @param handlers the handlers to run
   * @throws NullPointerException if {@code scope} or {@code handlers} are null
   */
  public SubagentStartHook(ClaudeHook scope, List<SubagentStartHandler> handlers)
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
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(SubagentStartHook.class).error("Failed to create JVM scope", e);
      System.err.println("Hook failed: " + e.getMessage());
    }
  }

  /**
   * Testable entry point with injectable I/O.
   *
   * @param scope the hook scope
   * @param args command line arguments (unused)
   * @param in input stream (unused)
   * @param out output stream for writing JSON response
   * @throws NullPointerException if any argument is null
   */
  public static void run(ClaudeHook scope, String[] args, InputStream in, PrintStream out)
  {
    HookRunner.execute(SubagentStartHook::new, scope, out);
  }

  /**
   * Processes the SubagentStart hook by running all subagent start handlers and combining their output.
   *
   * @param scope the hook scope providing input data and output building
   * @return the hook result containing JSON output with the combined context and warnings
   * @throws IllegalArgumentException if {@code session_id} or {@code agent_id} are blank
   */
  @Override
  public HookResult run(ClaudeHook scope)
  {
    String sessionId = scope.getSessionId();
    if (sessionId.isBlank())
    {
      throw new IllegalArgumentException(
        "session_id is blank. SubagentStart hook requires a valid session ID.");
    }
    String agentId = scope.getAgentId();
    if (agentId.isBlank())
    {
      throw new IllegalArgumentException(
        "agent_id is blank. SubagentStart hook requires a valid agent ID.");
    }

    StringJoiner combinedContext = new StringJoiner("\n\n");
    List<String> warnings = new ArrayList<>();

    for (SubagentStartHandler handler : handlers)
    {
      SubagentStartHandler.Result result = handler.handle(scope);
      if (!result.stderr().isEmpty())
        warnings.add(result.stderr());
      if (!result.additionalContext().isEmpty())
        combinedContext.add(result.additionalContext());
    }

    if (combinedContext.length() == 0)
      return new HookResult(scope.empty(), warnings);
    return new HookResult(scope.additionalContext("SubagentStart",
      combinedContext.toString()), warnings);
  }
}
