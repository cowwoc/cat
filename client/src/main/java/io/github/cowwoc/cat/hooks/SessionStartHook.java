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
import io.github.cowwoc.cat.hooks.session.InjectCriticalThinking;
import io.github.cowwoc.cat.hooks.session.InjectEnv;
import io.github.cowwoc.cat.hooks.session.InjectSessionInstructions;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified SessionStart and PreCompact hook dispatcher.
 * <p>
 * TRIGGER: SessionStart (normal mode) or PreCompact (preCompact mode)
 * <p>
 * In normal mode, consolidates all session start handlers into a single Java dispatcher. Each handler
 * contributes additional context for Claude and/or stderr messages for the user. The combined additional
 * context from all handlers is output as a single hookSpecificOutput JSON response.
 * <p>
 * In preCompact mode, runs only the ClearSkillMarkers and InjectCriticalThinking handlers for their side
 * effects, and always returns {@code "{}"} (discarding any additionalContext output).
 */
public final class SessionStartHook implements HookHandler
{
  private final List<SessionStartHandler> handlers;
  private final boolean preCompact;

  /**
   * Creates a new SessionStartHook in normal mode with the default handler list.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionStartHook(JvmScope scope)
  {
    this(List.of(
      new CheckUpgrade(scope),
      new CheckUpdateAvailable(scope),
      new EchoSessionId(),
      new CheckRetrospectiveDue(scope),
      new InjectSessionInstructions(),
      new ClearSkillMarkers(),
      new InjectCriticalThinking(),
      new InjectEnv(scope)), false);
  }

  /**
   * Creates a new SessionStartHook in normal mode with custom handlers (for testing).
   *
   * @param handlers the handlers to run
   * @throws NullPointerException if {@code handlers} is null
   */
  public SessionStartHook(List<SessionStartHandler> handlers)
  {
    this(handlers, false);
  }

  /**
   * Creates a new SessionStartHook with custom handlers and explicit mode (for testing preCompact mode).
   *
   * @param handlers  the handlers to run
   * @param preCompact {@code true} to discard additionalContext and always return {@code "{}"};
   *                  {@code false} for normal mode
   * @throws NullPointerException if {@code handlers} is null
   */
  public SessionStartHook(List<SessionStartHandler> handlers, boolean preCompact)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = handlers;
    this.preCompact = preCompact;
  }

  /**
   * Creates a new SessionStartHook in preCompact mode with the default handler list.
   *
   * @return a SessionStartHook configured for PreCompact hook invocation
   */
  public static SessionStartHook preCompact()
  {
    return new SessionStartHook(List.of(new ClearSkillMarkers(), new InjectCriticalThinking()), true);
  }

  /**
   * Entry point for the session start and pre-compact hooks.
   * <p>
   * Pass {@code --precompact} as the first argument to run in preCompact mode.
   *
   * @param args command line arguments; {@code --precompact} enables preCompact mode
   */
  public static void main(String[] args)
  {
    boolean preCompactMode = args.length > 0 && args[0].equals("--precompact");
    try (JvmScope scope = new MainJvmScope())
    {
      tools.jackson.databind.json.JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(scope);
      SessionStartHook hook;
      if (preCompactMode)
        hook = preCompact();
      else
        hook = new SessionStartHook(scope);
      HookResult result = hook.run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(SessionStartHook.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Processes hook input by running all session start handlers and combining their output.
   * <p>
   * In preCompact mode, handlers are still run for side effects, but the method always returns
   * {@code "{}"} as the output regardless of handler context.
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

    if (preCompact)
      return new HookResult("{}", warnings);

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
