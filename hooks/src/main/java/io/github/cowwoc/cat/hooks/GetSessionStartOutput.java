package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.CheckRetrospectiveDue;
import io.github.cowwoc.cat.hooks.session.CheckUpdateAvailable;
import io.github.cowwoc.cat.hooks.session.CheckUpgrade;
import io.github.cowwoc.cat.hooks.session.ClearSkillMarkers;
import io.github.cowwoc.cat.hooks.session.EchoSessionId;
import io.github.cowwoc.cat.hooks.session.InjectEnv;
import io.github.cowwoc.cat.hooks.session.InjectSessionInstructions;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;

import java.io.PrintStream;
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
      new InjectEnv()));
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
      HookOutput output = new HookOutput(mapper, System.out);
      new GetSessionStartOutput(scope).run(input, output);
    }
    catch (RuntimeException | AssertionError e)
    {
      System.err.println("GetSessionStartOutput: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Processes hook input by running all session start handlers and combining their output.
   * Delegates to {@link #run(HookInput, HookOutput, PrintStream)} with {@code System.err}.
   *
   * @param input the hook input to process
   * @param output the hook output writer
   * @throws NullPointerException if input or output is null
   * @throws IllegalStateException if any handler fails
   */
  @Override
  public void run(HookInput input, HookOutput output)
  {
    run(input, output, System.err);
  }

  /**
   * Processes hook input by running all session start handlers and combining their output.
   * Handler stderr messages are written to the provided stream.
   *
   * @param input the hook input to process
   * @param output the hook output writer
   * @param stderr the stream for handler stderr messages
   * @throws NullPointerException if any parameter is null
   * @throws IllegalStateException if any handler fails
   */
  public void run(HookInput input, HookOutput output, PrintStream stderr)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();
    requireThat(stderr, "stderr").isNotNull();

    StringBuilder combinedContext = new StringBuilder(256);
    List<String> errors = new ArrayList<>();

    for (SessionStartHandler handler : handlers)
    {
      try
      {
        SessionStartHandler.Result result = handler.handle(input);
        if (!result.stderr().isEmpty())
          stderr.println(result.stderr());
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
        stderr.println("GetSessionStartOutput: handler error (" + error + ")");
      }
    }

    if (combinedContext.isEmpty())
      output.empty();
    else
      output.additionalContext("SessionStart", combinedContext.toString());

    if (!errors.isEmpty())
      throw new IllegalStateException("SessionStart handlers failed: " + String.join(", ", errors));
  }
}
