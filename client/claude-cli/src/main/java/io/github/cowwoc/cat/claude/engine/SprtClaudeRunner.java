/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import io.github.cowwoc.cat.agent.AgentEngine;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.SprtEngineRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Claude-specific SPRT process runner.
 */
public final class SprtClaudeRunner extends SprtEngineRunner
{
  /**
   * Creates a new Claude SPRT runner.
   *
   * @param scope CLI scope
   */
  public SprtClaudeRunner(CliTool scope)
  {
    super(AgentEngine.CLAUDE, scope);
  }

  @Override
  protected int run(String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    Path launcher = scope.getPluginRoot().resolve("client").resolve("bin").resolve("claude-runner");
    List<String> command = new ArrayList<>(args.length + 1);
    command.add(launcher.toString());
    command.addAll(List.of(args));
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), UTF_8)))
    {
      while (true)
      {
        String line = reader.readLine();
        if (line == null)
          break;
        out.println(line);
      }
    }
    catch (IOException e)
    {
      process.destroyForcibly();
      throw e;
    }
    try
    {
      return process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IOException("Interrupted while waiting for claude-runner", e);
    }
  }

  @Override
  protected String[] buildTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentEngine.CLAUDE, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    return buildClaudeTrialArgs(promptFile, modelId, effort, runnerWorktree, outputJson,
      jlinkBin(runnerWorktree, engine));
  }

  @Override
  protected String[] buildGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree)
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentEngine.CLAUDE, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    return buildClaudeGraderArgs(graderPromptFile, modelId, effort, runnerWorktree,
      jlinkBin(runnerWorktree, engine));
  }

  /**
   * Builds Claude trial runner arguments.
   *
   * @param promptFile prompt file
   * @param modelId model identifier
   * @param effort reasoning effort
   * @param runnerWorktree runner worktree path
   * @param outputJson output JSON path
   * @param jlinkBin jlink binary directory
   * @return runner arguments
   */
  public static String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson, Path jlinkBin)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentEngine.CLAUDE, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    requireThat(jlinkBin, "jlinkBin").isNotNull();
    return new String[]{
      "--prompt-file", promptFile.toString(),
      "--model", modelId,
      "--effort", effort,
      "--plugin-source", Path.of(runnerWorktree, "client/plugin").toString(),
      "--jlink-bin", jlinkBin.toString(),
      "--cwd", runnerWorktree,
      "--output", outputJson
    };
  }

  /**
   * Builds Claude grader runner arguments.
   *
   * @param graderPromptFile grader prompt file
   * @param modelId model identifier
   * @param effort reasoning effort
   * @param runnerWorktree runner worktree path
   * @param jlinkBin jlink binary directory
   * @return runner arguments
   */
  public static String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree, Path jlinkBin)
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentEngine.CLAUDE, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(jlinkBin, "jlinkBin").isNotNull();
    return new String[]{
      "--prompt-file", graderPromptFile.toString(),
      "--model", modelId,
      "--effort", effort,
      "--agent", GRADER_AGENT,
      "--plugin-source", Path.of(runnerWorktree, "client/plugin").toString(),
      "--jlink-bin", jlinkBin.toString(),
      "--cwd", runnerWorktree
    };
  }
}
