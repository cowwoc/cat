/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.write.EnforcePluginFileIsolation;
import io.github.cowwoc.cat.hooks.write.StateSchemaValidator;
import io.github.cowwoc.cat.hooks.write.ValidateStateMdFormat;
import io.github.cowwoc.cat.hooks.write.WarnBaseBranchEdit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Hook: Enforce Worktree Isolation (M252).
 * <p>
 * Unified PreToolUse hook for Edit/Write operations.
 * <p>
 * TRIGGER: PreToolUse for Edit/Write
 * <p>
 * REGISTRATION: plugin/hooks/hooks.json (plugin hook)
 * <p>
 * Consolidates all Edit/Write validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Block edits (return decision=block with reason)</li>
 *   <li>Warn about edits (return warning)</li>
 *   <li>Allow edits (return allow)</li>
 * </ul>
 */
public final class GetWriteEditOutput implements HookHandler
{
  // Handlers are checked in order. WarnBaseBranchEdit warns first (non-blocking),
  // then blocking handlers (ValidateStateMdFormat, StateSchemaValidator,
  // EnforcePluginFileIsolation) run. This ensures warnings are emitted even when
  // validation would block the edit.
  private static final List<FileWriteHandler> DEFAULT_HANDLERS = List.of(
    new WarnBaseBranchEdit(),
    new ValidateStateMdFormat(),
    new StateSchemaValidator(),
    new EnforcePluginFileIsolation());
  private final List<FileWriteHandler> handlers;

  /**
   * Creates a new GetWriteEditOutput instance with default handlers.
   */
  public GetWriteEditOutput()
  {
    this.handlers = DEFAULT_HANDLERS;
  }

  /**
   * Creates a new GetWriteEditOutput instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if handlers is null
   */
  public GetWriteEditOutput(List<FileWriteHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = List.copyOf(handlers);
  }

  /**
   * Entry point for the Write/Edit PreToolUse hook.
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
      HookResult result = new GetWriteEditOutput().run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetWriteEditOutput.class);
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
    if (!(equalsIgnoreCase(toolName, "Write") || equalsIgnoreCase(toolName, "Edit")))
      return HookResult.withoutWarnings(output.empty());

    JsonNode toolInput = input.getToolInput();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();

    for (FileWriteHandler handler : this.handlers)
    {
      try
      {
        FileWriteHandler.Result result = handler.check(toolInput, sessionId);
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
