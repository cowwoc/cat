package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;

/**
 * Interface for Task tool handlers.
 * <p>
 * Task handlers validate task operations (subagent spawning) before they are executed.
 * PreToolUse handlers can block task operations.
 * <p>
 * Result factory methods: TaskHandler uses warn() to emit stderr warnings
 * for non-blocking violations (unlike AskHandler which uses withContext()).
 */
@FunctionalInterface
public interface TaskHandler
{
  /**
   * The result of a task handler check.
   *
   * @param blocked whether the task should be blocked (PreToolUse only)
   * @param reason the reason for blocking or warning
   * @param additionalContext optional additional context to inject
   */
  record Result(boolean blocked, String reason, String additionalContext)
  {
    /**
     * Creates a new task handler result.
     *
     * @param blocked whether the task should be blocked
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
   * Check a task operation.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if toolInput or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  Result check(JsonNode toolInput, String sessionId);
}
