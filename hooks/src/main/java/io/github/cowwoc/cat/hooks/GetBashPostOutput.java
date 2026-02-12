package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.bash.post.DetectConcatenatedCommit;
import io.github.cowwoc.cat.hooks.bash.post.DetectFailures;
import io.github.cowwoc.cat.hooks.bash.post.ValidateRebaseTarget;
import io.github.cowwoc.cat.hooks.bash.post.VerifyCommitType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * get-bash-posttool-output - Unified PostToolUse hook for Bash commands.
 * <p>
 * TRIGGER: PostToolUse (matcher: Bash)
 * <p>
 * Consolidates all Bash command result validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Warn about command results (return warning)</li>
 *   <li>Allow silently (return allow)</li>
 * </ul>
 */
public final class GetBashPostOutput implements HookHandler
{
  private static final List<BashHandler> HANDLERS = List.of(
    new DetectConcatenatedCommit(),
    new DetectFailures(),
    new ValidateRebaseTarget(),
    new VerifyCommitType());

  /**
   * Creates a new GetBashPostOutput instance.
   */
  public GetBashPostOutput()
  {
  }

  /**
   * Entry point for the Bash posttool output hook.
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
      new GetBashPostOutput().run(input, output);
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
    if (!equalsIgnoreCase(toolName, "Bash"))
    {
      output.empty();
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
      output.empty();
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
    output.empty();
  }
}
