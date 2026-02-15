/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.CheckRetrospectiveDue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cowwoc.cat.hooks.session.CheckUpdateAvailable;
import io.github.cowwoc.cat.hooks.session.CheckUpgrade;
import io.github.cowwoc.cat.hooks.session.ClearSkillMarkers;
import io.github.cowwoc.cat.hooks.session.EchoSessionId;
import io.github.cowwoc.cat.hooks.session.InjectEnv;
import io.github.cowwoc.cat.hooks.session.InjectSessionInstructions;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * GetSessionStartOutput - Unified SessionStart hook dispatcher.
 * <p>
 * TRIGGER: SessionStart
 * <p>
 * Consolidates all session start handlers into a single Java dispatcher. Each handler
 * contributes additional context for Claude and/or stderr messages for the user.
 * <p>
 * The combined additional context from all handlers is output as a single
 * hookSpecificOutput JSON response.
 */
public final class GetSessionStartOutput implements HookHandler
{
  private final List<SessionStartHandler> handlers;

  /**
   * Creates a new GetSessionStartOutput with the default handler list.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if scope is null
   */
  public GetSessionStartOutput(JvmScope scope)
  {
    this(List.of(
      new CheckUpgrade(scope),
      new CheckUpdateAvailable(scope),
      new EchoSessionId(),
      new CheckRetrospectiveDue(scope),
      new InjectSessionInstructions(),
      new ClearSkillMarkers(),
      new InjectEnv(scope)));
  }

  /**
   * Creates a new GetSessionStartOutput with custom handlers (for testing).
   *
   * @param handlers the handlers to run
   * @throws NullPointerException if handlers is null
   */
  public GetSessionStartOutput(List<SessionStartHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = handlers;
  }

  /**
   * Entry point for the session start output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      tools.jackson.databind.json.JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(scope);
      HookResult result = new GetSessionStartOutput(scope).run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetSessionStartOutput.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Processes hook input by running all session start handlers and combining their output.
   *
   * @param input the hook input to process
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
        warnings.add("GetSessionStartOutput: handler error (" + error + ")");
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
