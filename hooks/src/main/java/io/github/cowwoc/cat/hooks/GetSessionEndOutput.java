/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.SessionUnlock;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(mapper);
      HookResult result = new GetSessionEndOutput().run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetSessionEndOutput.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Processes hook input and returns the result with any warnings.
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

    List<String> allWarnings = new ArrayList<>();

    for (HookHandler handler : HANDLERS)
    {
      try
      {
        HookResult result = handler.run(input, output);
        allWarnings.addAll(result.warnings());
      }
      catch (Exception e)
      {
        allWarnings.add("get-session-end-output: handler error: " + e.getMessage());
      }
    }

    return new HookResult(output.empty(), allWarnings);
  }
}
