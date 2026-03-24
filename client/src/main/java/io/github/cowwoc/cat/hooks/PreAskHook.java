/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ask.WarnApprovalWithoutRenderDiff;
import io.github.cowwoc.cat.hooks.ask.WarnUnsquashedApproval;
import tools.jackson.databind.JsonNode;

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
    HookRunner.execute(PreAskHook::new, args);
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
