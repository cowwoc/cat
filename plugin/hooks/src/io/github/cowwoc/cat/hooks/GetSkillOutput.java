package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.prompt.CriticalThinking;
import io.github.cowwoc.cat.hooks.prompt.DestructiveOps;
import io.github.cowwoc.cat.hooks.prompt.UserIssues;

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
public final class GetSkillOutput
{
  private static final List<PromptHandler> HANDLERS = List.of(
      new CriticalThinking(),
      new DestructiveOps(),
      new UserIssues());

  private GetSkillOutput()
  {
    // Utility class
  }

  /**
   * Entry point for the skill output hook.
   *
   * @param _args command line arguments (unused)
   */
  @SuppressWarnings("UnusedVariable")
  public static void main(String[] _args)
  {
    HookInput input = HookInput.readFromStdin();

    String userPrompt = input.getUserPrompt();
    if (userPrompt.isEmpty())
    {
      HookOutput.empty();
      return;
    }

    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> outputs = new ArrayList<>();

    // Run prompt handlers (pattern checking for all prompts)
    for (PromptHandler handler : HANDLERS)
    {
      try
      {
        String result = handler.check(userPrompt, sessionId);
        if (!result.isEmpty())
          outputs.add(result);
      }
      catch (Exception e)
      {
        System.err.println("get-skill-output: prompt handler error: " + e.getMessage());
      }
    }

    // Output combined results
    if (!outputs.isEmpty())
    {
      StringBuilder combined = new StringBuilder();
      for (String output : outputs)
      {
        if (!combined.isEmpty())
          combined.append('\n');
        combined.append(HookOutput.wrapSystemReminder(output));
      }
      HookOutput.additionalContext("UserPromptSubmit", combined.toString());
    }
    else
    {
      HookOutput.empty();
    }
  }
}
