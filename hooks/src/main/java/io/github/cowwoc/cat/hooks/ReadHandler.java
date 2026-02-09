package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;

/**
 * Interface for read tool handlers (Read, Glob, Grep, WebFetch, WebSearch).
 * <p>
 * Read handlers validate read operations before or after execution.
 * PreToolUse handlers can block operations; PostToolUse handlers can only warn.
 */
@FunctionalInterface
public interface ReadHandler
{
  /**
   * The result of a read handler check.
   *
   * @param blocked whether the operation should be blocked (PreToolUse only)
   * @param reason the reason for blocking or warning
   * @param additionalContext optional additional context to inject
   */
  record Result(boolean blocked, String reason, String additionalContext)
  {
    /**
     * Creates a new read handler result.
     *
     * @param blocked whether the operation should be blocked
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
   * Check a read operation.
   *
   * @param toolName the tool name (Read, Glob, Grep, WebFetch, WebSearch)
   * @param toolInput the tool input JSON
   * @param toolResult the tool result JSON (null for PreToolUse)
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if toolName, toolInput, or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  Result check(String toolName, JsonNode toolInput, JsonNode toolResult, String sessionId);
}
