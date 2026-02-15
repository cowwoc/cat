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
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * get-ask-pretool-output - Unified PreToolUse hook for AskUserQuestion.
 * <p>
 * TRIGGER: PreToolUse (matcher: AskUserQuestion)
 * <p>
 * Consolidates all AskUserQuestion validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can inject additional context or warnings when asking user questions.
 */
public final class GetAskOutput implements HookHandler
{
  private final List<AskHandler> handlers;

  /**
   * Creates a new GetAskOutput instance with default handlers.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public GetAskOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new WarnUnsquashedApproval(),
      new WarnApprovalWithoutRenderDiff(scope));
  }

  /**
   * Creates a new GetAskOutput instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if handlers is null
   */
  public GetAskOutput(List<AskHandler> handlers)
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
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(scope);
      HookResult result = new GetAskOutput(scope).run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetAskOutput.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Processes hook input and returns the result with any warnings.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String toolName = input.getToolName();
    if (!equalsIgnoreCase(toolName, "AskUserQuestion"))
      return HookResult.withoutWarnings(output.empty());

    JsonNode toolInput = input.getToolInput();
    String sessionId = input.getSessionId();
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
            jsonOutput = output.block(result.reason());
          else
            jsonOutput = output.block(result.reason(), result.additionalContext());
          return HookResult.withoutWarnings(jsonOutput);
        }
        // Ask handlers inject context into the question. Only one context can be injected,
        // so we return on the first handler that provides additionalContext.
        if (!result.additionalContext().isEmpty())
        {
          String jsonOutput = output.additionalContext("PreToolUse", result.additionalContext());
          return HookResult.withoutWarnings(jsonOutput);
        }
        if (!result.reason().isEmpty())
          return new HookResult(output.empty(), List.of(result.reason()));
      }
      catch (RuntimeException e)
      {
        String jsonOutput = output.block("Hook handler failed: " + handler.getClass().getSimpleName() +
          ": " + e.getMessage());
        return HookResult.withoutWarnings(jsonOutput);
      }
    }

    return HookResult.withoutWarnings(output.empty());
  }
}
