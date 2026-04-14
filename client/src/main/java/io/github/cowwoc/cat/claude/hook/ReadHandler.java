/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

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

    /**
     * Creates a context-injection result (allows and injects additional context).
     *
     * @param additionalContext the additional context to inject
     * @return a context result
     * @throws NullPointerException if {@code additionalContext} is null
     */
    public static Result context(String additionalContext)
    {
      return new Result(false, "", additionalContext);
    }
  }

  /**
   * Check a read operation.
   *
   * @param toolName the tool name (Read, Glob, Grep, WebFetch, WebSearch)
   * @param toolInput the tool input JSON
   * @param toolResult the tool result JSON (null for PreToolUse)
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the check result
   * @throws NullPointerException if {@code toolName}, {@code toolInput}, or {@code catAgentId} are null
   * @throws IllegalArgumentException if {@code catAgentId} is blank
   */
  Result check(String toolName, JsonNode toolInput, JsonNode toolResult, String catAgentId);
}
