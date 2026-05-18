/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import io.github.cowwoc.cat.tool.skills.GetCheckpointOutput;
import io.github.cowwoc.cat.tool.skills.GetCleanupOutput;
import io.github.cowwoc.cat.tool.skills.GetIssueCompleteOutput;
import io.github.cowwoc.cat.tool.skills.GetNextIssueOutput;
import io.github.cowwoc.cat.tool.skills.GetDiffOutput;
import io.github.cowwoc.cat.tool.skills.GetOutput;
import io.github.cowwoc.cat.claude.hook.skills.GetStatusOutput;
import io.github.cowwoc.cat.tool.skills.ProgressBanner;
import io.github.cowwoc.cat.tool.skills.VerifyAudit;
import io.github.cowwoc.cat.tool.TokenCounter;
import io.github.cowwoc.cat.tool.util.BatchReader;
import io.github.cowwoc.cat.tool.util.Feedback;
import io.github.cowwoc.cat.claude.hook.util.HookRegistrar;
import io.github.cowwoc.cat.tool.util.MarkdownWrapper;
import io.github.cowwoc.cat.tool.util.SessionAnalyzer;
import io.github.cowwoc.cat.tool.util.StatusAlignmentValidator;
import io.github.cowwoc.cat.tool.util.UpdateBranch;
import io.github.cowwoc.cat.tool.util.WorkPrepare;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Exercises Claude handler code paths in a single JVM invocation for AOT training.
 * <p>
 * During the build, this replaces many separate JVM launches with one invocation for the Claude engine.
 */
public final class AotTraining
{
  /**
   * Prevents construction.
   */
  private AotTraining()
  {
  }

  /**
   * Runs Claude AOT training from the command line.
   *
   * @param args command line arguments (unused)
   * @throws Exception if training fails
   */
  public static void main(String[] args) throws Exception
  {
    System.exit(runFromEnvironment());
  }

  /**
   * Creates the production Claude hook scope and runs Claude AOT training.
   *
   * @return 0 on success, non-zero on failure
   * @throws Exception if training fails
   */
  @SuppressWarnings({"ResultOfMethodCallIgnored", "PMD.CloseResource"})
  public static int runFromEnvironment() throws Exception
  {
    // Redirect stdin so MainClaudeHook can parse the hook JSON payload during construction.
    // originalIn is System.in which must not be closed; PMD.CloseResource is suppressed intentionally.
    InputStream originalIn = System.in;
    int exitCode;
    System.setIn(new ByteArrayInputStream(
      "{\"session_id\": \"aot-training-session\", \"agent_id\": \"aot-training-agent\"}".getBytes(
        StandardCharsets.UTF_8)));
    try
    {
      try (AbstractClaudeHook scope = new MainClaudeHook())
      {
        System.setIn(originalIn);
        exitCode = run(scope);
      }
    }
    finally
    {
      System.setIn(originalIn);
    }
    return exitCode;
  }

  /**
   * Exercises all hook handler and skill constructor code paths for AOT training.
   * <p>
   * SYNC: Keep handler list synchronized with HANDLERS array in distribution/scripts/build-jlink-images.sh.
   * When adding a new handler, update both locations:
   * <ul>
   *   <li>Add launcher entry to HANDLERS array in build-jlink-images.sh</li>
   *   <li>Add training invocation to this method</li>
   * </ul>
   *
   * @param scope the hook scope providing access to services and configuration
   * @throws NullPointerException if {@code scope} is null
   * @throws Exception if training fails
   * @return 0 on success, non-zero on failure
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static int run(AbstractClaudeHook scope) throws Exception
  {
    requireThat(scope, "scope").isNotNull();

    // Hook handlers all accept the unified ClaudeHook scope
    new PreToolUseHook(scope).run();
    new PostBashHook(scope).run();
    new PreReadHook(scope).run();
    new PostReadHook(scope).run();
    new PostToolUseHook(scope).run();
    new UserPromptSubmitHook(scope).run();
    new PreAskHook(scope).run();
    new PreWriteHook(scope).run();
    new PreIssueHook(scope).run();
    new SessionEndHook(scope).run();
    new SessionStartHook(scope, Path.of("/tmp/aot-training-env")).run();
    new SubagentStartHook(scope).run();

    // Skill handlers - construct to load class graphs.
    // Calling getOutput() would read the filesystem, which is unnecessary for training.
    // All SkillOutput constructors require ClaudeTool; use referenceClass() since this hook scope
    // is AbstractClaudeHook which does not implement ClaudeTool.
    referenceClass(GetDiffOutput.class);
    referenceClass(GetCleanupOutput.class);
    referenceClass(GetStatusOutput.class);
    referenceClass(GetOutput.class);

    // Reference arg-based classes to force class loading without invoking main()
    // (their main() calls System.exit on missing args, or require ClaudeTool scope)
    referenceClass(VerifyAudit.class);
    referenceClass(EnforceStatusOutput.class);
    referenceClass(TokenCounter.class);
    referenceClass(GetCheckpointOutput.class);
    referenceClass(GetIssueCompleteOutput.class);
    referenceClass(GetNextIssueOutput.class);
    referenceClass(SessionAnalyzer.class);
    referenceClass(ProgressBanner.class);
    referenceClass(WorkPrepare.class);
    referenceClass(MarkdownWrapper.class);
    referenceClass(BatchReader.class);
    referenceClass(HookRegistrar.class);
    referenceClass(StatusAlignmentValidator.class);
    referenceClass(Feedback.class);
    referenceClass(UpdateBranch.class);
    return 0;
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
