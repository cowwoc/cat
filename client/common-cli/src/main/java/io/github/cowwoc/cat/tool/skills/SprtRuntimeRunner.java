/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.agent.AgentRuntime;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.runner.CodexRunnerSupport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Runtime-specific process launcher for SPRT trial and grader agents.
 */
final class SprtRuntimeRunner
{
  private static final String GRADER_AGENT = "instruction-grader-agent";
  private static final List<String> CLAUDE_EFFORT_LEVELS = List.of("low", "medium", "high",
    "xhigh", "max");
  private static final List<String> CODEX_EFFORT_LEVELS = List.of("minimal", "low", "medium",
    "high", "xhigh");
  private final CliTool scope;
  private final AgentRuntime runtime;

  static
  {
    SharedSecrets.setSprtRuntimeRunnerAccess(new SharedSecrets.SprtRuntimeRunnerAccess()
    {
      @Override
      public String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson, Path jlinkBin)
      {
        return SprtRuntimeRunner.buildClaudeTrialArgs(promptFile, modelId, effort, runnerWorktree,
          outputJson, jlinkBin);
      }

      @Override
      public String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson)
      {
        return SprtRuntimeRunner.buildCodexTrialArgs(promptFile, modelId, effort, runnerWorktree,
          outputJson);
      }

      @Override
      public String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree, Path jlinkBin)
      {
        return SprtRuntimeRunner.buildClaudeGraderArgs(graderPromptFile, modelId, effort,
          runnerWorktree, jlinkBin);
      }

      @Override
      public String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree)
      {
        return SprtRuntimeRunner.buildCodexGraderArgs(graderPromptFile, modelId, effort,
          runnerWorktree);
      }

      @Override
      public String runtimeIdForDescriptor(Path descriptor)
      {
        return runtimeOfDescriptor(descriptor).id();
      }

      @Override
      public String[] buildTrialArgsForDescriptor(Path descriptor, Path promptFile,
        String modelId, String effort, String runnerWorktree, String outputJson)
      {
        return buildTrialArgs(runtimeOfDescriptor(descriptor), promptFile, modelId, effort,
          runnerWorktree, outputJson);
      }

      @Override
      public String[] buildGraderArgsForDescriptor(Path descriptor, Path graderPromptFile,
        String modelId, String effort, String runnerWorktree)
      {
        return buildGraderArgs(runtimeOfDescriptor(descriptor), graderPromptFile, modelId,
          effort, runnerWorktree);
      }
    });
  }

  /**
   * Creates a new runtime runner.
   *
   * @param scope the active CLI scope
   */
  SprtRuntimeRunner(CliTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.runtime = runtimeOf(scope);
  }

  /**
   * Returns the active runtime.
   *
   * @return the active runtime
   */
  AgentRuntime runtime()
  {
    return runtime;
  }

  /**
   * Validates model and effort values for the active runtime.
   *
   * @param modelId the model ID
   * @param effort  the reasoning effort
   * @throws IllegalArgumentException if the model ID or effort is not supported by the active runtime
   */
  void validateConfiguration(String modelId, String effort)
  {
    validateModelAndEffort(runtime, modelId, effort);
  }

  /**
   * Runs a test trial.
   *
   * @param promptFile the prompt file
   * @param modelId the model id
   * @param effort the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param outputJson the output JSON path
   * @param logStream the log stream
   * @return the process exit code
   * @throws IOException if the runner cannot read or write files
   */
  int runTrial(Path promptFile, String modelId, String effort, String runnerWorktree,
    String outputJson, PrintStream logStream)
    throws IOException
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(runtime, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    requireThat(logStream, "logStream").isNotNull();

    String[] args = buildTrialArgs(runtime, promptFile, modelId, effort, runnerWorktree, outputJson);
    return switch (runtime)
    {
      case CLAUDE -> ClaudeRunner.run(scope, args, logStream);
      case CODEX -> CodexRunnerSupport.run(args, scope, logStream);
    };
  }

  /**
   * Runs a grader agent.
   *
   * @param graderPromptFile the grader prompt file
   * @param modelId the model id
   * @param effort the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param out the output stream
   * @return the process exit code
   * @throws IOException if the runner cannot read or write files
   */
  int runGrader(Path graderPromptFile, String modelId, String effort, String runnerWorktree,
    PrintStream out)
    throws IOException
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(out, "out").isNotNull();

    String[] args = buildGraderArgs(runtime, graderPromptFile, modelId, effort, runnerWorktree);
    return switch (runtime)
    {
      case CLAUDE -> ClaudeRunner.run(scope, args, out);
      case CODEX -> CodexRunnerSupport.run(args, scope, out);
    };
  }

  private static String[] buildTrialArgs(AgentRuntime runtime, Path promptFile, String modelId,
    String effort, String runnerWorktree, String outputJson)
  {
    requireThat(runtime, "runtime").isNotNull();
    requireThat(promptFile, "promptFile").isNotNull();
    validateModelAndEffort(runtime, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    return switch (runtime)
    {
      case CLAUDE -> buildClaudeTrialArgs(promptFile, modelId, effort, runnerWorktree, outputJson,
        jlinkBin(runnerWorktree, runtime));
      case CODEX -> buildCodexTrialArgs(promptFile, modelId, effort, runnerWorktree, outputJson);
    };
  }

  private static String[] buildGraderArgs(AgentRuntime runtime, Path graderPromptFile,
    String modelId, String effort, String runnerWorktree)
  {
    requireThat(runtime, "runtime").isNotNull();
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    validateModelAndEffort(runtime, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    return switch (runtime)
    {
      case CLAUDE -> buildClaudeGraderArgs(graderPromptFile, modelId, effort, runnerWorktree,
        jlinkBin(runnerWorktree, runtime));
      case CODEX -> buildCodexGraderArgs(graderPromptFile, modelId, effort, runnerWorktree);
    };
  }

  /**
   * Builds the trial argument array for ClaudeRunner invocation.
   *
   * @param promptFile     the trial prompt file path
   * @param modelId        the model ID to use
   * @param effort         the reasoning effort to use
   * @param runnerWorktree the runner worktree path
   * @param outputJson     the output JSON path
   * @param jlinkBin       the jlink binary directory path
   * @return the trial arguments array
   */
  static String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson, Path jlinkBin)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentRuntime.CLAUDE, modelId, effort);
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

  private static void validateModelAndEffort(AgentRuntime runtime, String modelId, String effort)
  {
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    switch (runtime)
    {
      case CLAUDE ->
      {
        if (!CLAUDE_EFFORT_LEVELS.contains(effort))
        {
          throw new IllegalArgumentException("Invalid effort '" + effort +
            "'. Valid values: " + CLAUDE_EFFORT_LEVELS);
        }
        if (!modelId.startsWith("claude-"))
          throw new IllegalArgumentException("Invalid Claude model ID: " + modelId);
      }
      case CODEX ->
      {
        if (!CODEX_EFFORT_LEVELS.contains(effort))
        {
          throw new IllegalArgumentException("Invalid effort '" + effort +
            "'. Valid values: " + CODEX_EFFORT_LEVELS);
        }
        if (!(modelId.startsWith("gpt-") || modelId.startsWith("o")))
          throw new IllegalArgumentException("Invalid Codex model ID: " + modelId);
      }
    }
  }

  /**
   * Builds the trial argument array for CodexRunnerSupport invocation.
   *
   * @param promptFile     the trial prompt file path
   * @param modelId        the model ID to use
   * @param effort         the reasoning effort to use
   * @param runnerWorktree the runner worktree path
   * @param outputJson     the output JSON path
   * @return the trial arguments array
   */
  static String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentRuntime.CODEX, modelId, effort);
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
   * Builds the grader argument array for ClaudeRunner invocation.
   *
   * @param graderPromptFile the grader prompt file path
   * @param modelId          the model ID to use for grading
   * @param effort           the reasoning effort to use
   * @param runnerWorktree   the runner worktree path
   * @param jlinkBin         the jlink binary directory path
   * @return the grader arguments array
   */
  static String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree, Path jlinkBin)
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentRuntime.CLAUDE, modelId, effort);
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

  /**
   * Builds the grader argument array for CodexRunnerSupport invocation.
   *
   * @param graderPromptFile the grader prompt file path
   * @param modelId          the model ID to use for grading
   * @param effort           the reasoning effort to use
   * @param runnerWorktree   the runner worktree path
   * @return the grader arguments array
   */
  static String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree)
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(AgentRuntime.CODEX, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    return new String[]{
      "--prompt-file", graderPromptFile.toString(),
      "--model", modelId,
      "--effort", effort,
      "--cwd", runnerWorktree
    };
  }

  private static Path jlinkBin(String runnerWorktree, AgentRuntime runtime)
  {
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(runtime, "runtime").isNotNull();
    Path result = Path.of(runnerWorktree, "client/distribution/target/jlink", runtime.id(), "bin");
    if (!Files.isDirectory(result))
      throw new IllegalArgumentException(
        "jlink directory not found in runner worktree for runtime '" + runtime.id() + "': " +
          result);
    return result;
  }

  private static AgentRuntime runtimeOf(CliTool scope)
  {
    return runtimeOfDescriptor(scope.getPluginDescriptor());
  }

  static AgentRuntime runtimeOfDescriptor(Path descriptor)
  {
    requireThat(descriptor, "descriptor").isNotNull();
    if (descriptor.equals(AgentRuntime.CLAUDE.pluginDescriptor()))
      return AgentRuntime.CLAUDE;
    if (descriptor.equals(AgentRuntime.CODEX.pluginDescriptor()))
      return AgentRuntime.CODEX;
    throw new IllegalStateException("Unsupported CAT runtime descriptor: " + descriptor);
  }
}
