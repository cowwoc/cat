package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * get-bash-pretool-output - Unified PreToolUse hook for Bash commands
 *
 * TRIGGER: PreToolUse (matcher: Bash)
 *
 * Consolidates all Bash command validation hooks into a single Java dispatcher.
 *
 * Handlers can:
 * - Block commands (return decision=block with reason)
 * - Warn about commands (return warning)
 * - Allow commands (return null)
 */
public final class GetBashPretoolOutput
{
  /**
   * Entry point for the Bash pretool output hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    HookInput input = HookInput.readFromStdin();

    String toolName = input.getToolName();
    if (!equalsIgnoreCase(toolName, "Bash"))
    {
      HookOutput.empty();
      return;
    }

    JsonNode toolInput = input.getToolInput();
    JsonNode commandNode = toolInput.get("command");
    String command;
    if (commandNode != null)
    {
      command = commandNode.asString();
    }
    else
    {
      command = null;
    }

    if (command == null || command.isEmpty())
    {
      HookOutput.empty();
      return;
    }

    String sessionId = input.getSessionId();
    List<String> warnings = new ArrayList<>();

    // Run all bash pretool handlers
    for (BashHandler handler : HandlerRegistry.getBashPretoolHandlers())
    {
      try
      {
        BashHandler.Result result = handler.check(command, toolInput, null, sessionId);
        if (result.blocked())
        {
          // Handler blocked the command
          HookOutput.block(result.reason(), result.additionalContext());
          return;
        }
        if (!result.reason().isEmpty())
        {
          warnings.add(result.reason());
        }
      }
      catch (Exception e)
      {
        System.err.println("get-bash-pretool-output: handler error: " + e.getMessage());
      }
    }

    // Output warnings if any
    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    // Allow the command
    HookOutput.empty();
  }
}
