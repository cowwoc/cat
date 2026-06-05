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
import io.github.cowwoc.cat.codex.engine.CodexRunner.CodexSession;
import io.github.cowwoc.cat.codex.engine.CodexRunner.ParsedOutput;
import io.github.cowwoc.cat.codex.engine.CodexRunner.ProcessResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Implements the Codex runner command-line interface.
 */
final class CodexRunnerCli
{
  /**
   * Prevents instantiation.
   */
  private CodexRunnerCli()
  {
  }

  /**
   * Runs the Codex runner command-line interface.
   *
   * @param args the command-line arguments
   * @param scope the JVM scope
   * @param out the output stream
   * @return the exit code
   * @throws IOException if an input or output file cannot be accessed
   */
  static int run(String[] args, AgentPluginScope scope, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(scope, "scope").isNotNull();
    requireThat(out, "out").isNotNull();

    if (requestsHelp(args))
    {
      printHelp(out);
      return 0;
    }

    CliRequest request = parseCliRequest(args, scope);
    CodexRunner runner = new CodexRunner(scope);
    return executeCliRequest(runner, scope, request, out);
  }

  /**
   * Returns whether the CLI invocation requests help output.
   *
   * @param args the command-line arguments
   * @return {@code true} if help should be printed
   */
  private static boolean requestsHelp(String[] args)
  {
    return args.length == 0 || args[0].equals("--help") || args[0].equals("-h");
  }

  /**
   * Prints CLI usage information.
   *
   * @param out the output stream
   */
  private static void printHelp(PrintStream out)
  {
    out.println("""
      Usage: codex-runner --prompt-file <path> --model <name> --effort <level> [OPTIONS]

      Options:
        --prompt-file <path>  Path to a file containing the prompt to send (required)
        --model <name>        Codex model ID or alias accepted by codex exec (required)
        --effort <level>      Effort: low|medium|high|xhigh (required)
        --cwd <path>          Working directory (omitted: current CAT project path)
        --output <path>       Write parsed JSON results to file (omitted: stdout text only)
        --jsonl-output <path> Write raw Codex JSONL events to file (omitted: not persisted)
        --session-file <path> Persist session metadata for multi-turn execution""");
  }

  /**
   * Parses command-line arguments into an immutable request.
   *
   * @param args the command-line arguments
   * @param scope the CLI scope
   * @return the parsed request
   */
  private static CliRequest parseCliRequest(String[] args, AgentPluginScope scope)
  {
    CliRequestBuilder request = new CliRequestBuilder(scope);
    for (int index = 0; index < args.length; ++index)
      index = applyCliArgument(request, args, index);
    return request.toRequest();
  }

  /**
   * Applies one CLI argument/value pair to the mutable request builder.
   *
   * @param request the mutable request builder
   * @param args the command-line arguments
   * @param index the current argument index
   * @return the next index to process
   */
  private static int applyCliArgument(CliRequestBuilder request, String[] args, int index)
  {
    if (index + 1 >= args.length)
      return index;
    String value = args[index + 1];
    switch (args[index])
    {
      case "--prompt-file" -> request.promptPath = Path.of(value);
      case "--model" -> request.model = value;
      case "--effort" -> request.effort = value;
      case "--cwd" -> request.cwd = Path.of(value);
      case "--output" -> request.outputPath = Path.of(value);
      case "--jsonl-output" -> request.jsonlOutputPath = Path.of(value);
      case "--session-file" -> request.sessionFile = Path.of(value);
      default -> throw new IllegalArgumentException(
        "Unknown argument: " + args[index] + ". Valid arguments: --prompt-file, --model, " +
          "--effort, --cwd, --output, --jsonl-output, --session-file");
    }
    return index + 1;
  }

  /**
   * Executes one parsed CLI request.
   *
   * @param runner the runner instance
   * @param scope the CLI scope
   * @param request the parsed request
   * @param out the output stream
   * @return the exit code
   * @throws IOException if file IO fails
   */
  private static int executeCliRequest(CodexRunner runner, AgentPluginScope scope,
    CliRequest request, PrintStream out) throws IOException
  {
    String prompt = Files.readString(request.promptPath(), UTF_8);
    CodexSession session = runner.loadSession(request.sessionFile(), request.model(),
      request.effort(), request.cwd());
    Path lastMessageOutputPath = Files.createTempFile("codex-runner-last-message-", ".txt");
    try
    {
      List<String> command = buildCliCommand(runner, request, session, lastMessageOutputPath);
      return executeCliTurn(runner, scope, request, session, prompt, command,
        lastMessageOutputPath, out);
    }
    finally
    {
      Files.deleteIfExists(lastMessageOutputPath);
    }
  }

  /**
   * Builds the Codex CLI command for either a fresh or resumed managed session.
   *
   * @param runner the runner instance
   * @param request the parsed request
   * @param session the loaded managed session
   * @param lastMessageOutputPath the persisted final-assistant-message file
   * @return the nested Codex command
   */
  private static List<String> buildCliCommand(CodexRunner runner, CliRequest request,
    CodexSession session, Path lastMessageOutputPath)
  {
    if (session.sessionId().isBlank() || session.turns().isEmpty())
      return runner.buildCommand(request.model(), request.effort(), request.cwd(),
        lastMessageOutputPath);
    return runner.buildResumeCommand(session.sessionId(), request.model(), request.effort(),
      request.cwd(), lastMessageOutputPath);
  }

  /**
   * Executes one Codex CLI turn and persists requested artifacts.
   *
   * @param runner the runner instance
   * @param scope the CLI scope
   * @param request the parsed request
   * @param session the loaded managed session
   * @param prompt the prompt text to send to Codex
   * @param command the nested Codex command
   * @param lastMessageOutputPath the persisted final-assistant-message file
   * @param out the output stream
   * @return the CLI exit code
   * @throws IOException if file IO fails
   */
  private static int executeCliTurn(CodexRunner runner, AgentPluginScope scope, CliRequest request,
    CodexSession session, String prompt, List<String> command, Path lastMessageOutputPath,
    PrintStream out) throws IOException
  {
    boolean managedSession = request.sessionFile() != null;
    boolean invalidateSession = false;
    try
    {
      ProcessResult result = runner.executeProcess(command, prompt, request.cwd(),
        lastMessageOutputPath, request.jsonlOutputPath(), managedSession);
      if (managedSession)
        invalidateSession = true;
      if (emitCliError(out, result, managedSession))
        return CodexRunner.resolveCliExitCode(result, managedSession);
      for (String text : result.parsed().texts())
        out.println(text);
      CodexSession updatedSession = null;
      if (managedSession)
        updatedSession = session.appendTurn(prompt, result.parsed(), result.state());
      writeCliOutput(scope, request.outputPath(), updatedSession, result.parsed(), out);
      if (managedSession)
      {
        runner.saveSession(request.sessionFile(), updatedSession);
        invalidateSession = false;
      }
      return CodexRunner.resolveCliExitCode(result, managedSession);
    }
    finally
    {
      if (managedSession && invalidateSession)
        runner.closeSession(request.sessionFile(), request.cwd());
    }
  }

  /**
   * Prints a CLI-visible error when the nested process fails or reaches the wrong boundary.
   *
   * @param out the CLI output stream
   * @param result the nested process result
   * @param managedSession whether the CLI expects a resumable managed-session boundary
   * @return {@code true} if an error was printed
   */
  private static boolean emitCliError(PrintStream out, ProcessResult result, boolean managedSession)
  {
    if (!result.error().isEmpty())
    {
      out.println("ERROR: " + result.error());
      return true;
    }
    if (CodexRunner.reachedExpectedBoundary(result.state(), managedSession))
      return false;
    String error = result.state().error();
    if (error.isBlank())
      error = unexpectedBoundaryMessage(managedSession);
    out.println("ERROR: " + error);
    return true;
  }

  /**
   * Returns the CLI error message for an unexpected completion boundary.
   *
   * @param managedSession whether the CLI expects a managed-session boundary
   * @return the boundary-mismatch error message
   */
  private static String unexpectedBoundaryMessage(boolean managedSession)
  {
    if (managedSession)
      return "codex did not reach a resumable turn boundary";
    return "codex did not reach a clean completion boundary";
  }

  /**
   * Writes parsed output to the optional output file and announces the artifact path.
   *
   * @param scope the CLI scope
   * @param outputPath the optional output path
   * @param updatedSession the updated session, or {@code null} for one-shot runs
   * @param parsed the parsed output for the current turn
   * @param out the CLI output stream
   * @throws IOException if the output file cannot be written
   */
  private static void writeCliOutput(AgentPluginScope scope, Path outputPath,
    CodexSession updatedSession, ParsedOutput parsed, PrintStream out) throws IOException
  {
    if (outputPath == null)
      return;
    ParsedOutput output = parsed;
    if (updatedSession != null)
      output = updatedSession.toParsedOutput();
    Files.writeString(outputPath, scope.getJsonMapper().writeValueAsString(output), UTF_8);
    out.println("Results written to: " + outputPath);
  }

  /**
   * Mutable CLI request builder used while parsing command-line arguments.
   */
  private static final class CliRequestBuilder
  {
    private Path promptPath;
    private String model;
    private String effort;
    private Path cwd;
    private Path outputPath;
    private Path jsonlOutputPath;
    private Path sessionFile;

    /**
     * Creates a mutable request builder with the default working directory.
     *
     * @param scope the CLI scope
     */
    private CliRequestBuilder(AgentPluginScope scope)
    {
      this.cwd = scope.getProjectPath();
    }

    /**
     * Converts the mutable builder into an immutable request.
     *
     * @return the parsed CLI request
     */
    private CliRequest toRequest()
    {
      requireThat(promptPath, "promptPath").isNotNull();
      if (model == null)
        throw new IllegalArgumentException("--model argument is required");
      if (effort == null)
        throw new IllegalArgumentException("--effort argument is required");
      return new CliRequest(promptPath, model, effort, cwd, outputPath, jsonlOutputPath,
        sessionFile);
    }
  }

  /**
   * Structured Codex runner CLI request.
   *
   * @param promptPath the prompt-file path
   * @param model the requested model
   * @param effort the requested effort level
   * @param cwd the nested runner working directory
   * @param outputPath the optional parsed-output artifact path
   * @param jsonlOutputPath the optional JSONL-output artifact path
   * @param sessionFile the optional CAT-managed session file
   */
  private record CliRequest(Path promptPath, String model, String effort, Path cwd,
                            Path outputPath, Path jsonlOutputPath, Path sessionFile)
  {
  }
}
