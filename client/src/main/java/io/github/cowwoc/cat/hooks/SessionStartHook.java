/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.CheckRetrospectiveDue;
import io.github.cowwoc.cat.hooks.session.CheckUpdateAvailable;
import io.github.cowwoc.cat.hooks.session.CheckDataMigration;
import io.github.cowwoc.cat.hooks.session.ClearSkillMarkers;
import io.github.cowwoc.cat.hooks.session.EchoSessionId;
import io.github.cowwoc.cat.hooks.session.InjectCatRules;
import io.github.cowwoc.cat.hooks.session.InjectCriticalThinking;
import io.github.cowwoc.cat.hooks.session.InjectEnv;
import io.github.cowwoc.cat.hooks.session.InjectSessionInstructions;
import io.github.cowwoc.cat.hooks.session.InjectSkillListing;
import io.github.cowwoc.cat.hooks.session.RestoreWorktreeOnResume;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * SessionStart hook dispatcher.
 * <p>
 * Consolidates all session start handlers into a single Java dispatcher. Each handler contributes
 * additional context for Claude and/or stderr messages for the user. The combined additional context from
 * all handlers is output as a single hookSpecificOutput JSON response.
 */
public final class SessionStartHook implements HookHandler
{
  private final List<SessionStartHandler> handlers;

  /**
   * Creates a new SessionStartHook with the default handler list.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionStartHook(JvmScope scope)
  {
    this(List.of(
      new CheckDataMigration(scope),
      new CheckUpdateAvailable(scope),
      new EchoSessionId(),
      new CheckRetrospectiveDue(scope),
      new InjectSessionInstructions(),
      new InjectCatRules(scope),
      new InjectSkillListing(scope),
      new ClearSkillMarkers(scope),
      new InjectCriticalThinking(),
      new InjectEnv(scope),
      new RestoreWorktreeOnResume(scope)));
  }

  /**
   * Creates a new SessionStartHook with custom handlers (for testing).
   *
   * @param handlers the handlers to run
   * @throws NullPointerException if {@code handlers} is null
   */
  public SessionStartHook(List<SessionStartHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = handlers;
  }

  /**
   * Entry point for the session start hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    HookRunner.execute(SessionStartHook::new, args);
  }

  /**
   * Processes hook input by running all session start handlers and combining their output.
   *
   * @param input  the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    StringBuilder combinedContext = new StringBuilder(256);
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (SessionStartHandler handler : handlers)
    {
      try
      {
        SessionStartHandler.Result result = handler.handle(input);
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

    String jsonOutput;
    if (combinedContext.isEmpty())
      jsonOutput = output.empty();
    else
      jsonOutput = output.additionalContext("SessionStart", combinedContext.toString());

    return new HookResult(jsonOutput, warnings);
  }
}
