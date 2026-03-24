/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;

import io.github.cowwoc.cat.hooks.bash.post.DetectConcatenatedCommit;
import io.github.cowwoc.cat.hooks.bash.post.DetectFailures;
import io.github.cowwoc.cat.hooks.bash.post.ValidateRebaseTarget;
import io.github.cowwoc.cat.hooks.bash.post.VerifyCommitType;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified PostToolUse hook for Bash commands.
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
public final class PostBashHook implements HookHandler
{
  private static final List<BashHandler> HANDLERS = List.of(
    new DetectConcatenatedCommit(),
    new DetectFailures(),
    new ValidateRebaseTarget(),
    new VerifyCommitType());

  /**
   * Creates a new PostBashHook instance.
   */
  public PostBashHook()
  {
  }

  /**
   * Entry point for the Bash posttool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    HookRunner.execute(scope -> new PostBashHook(), args);
  }

  /**
   * Processes hook data and returns the result with any warnings.
   *
   * @param scope the hook scope providing input data and output building
   * @return the hook result containing JSON output and warnings
   */
  @Override
  public HookResult run(ClaudeHook scope)
  {
    String toolName = scope.getToolName();
    if (!equalsIgnoreCase(toolName, "Bash"))
      return HookResult.withoutWarnings(scope.empty());

    if (scope.getCommand().isEmpty())
      return HookResult.withoutWarnings(scope.empty());

    List<String> warnings = new ArrayList<>();
    List<String> errorWarnings = new ArrayList<>();

    // Run all bash posttool handlers
    for (BashHandler handler : HANDLERS)
    {
      try
      {
        BashHandler.Result result = handler.check(scope);
        // PostToolUse cannot block, only warn
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (Exception e)
      {
        errorWarnings.add("get-bash-posttool-output: handler error: " + e.getMessage());
      }
    }

    // Combine all warnings
    List<String> allWarnings = new ArrayList<>();
    allWarnings.addAll(warnings);
    allWarnings.addAll(errorWarnings);

    // Always allow (PostToolUse cannot block, only warn)
    return new HookResult(scope.empty(), allWarnings);
  }
}
