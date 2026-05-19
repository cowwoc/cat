/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.agent.AgentEngine;
import io.github.cowwoc.cat.tool.CliTool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Engine-dispatched process launcher for SPRT trial and grader agents.
 */
public final class EngineSprtRunner
{
  /**
   * The grader agent identifier.
   */
  private static final String GRADER_AGENT = "instruction-grader-agent";
  /**
   * Valid Claude effort levels.
   */
  private static final List<String> CLAUDE_EFFORT_LEVELS = List.of("low", "medium", "high",
    "xhigh", "max");
  /**
   * Valid Codex effort levels.
   */
  private static final List<String> CODEX_EFFORT_LEVELS = List.of("minimal", "low", "medium",
    "high", "xhigh");
  /**
   * CLI scope and services.
   */
  private final CliTool scope;
  /**
   * Engine associated with this runner.
   */
  private final AgentEngine engine;

  static
  {
    SharedSecrets.setEngineSprtRunnerAccess(new SharedSecrets.EngineSprtRunnerAccess()
    {
      @Override
      public String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson, Path jlinkBin)
      {
        return buildClaudeTrialArgsInternal(promptFile, modelId, effort, runnerWorktree,
          outputJson, jlinkBin);
      }

      @Override
      public String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson)
      {
        return buildCodexTrialArgsInternal(promptFile, modelId, effort, runnerWorktree, outputJson);
      }

      @Override
      public String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree, Path jlinkBin)
      {
        return buildClaudeGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree,
          jlinkBin);
      }

      @Override
      public String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree)
      {
        return buildCodexGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree);
      }

      @Override
      public String engineIdForDescriptor(Path descriptor)
      {
        return engineOfDescriptor(descriptor).id();
      }

      @Override
      public String[] buildTrialArgsForDescriptor(Path descriptor, Path promptFile,
        String modelId, String effort, String runnerWorktree, String outputJson)
      {
        AgentEngine engine = engineOfDescriptor(descriptor);
        return buildTrialArgs(engine, promptFile, modelId, effort, runnerWorktree, outputJson);
      }

      @Override
      public String[] buildGraderArgsForDescriptor(Path descriptor, Path graderPromptFile,
        String modelId, String effort, String runnerWorktree)
      {
        AgentEngine engine = engineOfDescriptor(descriptor);
        return buildGraderArgs(engine, graderPromptFile, modelId, effort, runnerWorktree);
      }
    });
  }

  private EngineSprtRunner(AgentEngine engine, CliTool scope)
  {
    requireThat(engine, "engine").isNotNull();
    requireThat(scope, "scope").isNotNull();
    this.engine = engine;
    this.scope = scope;
  }

  static EngineSprtRunner create(CliTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    return new EngineSprtRunner(engineOf(scope), scope);
  }

  AgentEngine engine()
  {
    return engine;
  }

  void validateConfiguration(String modelId, String effort)
  {
    validateModelAndEffort(engine, modelId, effort);
  }

  int runTrial(Path promptFile, String modelId, String effort, String runnerWorktree,
    String outputJson, PrintStream logStream)
    throws IOException
  {
    requireThat(logStream, "logStream").isNotNull();
    String[] args = buildTrialArgs(engine, promptFile, modelId, effort, runnerWorktree, outputJson);
    return run(args, logStream);
  }

  int runGrader(Path graderPromptFile, String modelId, String effort, String runnerWorktree,
    PrintStream out)
    throws IOException
  {
    requireThat(out, "out").isNotNull();
    String[] args = buildGraderArgs(engine, graderPromptFile, modelId, effort, runnerWorktree);
    return run(args, out);
  }

  private int run(String[] args, PrintStream out) throws IOException
  {
    Path launcher = switch (engine)
    {
      case CLAUDE -> scope.getPluginRoot().resolve("client").resolve("bin").resolve("claude-runner");
      case CODEX -> scope.getPluginRoot().resolve("client").resolve("bin").resolve("codex-runner");
    };
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
      throw new IOException("Interrupted while waiting for " + launcher.getFileName(), e);
    }
  }

  private static String[] buildTrialArgs(AgentEngine engine, Path promptFile, String modelId,
    String effort, String runnerWorktree, String outputJson)
  {
    return switch (engine)
    {
      case CLAUDE -> buildClaudeTrialArgsInternal(promptFile, modelId, effort, runnerWorktree,
        outputJson, jlinkBin(runnerWorktree, engine));
      case CODEX -> buildCodexTrialArgsInternal(promptFile, modelId, effort, runnerWorktree,
        outputJson);
    };
  }

  private static String[] buildGraderArgs(AgentEngine engine, Path graderPromptFile, String modelId,
    String effort, String runnerWorktree)
  {
    return switch (engine)
    {
      case CLAUDE -> buildClaudeGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree,
        jlinkBin(runnerWorktree, engine));
      case CODEX -> buildCodexGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree);
    };
  }

  private static String[] buildClaudeTrialArgsInternal(Path promptFile, String modelId, String effort,
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

  private static String[] buildCodexTrialArgsInternal(Path promptFile, String modelId, String effort,
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

  private static String[] buildClaudeGraderArgsInternal(Path graderPromptFile, String modelId,
    String effort, String runnerWorktree, Path jlinkBin)
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

  private static String[] buildCodexGraderArgsInternal(Path graderPromptFile, String modelId,
    String effort, String runnerWorktree)
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

  private static void validateModelAndEffort(AgentEngine engine, String modelId, String effort)
  {
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    switch (engine)
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
   * Resolves the jlink binary directory for an engine in a runner worktree.
   *
   * @param runnerWorktree runner worktree path
   * @param engine the engine
   * @return the jlink bin directory
   */
  public static Path jlinkBin(String runnerWorktree, AgentEngine engine)
  {
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(engine, "engine").isNotNull();
    Path result = Path.of(runnerWorktree, "client/distribution/target/jlink", engine.id(), "bin");
    if (!Files.isDirectory(result))
      throw new IllegalArgumentException(
        "jlink directory not found in runner worktree for engine '" + engine.id() + "': " +
          result);
    return result;
  }

  static AgentEngine engineOf(CliTool scope)
  {
    return engineOfDescriptor(scope.getPluginDescriptor());
  }

  static AgentEngine engineOfDescriptor(Path descriptor)
  {
    requireThat(descriptor, "descriptor").isNotNull();
    if (descriptor.equals(AgentEngine.CLAUDE.pluginDescriptor()))
      return AgentEngine.CLAUDE;
    if (descriptor.equals(AgentEngine.CODEX.pluginDescriptor()))
      return AgentEngine.CODEX;
    throw new IllegalStateException("Unsupported CAT engine descriptor: " + descriptor);
  }
}
