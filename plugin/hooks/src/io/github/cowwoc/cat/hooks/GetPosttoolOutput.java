package io.github.cowwoc.cat.hooks;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * get-posttool-output - Unified PostToolUse hook for all tools
 *
 * TRIGGER: PostToolUse (no matcher - runs for all tools)
 *
 * Consolidates general PostToolUse hooks into a single Java dispatcher.
 * For Bash-specific PostToolUse hooks, see GetBashPosttoolOutput.
 *
 * Handlers can:
 * - Warn about tool results (return warning string)
 * - Inject additional context (return additionalContext)
 * - Allow silently (return null)
 */
public final class GetPosttoolOutput
{
  /**
   * Entry point for the general posttool output hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    HookInput input = HookInput.readFromStdin();

    String toolName = input.getToolName();
    if (toolName == null || toolName.isEmpty())
    {
      HookOutput.empty();
      return;
    }

    JsonNode toolResult = input.getToolResult();
    String sessionId = input.getSessionId();

    List<String> warnings = new ArrayList<>();
    List<String> additionalContexts = new ArrayList<>();

    // TODO: Port posttool handlers from Python

    // Output warnings to stderr
    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    // Build response with additionalContext if present
    if (!additionalContexts.isEmpty())
    {
      String combined = String.join("\n\n", additionalContexts);
      HookOutput.additionalContext("PostToolUse", combined);
    }
    else
    {
      HookOutput.empty();
    }
  }
}
