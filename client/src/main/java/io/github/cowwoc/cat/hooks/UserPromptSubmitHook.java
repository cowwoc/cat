/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.prompt.DestructiveOps;
import io.github.cowwoc.cat.hooks.prompt.DetectGivingUp;
import io.github.cowwoc.cat.hooks.prompt.ForcedEvalSkills;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public UserPromptSubmitHook(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new ForcedEvalSkills(scope),
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
    try (JvmScope scope = new MainJvmScope())
    {
      HookInput input = HookInput.readFromStdin(scope.getJsonMapper());
      HookOutput output = new HookOutput(scope);
      HookResult result = new UserPromptSubmitHook(scope).run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(UserPromptSubmitHook.class);
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

    String userPrompt = input.getUserPrompt();
    if (userPrompt.isEmpty())
      return HookResult.withoutWarnings(output.empty());

    String sessionId = input.getSessionId();
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
        combined.append(HookOutput.wrapSystemReminder(out));
      }
      jsonOutput = output.additionalContext("UserPromptSubmit", combined.toString());
    }
    else
    {
      jsonOutput = output.empty();
    }

    return new HookResult(jsonOutput, warnings);
  }
}
