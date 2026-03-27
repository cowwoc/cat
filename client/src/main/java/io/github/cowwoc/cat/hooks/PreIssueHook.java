/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.task.EnforceApprovalBeforeMerge;
import io.github.cowwoc.cat.hooks.task.EnforceCollectAfterAgent;
import io.github.cowwoc.cat.hooks.task.EnforceCommitBeforeSubagentSpawn;
import io.github.cowwoc.cat.hooks.task.EnforceWorktreeSafetyBeforeMerge;
import tools.jackson.databind.JsonNode;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified PreToolUse hook for Task and Skill operations.
 * <p>
 * TRIGGER: PreToolUse (matcher: Task|Skill)
 * <p>
 * Consolidates all Task and Skill validation hooks into a single Java dispatcher. Handling both tools
 * together ensures that approval gate enforcement covers all paths to spawn the work-merge workflow —
 * whether via Task (subagent spawn) or via Skill (direct skill invocation).
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Block task operations (return decision=block with reason)</li>
 *   <li>Warn about task operations (return warning)</li>
 *   <li>Allow task operations (return allow)</li>
 * </ul>
 */
public final class PreIssueHook implements HookHandler
{
  private final List<TaskHandler> handlers;

  /**
   * Creates a new PreIssueHook instance with default handlers.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public PreIssueHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new EnforceCollectAfterAgent(scope),
      new EnforceCommitBeforeSubagentSpawn(scope),
      new EnforceWorktreeSafetyBeforeMerge(),
      new EnforceApprovalBeforeMerge(scope));
  }

  /**
   * Creates a new PreIssueHook instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if handlers is null
   */
  public PreIssueHook(List<TaskHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = List.copyOf(handlers);
  }

  /**
   * Entry point for the Task pretool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(PreIssueHook.class).error("Failed to create JVM scope", e);
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
    HookRunner.execute(PreIssueHook::new, scope, out);
  }

  /**
   * Processes hook data and returns the result with any warnings.
   *
   * @param scope the hook scope providing input data and output building
   * @return the hook result containing JSON output and warnings
   */
  @Override
  public HookResult run(ClaudeHook scope)
  {
    String toolName = scope.getToolName();
    boolean isTask = equalsIgnoreCase(toolName, "Task");
    boolean isSkill = equalsIgnoreCase(toolName, "Skill");
    if (!isTask && !isSkill)
      return HookResult.withoutWarnings(scope.empty());

    JsonNode toolInput = scope.getToolInput();
    String sessionId = scope.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    String cwd = scope.getString("cwd");
    List<String> warnings = new ArrayList<>();

    for (TaskHandler handler : this.handlers)
    {
      try
      {
        TaskHandler.Result result = handler.check(toolInput, sessionId, cwd);
        if (result.blocked())
        {
          String jsonOutput;
          if (result.additionalContext().isEmpty())
            jsonOutput = scope.block(result.reason());
          else
            jsonOutput = scope.block(result.reason(), result.additionalContext());
          return HookResult.withoutWarnings(jsonOutput);
        }
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (RuntimeException e)
      {
        String jsonOutput = scope.block("Hook handler failed: " + handler.getClass().getSimpleName() +
          ": " + e.getMessage());
        return HookResult.withoutWarnings(jsonOutput);
      }
    }

    return new HookResult(scope.empty(), warnings);
  }
}
