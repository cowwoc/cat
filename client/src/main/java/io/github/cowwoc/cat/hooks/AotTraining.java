/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner;
import io.github.cowwoc.cat.hooks.skills.GetCheckpointOutput;
import io.github.cowwoc.cat.hooks.skills.GetIssueCompleteOutput;
import io.github.cowwoc.cat.hooks.skills.GetNextTaskOutput;
import io.github.cowwoc.cat.hooks.skills.GetRenderDiffOutput;
import io.github.cowwoc.cat.hooks.skills.GetStatusOutput;
import io.github.cowwoc.cat.hooks.skills.ProgressBanner;
import io.github.cowwoc.cat.hooks.skills.VerifyAudit;
import io.github.cowwoc.cat.hooks.util.SessionAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
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
   * <p>
   * SYNC: Keep handler list synchronized with HANDLERS array in hooks/build-jlink.sh.
   * When adding a new handler, update both locations:
   * <ul>
   *   <li>Add launcher entry to HANDLERS array in build-jlink.sh</li>
   *   <li>Add training invocation to this method</li>
   * </ul>
   *
   * @param args command line arguments (unused)
   * @throws Exception if training fails
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void main(String[] args) throws Exception
  {
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.empty(mapper);
      HookOutput output = new HookOutput(scope);

      // Hook handlers with run(HookInput, HookOutput)
      new PreToolUseHook(scope).run(input, output);
      new GetBashPostOutput().run(input, output);
      new GetReadOutput(scope).run(input, output);
      new GetReadPostOutput(scope).run(input, output);
      new PostToolUseHook(scope.getClaudeConfigDir()).run(input, output);
      new UserPromptSubmitHook(scope).run(input, output);
      new GetAskOutput(scope).run(input, output);
      new GetEditOutput().run(input, output);
      new GetWriteEditOutput(scope).run(input, output);
      new GetTaskOutput(scope).run(input, output);
      new GetSessionEndOutput(scope).run(input, output);
      new SessionStartHook(scope).run(input, output);

      // Skill handlers - construct to load class graphs.
      // Calling getOutput() would read the filesystem, which is unnecessary for training.
      new GetStatusOutput(scope);
      new GetRenderDiffOutput(scope);

      // VerifyAudit training - create temp file for parse() and minimal JSON for report()
      Path tempDir = Files.createTempDirectory("aot-training-");
      try
      {
        Path planFile = tempDir.resolve("PLAN.md");
        Files.writeString(planFile, """
          # Plan
          ## Acceptance Criteria
          - [ ] Test criterion
          ## Files to Modify
          - test.md
          """);

        VerifyAudit audit = new VerifyAudit(scope);
        audit.parse(planFile);
        audit.report("test-issue", "{\"criteria_results\": [], \"file_results\": {\"modify\": {}, \"delete\": {}}}");
      }
      finally
      {
        Files.deleteIfExists(tempDir.resolve("PLAN.md"));
        Files.deleteIfExists(tempDir);
      }

      // Reference arg-based classes to force class loading without invoking main()
      // (their main() calls System.exit on missing args)
      referenceClass(EnforceStatusOutput.class);
      referenceClass(TokenCounter.class);
      referenceClass(GetCheckpointOutput.class);
      referenceClass(GetIssueCompleteOutput.class);
      referenceClass(GetNextTaskOutput.class);
      referenceClass(SessionAnalyzer.class);
      referenceClass(ProgressBanner.class);
      referenceClass(EmpiricalTestRunner.class);
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
