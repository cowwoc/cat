/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs SessionStart handlers and combines their context and warning output.
 */
public final class SessionStartDispatcher
{
  private SessionStartDispatcher()
  {
  }

  /**
   * Runs the supplied handlers and combines their output.
   *
   * @param handlers the handlers to run
   * @return combined SessionStart output
   * @throws NullPointerException if {@code handlers} is null
   */
  public static Result run(List<SessionStartHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    StringBuilder combinedContext = new StringBuilder(256);
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (SessionStartHandler handler : handlers)
    {
      try
      {
        SessionStartHandler.Result result = handler.handle();
        if (!result.stderr().isEmpty())
          warnings.add(result.stderr());
        if (!result.additionalContext().isEmpty())
        {
          if (!combinedContext.isEmpty())
            combinedContext.append("\n\n");
          combinedContext.append(result.additionalContext());
        }
      }
      catch (RuntimeException | AssertionError e)
      {
        String errorMessage = handler.getClass().getSimpleName() + ": " + e.getMessage();
        errors.add(errorMessage);
      }
    }

    if (!errors.isEmpty())
    {
      if (!combinedContext.isEmpty())
        combinedContext.append("\n\n");
      combinedContext.append("## SessionStart Handler Errors\n");
      for (String error : errors)
      {
        combinedContext.append("- ").append(error).append('\n');
        warnings.add("SessionStartHook: handler error (" + error + ")");
      }
    }
    return new Result(combinedContext.toString(), warnings);
  }

  /**
   * Combined SessionStart handler output.
   *
   * @param additionalContext the combined additional context
   * @param warnings warnings to show to the user
   */
  public record Result(String additionalContext, List<String> warnings)
  {
    /**
     * Creates a combined SessionStart result.
     *
     * @param additionalContext the combined additional context
     * @param warnings warnings to show to the user
     * @throws NullPointerException if {@code additionalContext} or {@code warnings} are null
     */
    public Result
    {
      requireThat(additionalContext, "additionalContext").isNotNull();
      requireThat(warnings, "warnings").isNotNull();
    }
  }
}
