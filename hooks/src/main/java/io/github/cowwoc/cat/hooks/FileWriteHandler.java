/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;

/**
 * Interface for Write/Edit tool handlers.
 * <p>
 * Write/Edit handlers validate write and edit operations before they are executed.
 * PreToolUse handlers can block edits or inject warnings.
 * <p>
 * Result factory methods: FileWriteHandler uses warn() to emit stderr warnings
 * for non-blocking violations (similar to EditHandler).
 */
@FunctionalInterface
public interface FileWriteHandler
{
  /**
   * The result of a write/edit handler check.
   *
   * @param blocked whether the edit should be blocked (PreToolUse only)
   * @param reason the reason for blocking or warning
   * @param additionalContext additional context to inject into the response
   */
  record Result(boolean blocked, String reason, String additionalContext)
  {
    /**
     * Creates a new write/edit handler result.
     *
     * @param blocked whether the edit should be blocked
     * @param reason the reason for blocking or warning
     * @param additionalContext additional context to inject into the response
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
   * Check a write/edit operation.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if toolInput or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  Result check(JsonNode toolInput, String sessionId);
}
