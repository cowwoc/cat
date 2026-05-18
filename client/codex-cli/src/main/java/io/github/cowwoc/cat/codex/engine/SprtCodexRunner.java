/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.engine;

import io.github.cowwoc.cat.agent.AgentEngine;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.runner.CodexRunnerSupport;
import io.github.cowwoc.cat.tool.skills.SprtEngineRunner;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Codex-specific SPRT process runner.
 */
public final class SprtCodexRunner extends SprtEngineRunner
{
  /**
   * Creates a new Codex SPRT runner.
   *
   * @param scope CLI scope
   */
  public SprtCodexRunner(CliTool scope)
  {
    super(AgentEngine.CODEX, scope);
  }

  @Override
  protected int run(String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    return CodexRunnerSupport.run(args, scope, out);
  }

  @Override
  protected String[] buildTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson)
  {
    return buildCodexTrialArgs(promptFile, modelId, effort, runnerWorktree, outputJson);
  }

  @Override
  protected String[] buildGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree)
  {
    return buildCodexGraderArgs(graderPromptFile, modelId, effort, runnerWorktree);
  }

  /**
   * Builds Codex trial runner arguments.
   *
   * @param promptFile prompt file
   * @param modelId model identifier
   * @param effort reasoning effort
   * @param runnerWorktree runner worktree path
   * @param outputJson output JSON path
   * @return runner arguments
   */
  public static String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentEngine.CODEX, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    return new String[]{
      "--prompt-file", promptFile.toString(),
      "--model", modelId,
      "--effort", effort,
      "--cwd", runnerWorktree,
      "--output", outputJson
    };
  }

  /**
   * Builds Codex grader runner arguments.
   *
   * @param graderPromptFile grader prompt file
   * @param modelId model identifier
   * @param effort reasoning effort
   * @param runnerWorktree runner worktree path
   * @return runner arguments
   */
  public static String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree)
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentEngine.CODEX, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    return new String[]{
      "--prompt-file", graderPromptFile.toString(),
      "--model", modelId,
      "--effort", effort,
      "--cwd", runnerWorktree
    };
  }
}
