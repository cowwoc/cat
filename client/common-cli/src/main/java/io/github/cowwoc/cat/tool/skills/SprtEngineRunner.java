/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.agent.AgentEngine;
import io.github.cowwoc.cat.tool.CliTool;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Engine-dispatched process launcher for SPRT trial and grader agents.
 */
public abstract class SprtEngineRunner
{
  /**
   * The grader agent identifier.
   */
  protected static final String GRADER_AGENT = "instruction-grader-agent";
  /**
   * Valid Claude effort levels.
   */
  protected static final List<String> CLAUDE_EFFORT_LEVELS = List.of("low", "medium", "high",
    "xhigh", "max");
  /**
   * Valid Codex effort levels.
   */
  protected static final List<String> CODEX_EFFORT_LEVELS = List.of("minimal", "low", "medium",
    "high", "xhigh");
  /**
   * CLI scope and services.
   */
  protected final CliTool scope;
  /**
   * Engine associated with this runner.
   */
  protected final AgentEngine engine;

  static
  {
    SharedSecrets.setSprtEngineRunnerAccess(new SharedSecrets.SprtEngineRunnerAccess()
    {
      @Override
      public String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson, Path jlinkBin)
      {
        return invokeStringArrayStatic("io.github.cowwoc.cat.claude.engine.SprtClaudeRunner",
          "buildClaudeTrialArgs",
          new Class<?>[]{Path.class, String.class, String.class, String.class, String.class, Path.class},
          promptFile, modelId, effort, runnerWorktree, outputJson, jlinkBin);
      }

      @Override
      public String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson)
      {
        return invokeStringArrayStatic("io.github.cowwoc.cat.codex.engine.SprtCodexRunner",
          "buildCodexTrialArgs",
          new Class<?>[]{Path.class, String.class, String.class, String.class, String.class},
          promptFile, modelId, effort, runnerWorktree, outputJson);
      }

      @Override
      public String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree, Path jlinkBin)
      {
        return invokeStringArrayStatic("io.github.cowwoc.cat.claude.engine.SprtClaudeRunner",
          "buildClaudeGraderArgs",
          new Class<?>[]{Path.class, String.class, String.class, String.class, Path.class},
          graderPromptFile, modelId, effort, runnerWorktree, jlinkBin);
      }

      @Override
      public String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree)
      {
        return invokeStringArrayStatic("io.github.cowwoc.cat.codex.engine.SprtCodexRunner",
          "buildCodexGraderArgs",
          new Class<?>[]{Path.class, String.class, String.class, String.class},
          graderPromptFile, modelId, effort, runnerWorktree);
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
        return createForEngine(engineOfDescriptor(descriptor), null).buildTrialArgs(promptFile,
          modelId, effort, runnerWorktree, outputJson);
      }

      @Override
      public String[] buildGraderArgsForDescriptor(Path descriptor, Path graderPromptFile,
        String modelId, String effort, String runnerWorktree)
      {
        return createForEngine(engineOfDescriptor(descriptor), null).buildGraderArgs(
          graderPromptFile, modelId, effort, runnerWorktree);
      }
    });
  }

  protected SprtEngineRunner(AgentEngine engine, CliTool scope)
  {
    requireThat(engine, "engine").isNotNull();
    this.engine = engine;
    this.scope = scope;
  }

  static SprtEngineRunner create(CliTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    return createForEngine(engineOf(scope), scope);
  }

  private static SprtEngineRunner createForEngine(AgentEngine engine, CliTool scope)
  {
    return switch (engine)
    {
      case CLAUDE -> instantiateRunner("io.github.cowwoc.cat.claude.engine.SprtClaudeRunner", scope);
      case CODEX -> instantiateRunner("io.github.cowwoc.cat.codex.engine.SprtCodexRunner", scope);
    };
  }

  private static SprtEngineRunner instantiateRunner(String className, CliTool scope)
  {
    try
    {
      Class<?> clazz = Class.forName(className);
      return (SprtEngineRunner) clazz.getDeclaredConstructor(CliTool.class).newInstance(scope);
    }
    catch (ReflectiveOperationException e)
    {
      throw new IllegalStateException("Unable to instantiate " + className, e);
    }
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
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(engine, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    requireThat(logStream, "logStream").isNotNull();
    return run(buildTrialArgs(promptFile, modelId, effort, runnerWorktree, outputJson), logStream);
  }

  int runGrader(Path graderPromptFile, String modelId, String effort, String runnerWorktree,
    PrintStream out)
    throws IOException
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    validateModelAndEffort(engine, modelId, effort);
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(out, "out").isNotNull();
    return run(buildGraderArgs(graderPromptFile, modelId, effort, runnerWorktree), out);
  }

  protected abstract int run(String[] args, PrintStream out) throws IOException;

  protected abstract String[] buildTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson);

  protected abstract String[] buildGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree);

  protected static void validateModelAndEffort(AgentEngine engine, String modelId, String effort)
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

  protected static Path jlinkBin(String runnerWorktree, AgentEngine engine)
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

  private static String[] invokeStringArrayStatic(String className, String methodName,
    Class<?>[] parameterTypes, Object... args)
  {
    try
    {
      Class<?> clazz = Class.forName(className);
      return (String[]) clazz.getMethod(methodName, parameterTypes).invoke(null, args);
    }
    catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e)
    {
      throw new IllegalStateException("Unable to invoke " + className + "." + methodName, e);
    }
    catch (InvocationTargetException e)
    {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException engineException)
        throw engineException;
      throw new IllegalStateException("Invocation failed: " + className + "." + methodName, cause);
    }
  }
}
