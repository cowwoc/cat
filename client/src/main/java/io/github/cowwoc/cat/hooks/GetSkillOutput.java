/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.prompt.CriticalThinking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cowwoc.cat.hooks.prompt.DestructiveOps;
import io.github.cowwoc.cat.hooks.prompt.DetectGivingUp;

import java.util.ArrayList;
import java.util.List;

/**
 * get-skill-output - Unified UserPromptSubmit hook for CAT
 *
 * TRIGGER: UserPromptSubmit
 *
 * This dispatcher consolidates all UserPromptSubmit hooks into a single Java
 * entry point for prompt pattern checking.
 */
public final class GetSkillOutput implements HookHandler
{
  private final List<PromptHandler> handlers;

  /**
   * Creates a new GetSkillOutput instance.
   *
   * @param scope the JVM scope providing singleton handlers
   * @throws NullPointerException if scope is null
   */
  public GetSkillOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new CriticalThinking(),
      new DestructiveOps(),
      new DetectGivingUp(),
      scope.getUserIssues());
  }

  /**
   * Entry point for the skill output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      HookInput input = HookInput.readFromStdin(scope.getJsonMapper());
      HookOutput output = new HookOutput(scope);
      HookResult result = new GetSkillOutput(scope).run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetSkillOutput.class);
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
        warnings.add("get-skill-output: prompt handler error: " + e.getMessage());
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
