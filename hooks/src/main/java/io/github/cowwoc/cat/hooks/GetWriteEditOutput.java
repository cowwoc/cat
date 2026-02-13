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
      HookOutput output = new HookOutput(mapper, System.out);
      new GetWriteEditOutput().run(input, output);
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
    if (!(equalsIgnoreCase(toolName, "Write") || equalsIgnoreCase(toolName, "Edit")))
    {
      output.empty();
      return;
    }

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
