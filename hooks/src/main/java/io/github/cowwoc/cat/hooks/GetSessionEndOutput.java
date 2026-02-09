package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.SessionUnlock;

import java.util.List;

/**
 * get-session-end-output - Unified SessionEnd hook for CAT
 * <p>
 * TRIGGER: SessionEnd
 * <p>
 * This dispatcher consolidates all SessionEnd hooks into a single Java
 * entry point for session cleanup operations.
 */
public final class GetSessionEndOutput implements HookHandler
{
  private static final List<HookHandler> HANDLERS = List.of(
    new SessionUnlock());

  /**
   * Creates a new GetSessionEndOutput instance.
   */
  public GetSessionEndOutput()
  {
  }

  /**
   * Entry point for the session end hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    HookInput input = HookInput.readFromStdin();
    HookOutput output = new HookOutput(System.out);
    new GetSessionEndOutput().run(input, output);
  }

  /**
   * Processes hook input and writes the result.
   *
   * @param input the hook input to process
   * @param output the hook output writer
   * @throws NullPointerException if input or output is null
   */
  @Override
  public void run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    for (HookHandler handler : HANDLERS)
    {
      try
      {
        handler.run(input, output);
      }
      catch (Exception e)
      {
        System.err.println("get-session-end-output: handler error: " + e.getMessage());
      }
    }
  }
}
