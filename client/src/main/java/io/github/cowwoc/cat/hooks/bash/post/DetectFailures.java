/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash.post;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.HookInput;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Detect command failures and suggest learning from mistakes.
 * <p>
 * Trigger: PostToolUse for Bash
 */
public final class DetectFailures implements BashHandler
{
  private static final Pattern FAILURE_PATTERN = Pattern.compile(
    "BUILD FAILED|FAILED|ERROR:|error:|Exception|FATAL|fatal:",
    Pattern.CASE_INSENSITIVE);

  /**
   * Creates a new handler for detecting command failures.
   */
  public DetectFailures()
  {
    // Handler class
  }

  @Override
  public Result check(HookInput input)
  {
    JsonNode toolResult = input.getToolResult();

    // treat empty tool_result object as absent (PreToolUse context has no tool result)
    if (toolResult.isEmpty())
      return Result.allow();

    // Get exit code
    int exitCode = 0;
    JsonNode exitCodeNode = toolResult.get("exit_code");
    if (exitCodeNode != null && exitCodeNode.isNumber())
    {
      exitCode = exitCodeNode.asInt();
    }
    else
    {
      JsonNode exitCodeCamelNode = toolResult.get("exitCode");
      if (exitCodeCamelNode != null && exitCodeCamelNode.isNumber())
      {
        exitCode = exitCodeCamelNode.asInt();
      }
    }

    // Skip if successful
    if (exitCode == 0)
      return Result.allow();

    // Get output
    String stdout = "";
    String stderr = "";
    JsonNode stdoutNode = toolResult.get("stdout");
    if (stdoutNode != null)
      stdout = stdoutNode.asString();
    JsonNode stderrNode = toolResult.get("stderr");
    if (stderrNode != null)
      stderr = stderrNode.asString();
    String output = stdout + stderr;

    // Check for failure patterns
    if (FAILURE_PATTERN.matcher(output).find())
    {
      return Result.warn(String.format("""

        ----------------------------------------
        Failure detected (exit code: %d)
        ----------------------------------------

        MANDATORY: Invoke cat:learn-agent to record this mistake and implement prevention.
        Do NOT continue to the next step without invoking learn first.

        Steps:
        1. Fix the immediate issue
        2. Invoke cat:learn-agent to record the mistake and prevent recurrence

        See: /cat:learn-agent""", exitCode));
    }

    return Result.allow();
  }
}
