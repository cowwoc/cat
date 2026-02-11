package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.edit.EnforceWorkflowCompletion;
import io.github.cowwoc.cat.hooks.edit.WarnSkillEditWithoutBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

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
public final class GetEditPretoolOutput implements HookHandler
{
  // Handlers are checked in order. EnforceWorkflowCompletion blocks first if approval is missing,
  // then WarnSkillEditWithoutBuilder warns about skill editing patterns (non-blocking).
  private static final List<EditHandler> DEFAULT_HANDLERS = List.of(
    new EnforceWorkflowCompletion(),
    new WarnSkillEditWithoutBuilder());
  private final List<EditHandler> handlers;

  /**
   * Creates a new GetEditPretoolOutput instance with default handlers.
   */
  public GetEditPretoolOutput()
  {
    this.handlers = DEFAULT_HANDLERS;
  }

  /**
   * Creates a new GetEditPretoolOutput instance with custom handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if handlers is null
   */
  public GetEditPretoolOutput(List<EditHandler> handlers)
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
      HookOutput output = new HookOutput(mapper, System.out);
      new GetEditPretoolOutput().run(input, output);
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
    if (!equalsIgnoreCase(toolName, "Edit"))
    {
      output.empty();
      return;
    }

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
