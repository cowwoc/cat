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
public final class GetSkillOutput implements HookHandler
{
  private static final List<PromptHandler> HANDLERS = List.of(
      new CriticalThinking(),
      new DestructiveOps(),
      new UserIssues());

  /**
   * Creates a new GetSkillOutput instance.
   */
  public GetSkillOutput()
  {
  }

  /**
   * Entry point for the skill output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    HookInput input = HookInput.readFromStdin();
    HookOutput output = new HookOutput(System.out);
    new GetSkillOutput().run(input, output);
  }

  /**
   * Processes hook input and writes the result.
   *
   * @param input the hook input to process
   * @param output the hook output writer
   * @throws NullPointerException if input or output is null
   */
  @Override
  public void run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String userPrompt = input.getUserPrompt();
    if (userPrompt.isEmpty())
    {
      output.empty();
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
      for (String out : outputs)
      {
        if (!combined.isEmpty())
          combined.append('\n');
        combined.append(HookOutput.wrapSystemReminder(out));
      }
      output.additionalContext("UserPromptSubmit", combined.toString());
    }
    else
    {
      output.empty();
    }
  }
}
