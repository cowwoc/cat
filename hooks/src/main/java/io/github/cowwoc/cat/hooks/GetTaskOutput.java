package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.task.EnforceApprovalBeforeMerge;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * get-task-pretool-output - Unified PreToolUse hook for Task operations.
 * <p>
 * TRIGGER: PreToolUse (matcher: Task)
 * <p>
 * Consolidates all Task validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Block task operations (return decision=block with reason)</li>
 *   <li>Warn about task operations (return warning)</li>
 *   <li>Allow task operations (return allow)</li>
 * </ul>
 */
public final class GetTaskOutput implements HookHandler
{
  private static final List<TaskHandler> DEFAULT_HANDLERS = List.of(
    new EnforceApprovalBeforeMerge());
  private final List<TaskHandler> handlers;

  /**
   * Creates a new GetTaskOutput instance with default handlers.
   */
  public GetTaskOutput()
  {
    this.handlers = DEFAULT_HANDLERS;
  }

  /**
   * Creates a new GetTaskOutput instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if handlers is null
   */
  public GetTaskOutput(List<TaskHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = List.copyOf(handlers);
  }

  /**
   * Entry point for the Task pretool output hook.
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
      new GetTaskOutput().run(input, output);
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
    if (!equalsIgnoreCase(toolName, "Task"))
    {
      output.empty();
      return;
    }

    JsonNode toolInput = input.getToolInput();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();

    for (TaskHandler handler : this.handlers)
    {
      try
      {
        TaskHandler.Result result = handler.check(toolInput, sessionId);
        if (result.blocked())
        {
          if (result.additionalContext().isEmpty())
            output.block(result.reason());
          else
            output.block(result.reason(), result.additionalContext());
          return;
        }
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (RuntimeException e)
      {
        output.block("Hook handler failed: " + handler.getClass().getSimpleName() +
          ": " + e.getMessage());
        return;
      }
    }

    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    output.empty();
  }
}
