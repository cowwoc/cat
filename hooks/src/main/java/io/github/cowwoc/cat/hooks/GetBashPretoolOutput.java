package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.bash.BlockLockManipulation;
import io.github.cowwoc.cat.hooks.bash.BlockMainRebase;
import io.github.cowwoc.cat.hooks.bash.BlockMergeCommits;
import io.github.cowwoc.cat.hooks.bash.BlockReflogDestruction;
import io.github.cowwoc.cat.hooks.bash.BlockWorktreeCd;
import io.github.cowwoc.cat.hooks.bash.ComputeBoxLines;
import io.github.cowwoc.cat.hooks.bash.RemindGitSquash;
import io.github.cowwoc.cat.hooks.bash.ValidateCommitType;
import io.github.cowwoc.cat.hooks.bash.ValidateGitFilterBranch;
import io.github.cowwoc.cat.hooks.bash.ValidateGitOperations;
import io.github.cowwoc.cat.hooks.bash.WarnFileExtraction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * get-bash-pretool-output - Unified PreToolUse hook for Bash commands.
 * <p>
 * TRIGGER: PreToolUse (matcher: Bash)
 * <p>
 * Consolidates all Bash command validation hooks into a single Java dispatcher.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Block commands (return decision=block with reason)</li>
 *   <li>Warn about commands (return warning)</li>
 *   <li>Allow commands (return allow)</li>
 * </ul>
 */
public final class GetBashPretoolOutput implements HookHandler
{
  private final List<BashHandler> handlers;

  /**
   * Creates a new GetBashPretoolOutput instance with the specified JVM scope.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public GetBashPretoolOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new BlockLockManipulation(),
      new BlockMainRebase(),
      new BlockMergeCommits(),
      new BlockReflogDestruction(),
      new BlockWorktreeCd(),
      new ComputeBoxLines(scope),
      new RemindGitSquash(),
      new ValidateCommitType(),
      new ValidateGitFilterBranch(),
      new ValidateGitOperations(),
      new WarnFileExtraction());
  }

  /**
   * Entry point for the Bash pretool output hook.
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
      new GetBashPretoolOutput(scope).run(input, output);
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

    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();

    // Run all bash pretool handlers
    for (BashHandler handler : handlers)
    {
      try
      {
        BashHandler.Result result = handler.check(command, toolInput, null, sessionId);
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
        System.err.println("get-bash-pretool-output: handler error: " + e.getMessage());
      }
    }

    // Output warnings if any
    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    // Allow the command
    output.empty();
  }
}
