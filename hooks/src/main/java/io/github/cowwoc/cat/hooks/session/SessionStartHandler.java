/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;

/**
 * Interface for session start handlers.
 * <p>
 * Each handler performs a specific task during session initialization and returns context
 * to inject into Claude's conversation and/or messages for stderr.
 */
@FunctionalInterface
public interface SessionStartHandler
{
  /**
   * Handles a session start event.
   *
   * @param input the hook input
   * @return the handler result
   * @throws NullPointerException if input is null
   */
  Result handle(HookInput input);

  /**
   * The result of handling a session start event.
   *
   * @param additionalContext context to inject into Claude's conversation
   * @param stderr messages to print to stderr (visible to user in terminal)
   */
  record Result(String additionalContext, String stderr)
  {
    /**
     * Creates a new session start handler result.
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
  }
}
