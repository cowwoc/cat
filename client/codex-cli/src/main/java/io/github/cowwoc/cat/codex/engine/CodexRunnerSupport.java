/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.agent.AgentPluginScope;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * CLI support wrapper around {@link CodexRunner}.
 */
public final class CodexRunnerSupport
{
  private final CodexRunner delegate;

  /**
   * Creates a new runner.
   *
   * @param scope the scope providing the JSON mapper
   */
  public CodexRunnerSupport(AgentPluginScope scope)
  {
    this(scope, Duration.ofMinutes(10));
  }

  /**
   * Creates a new runner.
   *
   * @param scope   the scope providing the JSON mapper
   * @param timeout the process timeout
   */
  public CodexRunnerSupport(AgentPluginScope scope, Duration timeout)
  {
    this(scope, timeout, System.getenv());
  }

  /**
   * Creates a new runner.
   *
   * @param scope       the scope providing the JSON mapper
   * @param timeout     the process timeout
   * @param environment command-policy environment
   */
  public CodexRunnerSupport(AgentPluginScope scope, Duration timeout, Map<String, String> environment)
  {
    requireThat(scope, "scope").isNotNull();
    this.delegate = new CodexRunner(scope, timeout, environment);
  }

  /**
   * Builds the Codex CLI command.
   *
   * @param model                 the model to use
   * @param effort                the reasoning effort level
   * @param cwd                   the working directory for Codex
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @return the command as a list of strings
   */
  public List<String> buildCommand(String model, String effort, Path cwd, Path lastMessageOutputPath)
  {
    return delegate.buildCommand(model, effort, cwd, lastMessageOutputPath);
  }

  /**
   * Builds a process builder for the supplied command.
   *
   * @param command the command to execute
   * @param cwd     the working directory
   * @return the process builder
   */
  public ProcessBuilder buildProcessBuilder(List<String> command, Path cwd)
  {
    return delegate.buildProcessBuilder(command, cwd);
  }

  /**
   * Executes the Codex CLI process with the given prompt.
   *
   * @param command               the command to execute
   * @param prompt                the prompt to send to standard input
   * @param cwd                   the working directory
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @return the process result
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd, Path lastMessageOutputPath)
  {
    CodexRunner.ProcessResult result = delegate.executeProcess(command, prompt, cwd, lastMessageOutputPath);
    return new ProcessResult(result.parsed(), result.elapsed(), result.error());
  }

  /**
   * Executes the Codex CLI process with the given prompt.
   *
   * @param command               the command to execute
   * @param prompt                the prompt to send to standard input
   * @param cwd                   the working directory
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @param jsonlOutputPath       optional file that receives the raw JSONL stream
   * @return the process result
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd,
    Path lastMessageOutputPath, Path jsonlOutputPath)
  {
    CodexRunner.ProcessResult result = delegate.executeProcess(command, prompt, cwd, lastMessageOutputPath,
      jsonlOutputPath);
    return new ProcessResult(result.parsed(), result.elapsed(), result.error());
  }

  /**
   * Parses Codex JSONL output.
   *
   * @param output the raw JSONL output
   * @return the parsed output
   */
  public CodexRunner.ParsedOutput parseOutput(String output)
  {
    return delegate.parseOutput(output);
  }

  /**
   * Runs the command-line interface.
   *
   * @param args  the command-line arguments
   * @param scope the JVM scope
   * @param out   the output stream
   * @return the exit code
   * @throws IOException if an input or output file cannot be accessed
   */
  public static int run(String[] args, AgentPluginScope scope, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(scope, "scope").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
    {
      out.println("""
        Usage: codex-runner --prompt-file <path> --model <name> --effort <level> [OPTIONS]

        Options:
          --prompt-file <path>  Path to a file containing the prompt to send (required)
          --model <name>        Codex model ID or alias accepted by codex exec (required)
          --effort <level>      Effort: low|medium|high|xhigh (required)
          --cwd <path>          Working directory (omitted: current CAT project path)
          --output <path>       Write parsed JSON results to file (omitted: stdout text only)
          --jsonl-output <path> Write raw Codex JSONL events to file (omitted: not persisted)""");
      return 0;
    }

    Path promptPath = null;
    String model = null;
    String effort = null;
    Path cwd = scope.getProjectPath();
    Path outputPath = null;
    Path jsonlOutputPath = null;

    for (int index = 0; index < args.length; ++index)
    {
      switch (args[index])
      {
        case "--prompt-file" ->
        {
          promptPath = Path.of(args[index + 1]);
          ++index;
        }
        case "--model" ->
        {
          model = args[index + 1];
          ++index;
        }
        case "--effort" ->
        {
          effort = args[index + 1];
          ++index;
        }
        case "--cwd" ->
        {
          cwd = Path.of(args[index + 1]);
          ++index;
        }
        case "--output" ->
        {
          outputPath = Path.of(args[index + 1]);
          ++index;
        }
        case "--jsonl-output" ->
        {
          jsonlOutputPath = Path.of(args[index + 1]);
          ++index;
        }
        default -> throw new IllegalArgumentException(
          "Unknown argument: " + args[index] + ". Valid arguments: --prompt-file, --model, " +
            "--effort, --cwd, --output, --jsonl-output");
      }
    }
    if (promptPath == null)
      throw new IllegalArgumentException("--prompt-file argument is required");
    if (model == null)
      throw new IllegalArgumentException("--model argument is required");
    if (effort == null)
      throw new IllegalArgumentException("--effort argument is required");

    CodexRunnerSupport runner = new CodexRunnerSupport(scope);
    String prompt = Files.readString(promptPath, UTF_8);
    Path lastMessageOutputPath = Files.createTempFile("codex-runner-last-message-", ".txt");
    try
    {
      List<String> command = runner.buildCommand(model, effort, cwd, lastMessageOutputPath);
      ProcessResult result = runner.executeProcess(command, prompt, cwd, lastMessageOutputPath,
        jsonlOutputPath);
      if (!result.error().isEmpty())
      {
        out.println("ERROR: " + result.error());
        return 1;
      }

      for (String text : result.parsed().texts())
        out.println(text);
      if (outputPath != null)
      {
        Files.writeString(outputPath, scope.getJsonMapper().writeValueAsString(result.parsed()),
          UTF_8);
        out.println("Results written to: " + outputPath);
      }
      return 0;
    }
    finally
    {
      Files.deleteIfExists(lastMessageOutputPath);
    }
  }

  /**
   * Process execution result.
   *
   * @param parsed  parsed JSONL output
   * @param elapsed process duration
   * @param error   non-empty on failure
   */
  public record ProcessResult(CodexRunner.ParsedOutput parsed, Duration elapsed, String error)
  {
  }
}
