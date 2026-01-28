package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * get-bash-posttool-output - Unified PostToolUse hook for Bash commands
 *
 * TRIGGER: PostToolUse (matcher: Bash)
 *
 * Consolidates all Bash command result validation hooks into a single Java dispatcher.
 *
 * Handlers can:
 * - Warn about command results (return warning)
 * - Allow silently (return null)
 */
public final class GetBashPosttoolOutput
{
  /**
   * Entry point for the Bash posttool output hook.
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

    JsonNode toolResult = input.getToolResult();
    String sessionId = input.getSessionId();
    List<String> warnings = new ArrayList<>();

    // Run all bash posttool handlers
    for (BashHandler handler : HandlerRegistry.getBashPosttoolHandlers())
    {
      try
      {
        BashHandler.Result result = handler.check(command, toolInput, toolResult, sessionId);
        // PostToolUse cannot block, only warn
        if (result.reason() != null && !result.reason().isEmpty())
        {
          warnings.add(result.reason());
        }
      }
      catch (Exception e)
      {
        System.err.println("get-bash-posttool-output: handler error: " + e.getMessage());
      }
    }

    // Output warnings if any
    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    // Always allow (PostToolUse cannot block, only warn)
    HookOutput.empty();
  }
}
