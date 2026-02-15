/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;

/**
 * Interface for AskUserQuestion handlers.
 * <p>
 * Ask handlers validate user questions before they are asked.
 * PreToolUse handlers can block questions or inject additional context.
 * <p>
 * Result factory methods: AskHandler uses withContext() instead of warn() because
 * it injects additional context into the question rather than emitting stderr warnings.
 */
@FunctionalInterface
public interface AskHandler
{
  /**
   * The result of an ask handler check.
   *
   * @param blocked whether the question should be blocked (PreToolUse only)
   * @param reason the reason for blocking or warning
   * @param additionalContext optional additional context to inject
   */
  record Result(boolean blocked, String reason, String additionalContext)
  {
    /**
     * Creates a new ask handler result.
     *
     * @param blocked whether the question should be blocked
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
     * Creates a warning result with additional context.
     *
     * @param additionalContext additional context to inject
     * @return a warning result
     */
    public static Result withContext(String additionalContext)
    {
      return new Result(false, "", additionalContext);
    }
  }

  /**
   * Check an AskUserQuestion operation.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if toolInput or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  Result check(JsonNode toolInput, String sessionId);
}
