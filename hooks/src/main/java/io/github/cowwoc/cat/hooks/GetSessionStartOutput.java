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
   */
  public GetSessionStartOutput()
  {
    this(List.of(
      new CheckUpgrade(),
      new CheckUpdateAvailable(),
      new EchoSessionId(),
      new CheckRetrospectiveDue(),
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
    HookInput input = HookInput.readFromStdin();
    HookOutput output = new HookOutput(System.out);
    new GetSessionStartOutput().run(input, output);
  }

  /**
   * Processes hook input by running all session start handlers and combining their output.
   * Delegates to {@link #run(HookInput, HookOutput, PrintStream)} with {@code System.err}.
   *
   * @param input the hook input to process
   * @param output the hook output writer
   * @throws NullPointerException if input or output is null
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
   */
  public void run(HookInput input, HookOutput output, PrintStream stderr)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();
    requireThat(stderr, "stderr").isNotNull();

    StringBuilder combinedContext = new StringBuilder();

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
        stderr.println("GetSessionStartOutput: handler error (" +
          handler.getClass().getSimpleName() + "): " + e.getMessage());
      }
    }

    if (combinedContext.isEmpty())
    {
      output.empty();
      return;
    }
    output.additionalContext("SessionStart", combinedContext.toString());
  }
}
