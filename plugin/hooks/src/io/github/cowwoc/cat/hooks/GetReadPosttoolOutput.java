package io.github.cowwoc.cat.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * get-read-posttool-output - Unified PostToolUse hook for Read/Glob/Grep/WebFetch/WebSearch
 *
 * TRIGGER: PostToolUse (matcher: Read|Glob|Grep|WebFetch|WebSearch)
 *
 * Consolidates read operation validation hooks into a single Java dispatcher.
 *
 * Handlers can:
 * - Warn about patterns (return warning)
 * - Allow silently (return null)
 */
public final class GetReadPosttoolOutput
{
  private static final Set<String> SUPPORTED_TOOLS = Set.of(
    "Read", "Glob", "Grep", "WebFetch", "WebSearch");

  /**
   * Entry point for the Read posttool output hook.
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
    JsonNode toolResult = input.getToolResult();
    String sessionId = input.getSessionId();
    List<String> warnings = new ArrayList<>();

    // TODO: Port read posttool handlers from Python

    // Output warnings if any
    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    // Always allow (PostToolUse cannot block, only warn)
    HookOutput.empty();
  }
}
