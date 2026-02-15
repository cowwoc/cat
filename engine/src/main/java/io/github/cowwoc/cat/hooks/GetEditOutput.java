/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.edit.EnforceWorkflowCompletion;
import io.github.cowwoc.cat.hooks.edit.WarnSkillEditWithoutBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * get-edit-pretool-output - Unified PreToolUse hook for Edit operations.
 * <p>
 * TRIGGER: PreToolUse (matcher: Edit)
 * <p>
 * Consolidates all Edit validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Block edits (return decision=block with reason)</li>
 *   <li>Warn about edits (return warning)</li>
 *   <li>Allow edits (return allow)</li>
 * </ul>
 */
public final class GetEditOutput implements HookHandler
{
  // Handlers are checked in order. EnforceWorkflowCompletion blocks first if approval is missing,
  // then WarnSkillEditWithoutBuilder warns about skill editing patterns (non-blocking).
  private static final List<EditHandler> DEFAULT_HANDLERS = List.of(
    new EnforceWorkflowCompletion(),
    new WarnSkillEditWithoutBuilder());
  private final List<EditHandler> handlers;

  /**
   * Creates a new GetEditOutput instance with default handlers.
   */
  public GetEditOutput()
  {
    this.handlers = DEFAULT_HANDLERS;
  }

  /**
   * Creates a new GetEditOutput instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if handlers is null
   */
  public GetEditOutput(List<EditHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = List.copyOf(handlers);
  }

  /**
   * Entry point for the Edit pretool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(mapper);
      HookResult result = new GetEditOutput().run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetEditOutput.class);
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
    if (!equalsIgnoreCase(toolName, "Edit"))
      return HookResult.withoutWarnings(output.empty());

    JsonNode toolInput = input.getToolInput();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();

    for (EditHandler handler : this.handlers)
    {
      try
      {
        EditHandler.Result result = handler.check(toolInput, sessionId);
        if (result.blocked())
        {
          String jsonOutput;
          if (result.additionalContext().isEmpty())
            jsonOutput = output.block(result.reason());
          else
            jsonOutput = output.block(result.reason(), result.additionalContext());
          return HookResult.withoutWarnings(jsonOutput);
        }
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (RuntimeException e)
      {
        String jsonOutput = output.block("Hook handler failed: " + handler.getClass().getSimpleName() +
          ": " + e.getMessage());
        return HookResult.withoutWarnings(jsonOutput);
      }
    }

    return new HookResult(output.empty(), warnings);
  }
}
