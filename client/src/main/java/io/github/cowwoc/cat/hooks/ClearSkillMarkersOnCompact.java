/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.ClearSkillMarkers;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * PreCompact hook that clears the session skill marker file.
 * <p>
 * TRIGGER: PreCompact
 * <p>
 * Clears the session skill marker file so that skills reload with fresh content
 * after context compaction.
 *
 * @see io.github.cowwoc.cat.hooks.util.SkillLoader
 * @see io.github.cowwoc.cat.hooks.session.ClearSkillMarkers
 */
public final class ClearSkillMarkersOnCompact implements HookHandler
{
  private final List<SessionStartHandler> handlers;

  /**
   * Creates a new ClearSkillMarkersOnCompact with the default handler list.
   */
  public ClearSkillMarkersOnCompact()
  {
    this(List.of(new ClearSkillMarkers()));
  }

  /**
   * Creates a new ClearSkillMarkersOnCompact with custom handlers (for testing).
   *
   * @param handlers the handlers to run
   * @throws NullPointerException if {@code handlers} is null
   */
  public ClearSkillMarkersOnCompact(List<SessionStartHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = handlers;
  }

  /**
   * Entry point for the pre-compact hook.
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
      HookResult result = new ClearSkillMarkersOnCompact().run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(ClearSkillMarkersOnCompact.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Processes hook input by running all pre-compact handlers and combining their output.
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

    List<String> warnings = new ArrayList<>();

    for (SessionStartHandler handler : handlers)
    {
      try
      {
        SessionStartHandler.Result result = handler.handle(input);
        if (!result.stderr().isEmpty())
          warnings.add(result.stderr());
      }
      catch (RuntimeException | AssertionError e)
      {
        String errorMsg = e.getMessage();
        if (errorMsg == null)
          errorMsg = e.getClass().getName();
        warnings.add(errorMsg);
      }
    }

    return new HookResult("{}", warnings);
  }
}
