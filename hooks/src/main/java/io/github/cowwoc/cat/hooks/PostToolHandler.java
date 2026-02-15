/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;

/**
 * Interface for general PostToolUse handlers.
 * <p>
 * These handlers process tool results for all tools (not just Bash or Read).
 * They can warn about results or inject additional context.
 */
@FunctionalInterface
public interface PostToolHandler
{
  /**
   * The result of a post-tool handler check.
   *
   * @param warning optional warning message
   * @param additionalContext optional additional context to inject
   */
  record Result(String warning, String additionalContext)
  {
    /**
     * Creates a new post-tool handler result.
     *
     * @param warning optional warning message
     * @param additionalContext optional additional context to inject
     */
    public Result
    {
      // warning and additionalContext use "" not null per Java conventions
    }

    /**
     * Creates an allow result (no warning, no context).
     *
     * @return an allow result
     */
    public static Result allow()
    {
      return new Result("", "");
    }

    /**
     * Creates a warning result.
     *
     * @param warning the warning message
     * @return a warning result
     */
    public static Result warn(String warning)
    {
      return new Result(warning, "");
    }

    /**
     * Creates an additional context result.
     *
     * @param additionalContext the context to inject
     * @return a context result
     */
    public static Result context(String additionalContext)
    {
      return new Result("", additionalContext);
    }

    /**
     * Creates a result with both warning and context.
     *
     * @param warning the warning message
     * @param additionalContext the context to inject
     * @return a combined result
     */
    public static Result warnAndContext(String warning, String additionalContext)
    {
      return new Result(warning, additionalContext);
    }
  }

  /**
   * Check a tool result.
   *
   * @param toolName the tool name
   * @param toolResult the tool result JSON
   * @param sessionId the session ID
   * @param hookData the full hook data JSON
   * @return the check result
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData);
}
