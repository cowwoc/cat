package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ask.WarnApprovalWithoutRenderDiff;
import io.github.cowwoc.cat.hooks.ask.WarnUnsquashedApproval;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

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
  // Handlers are checked in order. Ask handlers inject at most one context (first handler
  // providing additionalContext wins, then early return). This differs from Edit/Write/Task
  // dispatchers which accumulate all warnings, because only one context can be injected per question.
  private static final List<AskHandler> DEFAULT_HANDLERS = List.of(
    new WarnUnsquashedApproval(),
    new WarnApprovalWithoutRenderDiff());
  private final List<AskHandler> handlers;

  /**
   * Creates a new GetAskOutput instance with default handlers.
   */
  public GetAskOutput()
  {
    this.handlers = DEFAULT_HANDLERS;
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
      HookOutput output = new HookOutput(mapper, System.out);
      new GetAskOutput().run(input, output);
    }
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

    String toolName = input.getToolName();
    if (!equalsIgnoreCase(toolName, "AskUserQuestion"))
    {
      output.empty();
      return;
    }

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
          if (result.additionalContext().isEmpty())
            output.block(result.reason());
          else
            output.block(result.reason(), result.additionalContext());
          return;
        }
        // Ask handlers inject context into the question. Only one context can be injected,
        // so we return on the first handler that provides additionalContext.
        if (!result.additionalContext().isEmpty())
        {
          output.additionalContext("PreToolUse", result.additionalContext());
          return;
        }
        if (!result.reason().isEmpty())
          System.err.println(result.reason());
      }
      catch (RuntimeException e)
      {
        output.block("Hook handler failed: " + handler.getClass().getSimpleName() +
          ": " + e.getMessage());
        return;
      }
    }

    output.empty();
  }
}
