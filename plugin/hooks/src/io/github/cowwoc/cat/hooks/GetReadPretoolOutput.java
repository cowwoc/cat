package io.github.cowwoc.cat.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * get-read-pretool-output - Unified PreToolUse hook for Read/Glob/Grep
 *
 * TRIGGER: PreToolUse (matcher: Read|Glob|Grep)
 *
 * Consolidates read operation validation hooks into a single Java dispatcher.
 *
 * Handlers can:
 * - Warn about patterns (return warning)
 * - Block operations (return block=true with message)
 * - Allow silently (return null)
 */
public final class GetReadPretoolOutput
{
  private static final Set<String> SUPPORTED_TOOLS = Set.of("Read", "Glob", "Grep");

  /**
   * Entry point for the Read pretool output hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    HookInput input = HookInput.readFromStdin();

    String toolName = input.getToolName();
    if (!SUPPORTED_TOOLS.contains(toolName))
    {
      HookOutput.empty();
      return;
    }

    JsonNode toolInput = input.getToolInput();
    String sessionId = input.getSessionId();
    List<String> warnings = new ArrayList<>();

    // Run all read pretool handlers
    for (ReadHandler handler : HandlerRegistry.getReadPretoolHandlers())
    {
      try
      {
        ReadHandler.Result result = handler.check(toolName, toolInput, null, sessionId);
        if (result.blocked())
        {
          // Handler blocked the operation
          HookOutput.block(result.reason(), result.additionalContext());
          return;
        }
        if (result.reason() != null && !result.reason().isEmpty())
        {
          warnings.add(result.reason());
        }
      }
      catch (Exception e)
      {
        System.err.println("get-read-pretool-output: handler error: " + e.getMessage());
      }
    }

    // Output warnings if any
    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    // Allow the operation
    HookOutput.empty();
  }
}
