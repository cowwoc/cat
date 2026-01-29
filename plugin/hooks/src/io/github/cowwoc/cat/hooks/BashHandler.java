package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;

/**
 * Interface for bash command handlers.
 *
 * <p>Bash handlers validate bash commands before or after execution.
 * PreToolUse handlers can block commands; PostToolUse handlers can only warn.</p>
 */
@FunctionalInterface
public interface BashHandler
{
  /**
   * The result of a bash handler check.
   *
   * @param blocked whether the command should be blocked (PreToolUse only)
   * @param reason the reason for blocking or warning
   * @param additionalContext optional additional context to inject
   */
  record Result(boolean blocked, String reason, String additionalContext)
  {
    /**
     * Creates a new bash handler result.
     *
     * @param blocked whether the command should be blocked
     * @param reason the reason for blocking or warning
     * @param additionalContext optional additional context to inject
     */
    public Result
    {
      // reason and additionalContext use "" not null per Java conventions
    }

    /**
     * Creates an allow result (no blocking, no warning).
     *
     * @return an allow result
     */
    public static Result allow()
    {
      return new Result(false, "", "");
    }

    /**
     * Creates a block result (PreToolUse only).
     *
     * @param reason the reason for blocking
     * @return a block result
     */
    public static Result block(String reason)
    {
      return new Result(true, reason, "");
    }

    /**
     * Creates a block result with additional context (PreToolUse only).
     *
     * @param reason the reason for blocking
     * @param additionalContext additional context to inject
     * @return a block result
     */
    public static Result block(String reason, String additionalContext)
    {
      return new Result(true, reason, additionalContext);
    }

    /**
     * Creates a warning result (allows but warns).
     *
     * @param warning the warning message
     * @return a warning result
     */
    public static Result warn(String warning)
    {
      return new Result(false, warning, "");
    }
  }

  /**
   * Check a bash command.
   *
   * @param command the bash command string
   * @param toolInput the full tool input JSON
   * @param toolResult the tool result JSON (null for PreToolUse)
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if command, toolInput, or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId);
}
