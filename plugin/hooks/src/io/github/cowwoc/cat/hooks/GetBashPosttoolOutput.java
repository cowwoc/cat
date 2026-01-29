package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.bash.post.DetectConcatenatedCommit;
import io.github.cowwoc.cat.hooks.bash.post.DetectFailures;
import io.github.cowwoc.cat.hooks.bash.post.ValidateRebaseTarget;
import io.github.cowwoc.cat.hooks.bash.post.VerifyCommitType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * get-bash-posttool-output - Unified PostToolUse hook for Bash commands.
 *
 * <p>TRIGGER: PostToolUse (matcher: Bash)</p>
 *
 * <p>Consolidates all Bash command result validation hooks into a single Java dispatcher.</p>
 *
 * <p>Handlers can:</p>
 * <ul>
 *   <li>Warn about command results (return warning)</li>
 *   <li>Allow silently (return allow)</li>
 * </ul>
 */
public final class GetBashPosttoolOutput
{
  private static final List<BashHandler> HANDLERS = List.of(
    new DetectConcatenatedCommit(),
    new DetectFailures(),
    new ValidateRebaseTarget(),
    new VerifyCommitType());

  private GetBashPosttoolOutput()
  {
    // Utility class
  }

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
      command = commandNode.asString();
    else
      command = "";

    if (command.isEmpty())
    {
      HookOutput.empty();
      return;
    }

    JsonNode toolResult = input.getToolResult();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();

    // Run all bash posttool handlers
    for (BashHandler handler : HANDLERS)
    {
      try
      {
        BashHandler.Result result = handler.check(command, toolInput, toolResult, sessionId);
        // PostToolUse cannot block, only warn
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
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
