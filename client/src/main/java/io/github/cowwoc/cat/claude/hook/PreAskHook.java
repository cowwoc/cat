/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.cat.claude.hook.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.ask.WarnApprovalWithoutRenderDiff;
import io.github.cowwoc.cat.claude.hook.ask.WarnUnsquashedApproval;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

/**
 * Unified PreToolUse hook for AskUserQuestion.
 * <p>
 * TRIGGER: PreToolUse (matcher: AskUserQuestion)
 * <p>
 * Consolidates all AskUserQuestion validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can inject additional context or warnings when asking user questions.
 */
public final class PreAskHook implements HookHandler
{
  private final List<AskHandler> handlers;

  /**
   * Creates a new PreAskHook instance with default handlers.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public PreAskHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new WarnUnsquashedApproval(),
      new WarnApprovalWithoutRenderDiff(scope));
  }

  /**
   * Creates a new PreAskHook instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if handlers is null
   */
  public PreAskHook(List<AskHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = List.copyOf(handlers);
  }

  /**
   * Entry point for the AskUserQuestion pretool output hook.
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
      LoggerFactory.getLogger(PreAskHook.class).error("Failed to create JVM scope", e);
      System.err.println("Hook failed: " + e.getMessage());
    }
  }

  /**
   * Testable entry point for processing hook data with injectable I/O.
   *
   * @param scope the hook scope providing input data and output building
   * @param args command line arguments (unused)
   * @param in input stream (unused)
   * @param out output stream for writing JSON response
   * @throws NullPointerException if {@code scope}, {@code args}, {@code in}, or {@code out} are null
   */
  public static void run(ClaudeHook scope, String[] args, InputStream in, PrintStream out)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(in, "in").isNotNull();
    requireThat(out, "out").isNotNull();
    HookRunner.execute(PreAskHook::new, scope, out);
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
    if (!equalsIgnoreCase(toolName, "AskUserQuestion"))
      return HookResult.withoutWarnings(scope.empty());

    JsonNode toolInput = scope.getToolInput();
    String sessionId = scope.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();

    for (AskHandler handler : this.handlers)
    {
      try
      {
        AskHandler.Result result = handler.check(toolInput, sessionId);
        if (result.blocked())
        {
          String jsonOutput;
          if (result.additionalContext().isEmpty())
            jsonOutput = scope.block(result.reason());
          else
            jsonOutput = scope.block(result.reason(), result.additionalContext());
          return HookResult.withoutWarnings(jsonOutput);
        }
        // Ask handlers inject context into the question. Only one context can be injected,
        // so we return on the first handler that provides additionalContext.
        if (!result.additionalContext().isEmpty())
        {
          String jsonOutput = scope.additionalContext("PreToolUse", result.additionalContext());
          return HookResult.withoutWarnings(jsonOutput);
        }
        if (!result.reason().isEmpty())
          return new HookResult(scope.empty(), List.of(result.reason()));
      }
      catch (RuntimeException e)
      {
        String jsonOutput = scope.block("Hook handler failed: " + handler.getClass().getSimpleName() +
          ": " + e.getMessage());
        return HookResult.withoutWarnings(jsonOutput);
      }
    }

    return HookResult.withoutWarnings(scope.empty());
  }
}
