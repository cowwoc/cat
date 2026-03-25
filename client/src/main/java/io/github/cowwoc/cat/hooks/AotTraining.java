/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner;
import io.github.cowwoc.cat.hooks.skills.GetCheckpointOutput;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput;
import io.github.cowwoc.cat.hooks.skills.GetIssueCompleteOutput;
import io.github.cowwoc.cat.hooks.skills.GetNextIssueOutput;
import io.github.cowwoc.cat.hooks.skills.GetDiffOutput;
import io.github.cowwoc.cat.hooks.skills.GetOutput;
import io.github.cowwoc.cat.hooks.skills.GetStatusOutput;
import io.github.cowwoc.cat.hooks.skills.GetSubagentStatusOutput;
import io.github.cowwoc.cat.hooks.skills.ProgressBanner;
import io.github.cowwoc.cat.hooks.skills.VerifyAudit;
import io.github.cowwoc.cat.hooks.util.BatchReader;
import io.github.cowwoc.cat.hooks.util.Feedback;
import io.github.cowwoc.cat.hooks.util.HookRegistrar;
import io.github.cowwoc.cat.hooks.util.MarkdownWrapper;
import io.github.cowwoc.cat.hooks.util.SessionAnalyzer;
import io.github.cowwoc.cat.hooks.util.GetFile;
import io.github.cowwoc.cat.hooks.util.GetSkill;
import io.github.cowwoc.cat.hooks.util.StatusAlignmentValidator;
import io.github.cowwoc.cat.hooks.util.WorkPrepare;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
  @SuppressWarnings({"ResultOfMethodCallIgnored", "PMD.CloseResource"})
  public static void main(String[] args) throws Exception
  {
    // Redirect stdin so MainClaudeHook can parse the hook JSON payload during construction.
    // originalIn is System.in which must not be closed; PMD.CloseResource is suppressed intentionally.
    InputStream originalIn = System.in;
    System.setIn(new ByteArrayInputStream(
      "{\"session_id\": \"aot-training-session\", \"agent_id\": \"aot-training-agent\"}".getBytes(
        StandardCharsets.UTF_8)));
    try (AbstractClaudeHook scope = new MainClaudeHook())
    {
      System.setIn(originalIn);

      // Hook handlers all accept the unified ClaudeHook scope
      new PreToolUseHook(scope).run(scope);
      new PostBashHook().run(scope);
      new PreReadHook(scope).run(scope);
      new PostReadHook(scope).run(scope);
      new PostToolUseHook(scope).run(scope);
      new UserPromptSubmitHook(scope).run(scope);
      new PreAskHook(scope).run(scope);
      new PreWriteHook(scope).run(scope);
      new PreIssueHook(scope).run(scope);
      new SessionEndHook(scope).run(scope);
      new SessionStartHook(scope, Path.of("/tmp/aot-training-env")).run(scope);
      new SubagentStartHook(scope).run(scope);

      // Skill handlers - construct to load class graphs.
      // Calling getOutput() would read the filesystem, which is unnecessary for training.
      // GetDiffOutput and GetCleanupOutput accept JvmScope (no session required).
      // GetStatusOutput and GetOutput require AbstractClaudeTool (session-aware); use referenceClass() instead.
      new GetDiffOutput(scope);
      new GetCleanupOutput(scope);
      referenceClass(GetStatusOutput.class);
      referenceClass(GetOutput.class);

      // VerifyAudit training - create temp directory with plan.md for prepare() and minimal JSON for report()
      Path tempDir = Files.createTempDirectory("aot-training-");
      try
      {
        Path planFile = tempDir.resolve("plan.md");
        Files.writeString(planFile, """
          # Plan
          ## Post-conditions
          - [ ] Test criterion
          ## Files to Modify
          - test.md
          """);

        VerifyAudit audit = new VerifyAudit(scope);
        String prepareArgs = """
          {
            "issue_id": "aot-training",
            "issue_path": "%s",
            "worktree_path": "%s"
          }
          """.formatted(tempDir.toString(), tempDir.toString());
        audit.prepare(prepareArgs);
        audit.report("test-issue", "{\"criteria_results\": [], \"file_results\": {\"modify\": {}, \"delete\": {}}}");
      }
      finally
      {
        Files.deleteIfExists(tempDir.resolve("plan.md"));
        Files.deleteIfExists(tempDir);
      }

      // Reference arg-based classes to force class loading without invoking main()
      // (their main() calls System.exit on missing args)
      referenceClass(EnforceStatusOutput.class);
      referenceClass(TokenCounter.class);
      referenceClass(GetCheckpointOutput.class);
      referenceClass(GetIssueCompleteOutput.class);
      referenceClass(GetNextIssueOutput.class);
      referenceClass(SessionAnalyzer.class);
      referenceClass(ProgressBanner.class);
      referenceClass(EmpiricalTestRunner.class);
      referenceClass(WorkPrepare.class);
      referenceClass(MarkdownWrapper.class);
      referenceClass(BatchReader.class);
      referenceClass(GetSubagentStatusOutput.class);
      referenceClass(HookRegistrar.class);
      referenceClass(StatusAlignmentValidator.class);
      referenceClass(GetSkill.class);
      referenceClass(GetFile.class);
      referenceClass(Feedback.class);
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
