package io.github.cowwoc.cat.hooks.bash.post;

import io.github.cowwoc.cat.hooks.BashHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Detect command failures and suggest learning from mistakes.
 *
 * <p>Trigger: PostToolUse for Bash</p>
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
  @SuppressWarnings("UnusedVariable")
  public Result check(String _command, JsonNode _toolInput, JsonNode toolResult, String _sessionId)
  {
    if (toolResult == null)
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

        Consider:
        1. Fix the immediate issue
        2. If this could recur, use learn skill
           to implement prevention

        See: .claude/skills/learn/SKILL.md""", exitCode));
    }

    return Result.allow();
  }
}
