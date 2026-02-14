/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.GetCheckpointOutput;
import io.github.cowwoc.cat.hooks.skills.GetIssueCompleteOutput;
import io.github.cowwoc.cat.hooks.skills.GetNextTaskOutput;
import io.github.cowwoc.cat.hooks.skills.GetRenderDiffOutput;
import io.github.cowwoc.cat.hooks.skills.GetStatusOutput;
import io.github.cowwoc.cat.hooks.skills.ProgressBanner;
import io.github.cowwoc.cat.hooks.util.SessionAnalyzer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises all handler code paths in a single JVM invocation for AOT training.
 * <p>
 * During the build, this replaces 20 separate JVM launches with one, reducing AOT recording time from ~19s
 * to ~1s.
 */
public final class AotTraining
{
  private AotTraining()
  {
    // Utility class
  }

  /**
   * Runs all handlers with empty input to generate AOT training data.
   *
   * @param args command line arguments (unused)
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.empty(mapper);
      HookOutput output = new HookOutput(mapper);

      // Hook handlers with run(HookInput, HookOutput)
      new GetBashOutput(scope).run(input, output);
      new GetBashPostOutput().run(input, output);
      new GetReadOutput(scope).run(input, output);
      new GetReadPostOutput(scope).run(input, output);
      new GetPostOutput().run(input, output);
      new GetSkillOutput(scope).run(input, output);
      new GetAskOutput().run(input, output);
      new GetEditOutput().run(input, output);
      new GetWriteEditOutput().run(input, output);
      new GetTaskOutput().run(input, output);
      new GetSessionEndOutput().run(input, output);
      new GetSessionStartOutput(scope).run(input, output);

      // Skill handlers - construct to load class graphs.
      // Calling getOutput() would read the filesystem, which is unnecessary for training.
      new GetStatusOutput(scope);
      new GetRenderDiffOutput(scope);

      // Reference arg-based classes to force class loading without invoking main()
      // (their main() calls System.exit on missing args)
      referenceClass(EnforceStatusOutput.class);
      referenceClass(TokenCounter.class);
      referenceClass(GetCheckpointOutput.class);
      referenceClass(GetIssueCompleteOutput.class);
      referenceClass(GetNextTaskOutput.class);
      referenceClass(SessionAnalyzer.class);
      referenceClass(ProgressBanner.class);
    }
  }

  /**
   * Forces class loading without instantiation. Ensuring the class is loaded triggers static initializers
   * and class linking, which is sufficient for AOT training.
   *
   * @param clazz the class to reference
   * @return the class name (consumed to satisfy PMD's UselessPureMethodCall rule)
   */
  private static String referenceClass(Class<?> clazz)
  {
    return clazz.getName();
  }
}
