/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash.post;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detect command failures and suggest learning from mistakes.
 * <p>
 * Trigger: PostToolUse for Bash
 * <p>
 * Pattern matching is scoped to known test runner commands to avoid false positives from arbitrary
 * Bash commands whose output happens to contain failure keywords (e.g., git diff showing diff content
 * with "FAILED" in a file, or cat reading a file with "Exception" in its text).
 */
public final class DetectFailures implements BashHandler
{
  private static final Pattern FAILURE_PATTERN = Pattern.compile(
    "BUILD FAILED|FAILED|ERROR:|Exception|FATAL",
    Pattern.CASE_INSENSITIVE);

  /**
   * Command prefixes that identify known test runners.
   * <p>
   * Pattern matching is applied only when the executed command starts with one of these prefixes.
   * Commands not in this list (e.g., git, cat, javac, get-output) are skipped to prevent false positives
   * from incidental failure keywords in diff output or file content.
   */
  private static final List<String> TEST_RUNNER_PREFIXES = List.of(
    "mvn ",
    "mvnw ",
    "./mvnw ",
    "gradle ",
    "gradlew ",
    "./gradlew ",
    "bats ",
    "bats\t",
    "npm test",
    "npm run test",
    "yarn test",
    "pytest",
    "cargo test",
    "go test",
    "dotnet test");

  /**
   * Creates a new handler for detecting command failures.
   */
  public DetectFailures()
  {
    // Handler class
  }

  /**
   * Checks whether the given command is a known test runner invocation.
   *
   * @param command the bash command string to check
   * @return true if the command starts with a known test runner prefix
   * @throws NullPointerException if {@code command} is null
   */
  private static boolean isTestRunnerCommand(String command)
  {
    String normalized = command.strip();
    for (String prefix : TEST_RUNNER_PREFIXES)
    {
      // Prefix entries include a trailing delimiter (space or tab) to prevent startsWith() from
      // false-matching unrelated commands (e.g., "bats\t" avoids matching "batsman"). The trailing
      // delimiter also means startsWith() would miss an exact bare-command invocation like "bats"
      // (no arguments). prefix.strip() removes the delimiter to enable exact bare-command matching.
      if (normalized.startsWith(prefix) || normalized.equals(prefix.strip()))
        return true;
    }
    return false;
  }

  @Override
  public Result check(ClaudeHook scope)
  {
    JsonNode toolResult = scope.getToolResult();

    // treat empty tool_result object as absent (PreToolUse context has no tool result)
    if (toolResult.isEmpty())
      return Result.allow();

    // Only apply failure detection to known test runner commands to avoid false positives
    // from incidental keyword matches in diff output, file content, or unrelated command output.
    String command = scope.getCommand();
    if (!isTestRunnerCommand(command))
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
