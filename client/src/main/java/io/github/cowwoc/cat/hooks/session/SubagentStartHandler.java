/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeHook;

/**
 * Handles subagent-start hook operations.
 * <p>
 * Each handler performs a specific task during subagent initialization and returns context
 * to inject into Claude's conversation and/or messages for stderr.
 */
@FunctionalInterface
public interface SubagentStartHandler
{
  /**
   * Handles a subagent start event.
   *
   * @param scope the hook scope providing input data
   * @return the handler result
   * @throws NullPointerException if {@code scope} is null
   */
  Result handle(ClaudeHook scope);

  /**
   * The result of handling a subagent start event.
   *
   * @param additionalContext context to inject into Claude's conversation
   * @param stderr messages to print to stderr (visible to user in terminal)
   */
  record Result(String additionalContext, String stderr)
  {
    /**
     * Creates a new subagent start handler result.
     *
     * @param additionalContext context to inject into Claude's conversation
     * @param stderr messages to print to stderr (visible to user in terminal)
     * @throws NullPointerException if any parameter is null
     */
    public Result
    {
      requireThat(additionalContext, "additionalContext").isNotNull();
      requireThat(stderr, "stderr").isNotNull();
    }

    /**
     * Creates an empty result with no context or stderr output.
     *
     * @return an empty result
     */
    public static Result empty()
    {
      return new Result("", "");
    }

    /**
     * Creates a result with additional context only.
     *
     * @param additionalContext the context to inject
     * @return a result with context
     * @throws NullPointerException if additionalContext is null
     */
    public static Result context(String additionalContext)
    {
      return new Result(additionalContext, "");
    }

    /**
     * Creates a result with stderr output only.
     *
     * @param stderr the stderr message
     * @return a result with stderr
     * @throws NullPointerException if stderr is null
     */
    public static Result stderr(String stderr)
    {
      return new Result("", stderr);
    }

    /**
     * Creates a result with both context and stderr output.
     *
     * @param additionalContext the context to inject
     * @param stderr the stderr message
     * @return a result with both
     * @throws NullPointerException if any parameter is null
     */
    public static Result both(String additionalContext, String stderr)
    {
      return new Result(additionalContext, stderr);
    }

    /**
     * Returns a result with the given context, or an empty result if the context is empty.
     *
     * @param additionalContext the context to inject
     * @return a result with context, or empty if the context is blank
     * @throws NullPointerException if additionalContext is null
     */
    public static Result ofContext(String additionalContext)
    {
      if (additionalContext.isEmpty())
        return empty();
      return context(additionalContext);
    }

    /**
     * Returns a result with the given stderr, or an empty result if the message is empty.
     *
     * @param stderr the stderr message
     * @return a result with stderr, or empty if the message is blank
     * @throws NullPointerException if stderr is null
     */
    public static Result ofStderr(String stderr)
    {
      if (stderr.isEmpty())
        return empty();
      return stderr(stderr);
    }
  }
}
