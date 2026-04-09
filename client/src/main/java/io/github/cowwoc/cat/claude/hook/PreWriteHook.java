/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.cat.claude.hook.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.edit.EnforceWorkflowCompletion;
import io.github.cowwoc.cat.claude.hook.write.BlockGitconfigFileWrite;
import io.github.cowwoc.cat.claude.hook.write.EnforcePluginFileIsolation;
import io.github.cowwoc.cat.claude.hook.write.EnforceWorktreePathIsolation;
import io.github.cowwoc.cat.claude.hook.write.StateSchemaValidator;
import io.github.cowwoc.cat.claude.hook.write.ValidateSkillTestFormat;
import io.github.cowwoc.cat.claude.hook.write.WarnBaseBranchEdit;
import tools.jackson.databind.JsonNode;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified PreToolUse hook for Write/Edit operations.
 * <p>
 * TRIGGER: PreToolUse for Write|Edit
 * <p>
 * REGISTRATION: plugin/hooks/hooks.json (plugin hook)
 * <p>
 * Consolidates all Write/Edit validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Block edits (return decision=block with reason)</li>
 *   <li>Warn about edits (return warning)</li>
 *   <li>Allow edits (return allow)</li>
 * </ul>
 */
public final class PreWriteHook implements HookHandler
{
  private final List<FileWriteHandler> handlers;

  /**
   * Creates a new PreWriteHook instance with default handlers.
   * <p>
   * Handlers are checked in order. EnforceWorkflowCompletion warns first, then WarnBaseBranchEdit
   * warns (non-blocking), followed by blocking handlers (StateSchemaValidator,
   * BlockGitconfigFileWrite, ValidateSkillTestFormat, EnforcePluginFileIsolation,
   * EnforceWorktreePathIsolation).
   *
   * @param scope the JVM scope providing project directory and shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public PreWriteHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new EnforceWorkflowCompletion(),
      new WarnBaseBranchEdit(scope),
      new StateSchemaValidator(scope),
      new BlockGitconfigFileWrite(),
      new ValidateSkillTestFormat(),
      new EnforcePluginFileIsolation(),
      new EnforceWorktreePathIsolation(scope));
  }

  /**
   * Creates a new PreWriteHook instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if {@code handlers} is null
   */
  public PreWriteHook(List<FileWriteHandler> handlers)
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
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(PreWriteHook.class).error("Failed to create JVM scope", e);
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
    HookRunner.execute(PreWriteHook::new, scope, out);
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
    if (!(equalsIgnoreCase(toolName, "Write") || equalsIgnoreCase(toolName, "Edit")))
      return HookResult.withoutWarnings(scope.empty());

    JsonNode toolInput = scope.getToolInput();
    String sessionId = scope.getSessionId();
    String catAgentId = scope.getCatAgentId(sessionId);
    requireThat(catAgentId, "catAgentId").isNotBlank();
    List<String> warnings = new ArrayList<>();

    StringBuilder additionalContextAccumulator = new StringBuilder();
    for (FileWriteHandler handler : this.handlers)
    {
      try
      {
        FileWriteHandler.Result result = handler.check(toolInput, catAgentId);
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
        if (!result.additionalContext().isEmpty())
        {
          if (!additionalContextAccumulator.isEmpty())
            additionalContextAccumulator.append('\n');
          additionalContextAccumulator.append(result.additionalContext());
        }
      }
      catch (RuntimeException e)
      {
        String jsonOutput = scope.block("Hook handler failed: " + handler.getClass().getSimpleName() +
          ": " + e.getMessage());
        return HookResult.withoutWarnings(jsonOutput);
      }
    }

    String jsonOutput;
    if (!additionalContextAccumulator.isEmpty())
      jsonOutput = scope.additionalContext("PreToolUse", additionalContextAccumulator.toString());
    else
      jsonOutput = scope.empty();
    return new HookResult(jsonOutput, warnings);
  }
}
