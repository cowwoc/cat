/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.cat.claude.hook.Strings.wrapSystemReminder;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.prompt.DestructiveOps;
import io.github.cowwoc.cat.claude.hook.prompt.DetectGivingUp;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified UserPromptSubmit hook for CAT.
 * <p>
 * TRIGGER: UserPromptSubmit
 * <p>
 * This dispatcher consolidates all UserPromptSubmit hooks into a single Java
 * entry point for prompt pattern checking.
 */
public final class UserPromptSubmitHook implements HookHandler
{
  private final List<PromptHandler> handlers;

  /**
   * Creates a new UserPromptSubmitHook instance.
   *
   * @param scope the JVM scope providing singleton handlers
   * @throws NullPointerException if scope is null
   */
  public UserPromptSubmitHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new DestructiveOps(),
      new DetectGivingUp(),
      scope.getUserIssues());
  }

  /**
   * Entry point for the user prompt submit hook.
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
      LoggerFactory.getLogger(UserPromptSubmitHook.class).error("Failed to create JVM scope", e);
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
    HookRunner.execute(UserPromptSubmitHook::new, scope, out);
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
    String userPrompt = scope.getUserPrompt();
    if (userPrompt.isEmpty())
      return HookResult.withoutWarnings(scope.empty());

    String sessionId = scope.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> outputs = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // Run prompt handlers (pattern checking for all prompts)
    for (PromptHandler handler : handlers)
    {
      try
      {
        String result = handler.check(userPrompt, sessionId);
        if (!result.isEmpty())
          outputs.add(result);
      }
      catch (Exception e)
      {
        warnings.add("user-prompt-submit: prompt handler error: " + e.getMessage());
      }
    }

    // Build combined results
    String jsonOutput;
    if (!outputs.isEmpty())
    {
      StringBuilder combined = new StringBuilder();
      for (String out : outputs)
      {
        if (!combined.isEmpty())
          combined.append('\n');
        combined.append(wrapSystemReminder(out));
      }
      jsonOutput = scope.additionalContext("UserPromptSubmit", combined.toString());
    }
    else
    {
      jsonOutput = scope.empty();
    }

    return new HookResult(jsonOutput, warnings);
  }
}
