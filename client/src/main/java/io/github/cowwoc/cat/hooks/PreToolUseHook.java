/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.bash.BlockLockManipulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cowwoc.cat.hooks.bash.BlockMainRebase;
import io.github.cowwoc.cat.hooks.bash.BlockMergeCommits;
import io.github.cowwoc.cat.hooks.bash.BlockReflogDestruction;
import io.github.cowwoc.cat.hooks.bash.BlockUnsafeRemoval;
import io.github.cowwoc.cat.hooks.bash.ComputeBoxLines;
import io.github.cowwoc.cat.hooks.bash.RemindGitSquash;
import io.github.cowwoc.cat.hooks.bash.ValidateCommitType;
import io.github.cowwoc.cat.hooks.bash.ValidateGitFilterBranch;
import io.github.cowwoc.cat.hooks.bash.ValidateGitOperations;
import io.github.cowwoc.cat.hooks.bash.VerifyStateInCommit;
import io.github.cowwoc.cat.hooks.bash.WarnFileExtraction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified PreToolUse hook for Bash commands.
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
public final class PreToolUseHook implements HookHandler
{
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final List<BashHandler> handlers;

  /**
   * Creates a new PreToolUseHook instance with the specified JVM scope.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public PreToolUseHook(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new BlockLockManipulation(),
      new BlockMainRebase(),
      new BlockMergeCommits(),
      new BlockReflogDestruction(),
      new BlockUnsafeRemoval(),
      new ComputeBoxLines(scope),
      new RemindGitSquash(),
      new ValidateCommitType(),
      new ValidateGitFilterBranch(),
      new ValidateGitOperations(),
      new VerifyStateInCommit(),
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
      HookOutput output = new HookOutput(scope);
      HookResult result = new PreToolUseHook(scope).run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(PreToolUseHook.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Processes hook input and returns the result with any warnings.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String toolName = input.getToolName();
    if (!equalsIgnoreCase(toolName, "Bash"))
      return HookResult.withoutWarnings(output.empty());

    JsonNode toolInput = input.getToolInput();
    JsonNode commandNode = toolInput.get("command");
    String command;
    if (commandNode != null)
      command = commandNode.asString();
    else
      command = "";

    if (command.isEmpty())
      return HookResult.withoutWarnings(output.empty());

    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    String workingDirectory = input.getString("cwd");
    requireThat(workingDirectory, "workingDirectory").isNotBlank();
    List<String> warnings = new ArrayList<>();

    // Run all bash pretool handlers
    for (BashHandler handler : handlers)
    {
      try
      {
        BashHandler.Result result = handler.check(command, workingDirectory, toolInput, null, sessionId);
        if (result.blocked())
        {
          String jsonOutput;
          if (result.additionalContext().isEmpty())
            jsonOutput = output.block(result.reason());
          else
            jsonOutput = output.block(result.reason(), result.additionalContext());
          return new HookResult(jsonOutput, warnings);
        }
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (RuntimeException e)
      {
        log.error("get-bash-pretool-output: handler error", e);
      }
    }

    // Allow the command
    return new HookResult(output.empty(), warnings);
  }
}
