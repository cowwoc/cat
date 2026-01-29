package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.bash.BlockLockManipulation;
import io.github.cowwoc.cat.hooks.bash.BlockMainRebase;
import io.github.cowwoc.cat.hooks.bash.BlockMergeCommits;
import io.github.cowwoc.cat.hooks.bash.BlockReflogDestruction;
import io.github.cowwoc.cat.hooks.bash.ComputeBoxLines;
import io.github.cowwoc.cat.hooks.bash.RemindGitSquash;
import io.github.cowwoc.cat.hooks.bash.ValidateCommitType;
import io.github.cowwoc.cat.hooks.bash.ValidateGitFilterBranch;
import io.github.cowwoc.cat.hooks.bash.ValidateGitOperations;
import io.github.cowwoc.cat.hooks.bash.WarnFileExtraction;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * get-bash-pretool-output - Unified PreToolUse hook for Bash commands.
 *
 * <p>TRIGGER: PreToolUse (matcher: Bash)</p>
 *
 * <p>Consolidates all Bash command validation hooks into a single Java dispatcher.</p>
 *
 * <p>Handlers can:</p>
 * <ul>
 *   <li>Block commands (return decision=block with reason)</li>
 *   <li>Warn about commands (return warning)</li>
 *   <li>Allow commands (return allow)</li>
 * </ul>
 */
public final class GetBashPretoolOutput
{
  private GetBashPretoolOutput()
  {
    // Utility class
  }

  private static final List<BashHandler> HANDLERS = List.of(
    new BlockLockManipulation(),
    new BlockMainRebase(),
    new BlockMergeCommits(),
    new BlockReflogDestruction(),
    new ComputeBoxLines(),
    new RemindGitSquash(),
    new ValidateCommitType(),
    new ValidateGitFilterBranch(),
    new ValidateGitOperations(),
    new WarnFileExtraction());

  /**
   * Entry point for the Bash pretool output hook.
   *
   * @param _args command line arguments (unused)
   */
  @SuppressWarnings("UnusedVariable")
  public static void main(String[] _args)
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

    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();

    // Run all bash pretool handlers
    for (BashHandler handler : HANDLERS)
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
          warnings.add(result.reason());
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
