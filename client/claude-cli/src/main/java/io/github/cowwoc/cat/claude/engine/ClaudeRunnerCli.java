/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.ModelIdResolver;
import io.github.cowwoc.cat.claude.engine.ClaudeRunner.ClaudeSession;
import io.github.cowwoc.cat.claude.engine.ClaudeRunner.ParsedOutput;
import io.github.cowwoc.cat.claude.engine.ClaudeRunner.ProcessResult;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Implements the Claude runner command-line interface on top of {@link ClaudeRunner}.
 */
final class ClaudeRunnerCli
{
  private ClaudeRunnerCli()
  {
  }

  /**
   * Executes the Claude runner CLI with a caller-provided output stream.
   *
   * @param scope the scope providing access to shared services
   * @param args command line arguments
   * @param out the output stream to write to
   * @return the CLI exit code
   * @throws IOException if an I/O error occurs
   */
  static int run(CliTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length >= 1 && args[0].equals("resolve-model"))
    {
      String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
      out.println(resolveModel(rest));
      return 0;
    }

    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
    {
      out.println("""
        Usage: claude-runner --prompt-file <path> [OPTIONS]

        Options:
          --prompt-file <path>            Path to a file containing the prompt to send (required)
          --model <name>                  Model: haiku|sonnet|opus (required)
          --effort <level>                Effort: low|medium|high|xhigh|max (required)
          --cwd <path>                    Working directory (omitted: current directory)
          --plugin-source <path>          Plugin source directory; requires --jlink-bin to isolate
          --jlink-bin <path>              jlink binary directory; requires --plugin-source to isolate
          --plugin-version <ver>          Cache version when isolating (omitted: 2.1)
          --agent <name>                  Run claude --agent <name> (omitted: main agent)
          --append-system-prompt <text>   Extra system prompt (omitted: none)
          --output <path>                 Write JSON results to file (omitted: stdout text only)
          --session-file <path>           Persist CAT-managed multi-turn session state

        Subcommands:
          resolve-model <short_name>      Resolve a short model name to a fully-qualified model ID""");
      return 0;
    }
    CliRequest request = parseCliRequest(args);
    String promptText = readPromptText(request.promptFile());
    return executeCliRequest(scope, request, promptText, out);
  }

  /**
   * Parses Claude runner CLI arguments into a structured request.
   *
   * @param args command line arguments
   * @return the parsed CLI request
   */
  private static CliRequest parseCliRequest(String[] args)
  {
    CliRequestBuilder request = new CliRequestBuilder();
    for (int i = 0; i < args.length; ++i)
    {
      if (i + 1 >= args.length)
        continue;
      i = applyCliArgument(request, args, i);
    }
    return request.toRequest();
  }

  /**
   * Applies one CLI argument and returns the next loop index to inspect.
   *
   * @param request the mutable request builder
   * @param args the full CLI argument vector
   * @param index the current argument index
   * @return the next loop index after consuming any associated value
   */
  private static int applyCliArgument(CliRequestBuilder request, String[] args, int index)
  {
    String value = args[index + 1];
    switch (args[index])
    {
      case "--prompt-file" -> request.prompt = value;
      case "--model" -> request.model = value;
      case "--effort" -> request.effort = value;
      case "--cwd" -> request.cwd = Path.of(value);
      case "--plugin-source" -> request.pluginSource = Path.of(value);
      case "--jlink-bin" -> request.jlinkBin = Path.of(value);
      case "--plugin-version" -> request.pluginVersion = value;
      case "--agent" -> request.agent = value;
      case "--append-system-prompt" -> request.appendSystemPrompt = value;
      case "--output" -> request.outputPath = Path.of(value);
      case "--session-file" -> request.sessionFile = Path.of(value);
      default -> throw new IllegalArgumentException(
        "Unknown argument: " + args[index] + ". Valid arguments: --prompt-file <path>, --model, " +
          "--effort, --cwd, --plugin-source, --jlink-bin, --plugin-version, --agent, " +
          "--append-system-prompt, --output, --session-file");
    }
    return index + 1;
  }

  /**
   * Reads the prompt file referenced by the CLI request.
   *
   * @param promptFile the prompt-file path
   * @return the prompt text
   * @throws IOException if the prompt file cannot be read
   */
  private static String readPromptText(Path promptFile) throws IOException
  {
    try
    {
      return Files.readString(promptFile);
    }
    catch (IOException e)
    {
      throw new IOException("--prompt-file file not found: " + promptFile, e);
    }
  }

  /**
   * Executes the main Claude runner CLI path for one prompt file.
   *
   * @param scope the CLI tool scope
   * @param request the parsed CLI request
   * @param promptText the prompt file contents
   * @param out the output stream to write user-facing results to
   * @return the CLI exit code
   * @throws IOException if reading or writing artifacts fails
   */
  private static int executeCliRequest(CliTool scope, CliRequest request, String promptText,
    PrintStream out) throws IOException
  {
    try (ClaudeRunner runner = new ClaudeRunner(scope))
    {
      if (request.pluginSource() != null && request.jlinkBin() != null)
      {
        runner.createIsolatedConfig(scope.getConfigPath(), request.pluginSource(),
          request.jlinkBin(), request.pluginVersion());
      }
      ClaudeSession session = runner.loadSession(request.sessionFile(), request.model(),
        request.effort(), request.cwd(), request.appendSystemPrompt(), request.agent());
      List<String> command = buildCliCommand(runner, request, session);
      String input = runner.buildInput(List.of(), List.of(promptText), List.of());
      return executeCliTurn(scope, runner, request, session, promptText, command, input, out);
    }
  }

  /**
   * Builds the nested Claude command for one CLI request.
   * <p>
   * When {@code --agent} is specified, the nested Claude Code process resolves that agent from the
   * isolated plugin configuration (if any), honoring the candidate plugin's agent frontmatter.
   *
   * @param runner the runner that builds the nested command
   * @param request the parsed CLI request
   * @param session the loaded managed session
   * @return the nested Claude command line
   */
  private static List<String> buildCliCommand(ClaudeRunner runner, CliRequest request,
    ClaudeSession session)
  {
    String resumeSessionId = "";
    if (!session.sessionId().isBlank() && !session.turns().isEmpty())
      resumeSessionId = session.sessionId();
    return runner.buildCommand(request.model(), request.effort(), request.appendSystemPrompt(),
      request.agent(), resumeSessionId);
  }

  /**
   * Executes one nested Claude turn for the CLI and persists any requested artifacts.
   *
   * @param scope the CLI tool scope
   * @param runner the runner instance
   * @param request the parsed CLI request
   * @param session the loaded managed session
   * @param promptText the prompt file contents
   * @param command the nested Claude command
   * @param input the stream-json input payload
   * @param out the output stream to write user-facing results to
   * @return the CLI exit code
   * @throws IOException if reading or writing artifacts fails
   */
  private static int executeCliTurn(CliTool scope, ClaudeRunner runner, CliRequest request,
    ClaudeSession session, String promptText, List<String> command, String input, PrintStream out)
    throws IOException
  {
    boolean managedSession = request.sessionFile() != null;
    boolean invalidateSession = false;
    try
    {
      ProcessResult result = runner.executeProcess(command, input, request.cwd(), managedSession);
      if (managedSession)
        invalidateSession = true;
      if (emitCliError(out, result, managedSession))
        return ClaudeRunner.resolveCliExitCode(result, managedSession);
      out.println(String.join("\n", result.parsed().texts()));

      ClaudeSession updatedSession = null;
      if (managedSession)
        updatedSession = session.appendTurn(promptText, result.parsed(), result.state());
      writeCliOutput(scope, request.outputPath(), updatedSession, result.parsed(), out);
      if (managedSession)
      {
        runner.saveSession(request.sessionFile(), updatedSession);
        invalidateSession = false;
      }
      return ClaudeRunner.resolveCliExitCode(result, managedSession);
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
    if (ClaudeRunner.reachedExpectedBoundary(result.state(), managedSession))
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
      return "claude did not reach a resumable turn boundary";
    return "claude did not reach a clean completion boundary";
  }

  /**
   * Writes parsed output to the optional output file and announces the artifact path.
   *
   * @param scope the CLI tool scope
   * @param outputPath the optional output path
   * @param updatedSession the updated session, or {@code null} for one-shot runs
   * @param parsed the parsed output for the current turn
   * @param out the CLI output stream
   * @throws IOException if the output file cannot be written
   */
  private static void writeCliOutput(CliTool scope, Path outputPath, ClaudeSession updatedSession,
    ParsedOutput parsed, PrintStream out) throws IOException
  {
    if (outputPath == null)
      return;
    ParsedOutput output = parsed;
    if (updatedSession != null)
      output = updatedSession.toParsedOutput();
    try (OutputStream fileOut = Files.newOutputStream(outputPath))
    {
      scope.getJsonMapper().writeValue(fileOut, output);
    }
    out.println("Results written to: " + outputPath);
  }

  /**
   * Implements the {@code resolve-model} subcommand.
   * <p>
   * Resolves a short model name (for example, {@code "sonnet"}) to a fully-qualified model
   * identifier (for example, {@code "claude-sonnet-4-6"}).
   *
   * @param args {@code [short_name]}
   * @return the fully-qualified model identifier
   * @throws IOException if the Claude version cannot be determined
   */
  private static String resolveModel(String[] args) throws IOException
  {
    if (args.length != 1)
    {
      throw new IllegalArgumentException(
        "claude-runner resolve-model: expected 1 argument <short_name>, got " + args.length +
          ".\nUsage: claude-runner resolve-model <short_name>");
    }
    String shortName = args[0];
    return ModelIdResolver.resolveModelStrict(shortName);
  }

  /**
   * Mutable CLI request builder used while parsing command-line arguments.
   */
  private static final class CliRequestBuilder
  {
    private String prompt;
    private String model;
    private String effort;
    private Path cwd = Path.of(".");
    private Path pluginSource;
    private Path jlinkBin;
    private String pluginVersion = "2.1";
    private String agent = "";
    private String appendSystemPrompt = "";
    private Path outputPath;
    private Path sessionFile;

    /**
     * Converts the mutable builder into an immutable request.
     *
     * @return the parsed CLI request
     */
    private CliRequest toRequest()
    {
      if (prompt == null)
        throw new IllegalArgumentException("--prompt-file argument is required");
      if (model == null)
        throw new IllegalArgumentException("--model argument is required");
      if (effort == null)
        throw new IllegalArgumentException("--effort argument is required");
      return new CliRequest(Path.of(prompt), model, effort, cwd, pluginSource, jlinkBin,
        pluginVersion, agent, appendSystemPrompt, outputPath, sessionFile);
    }
  }

  /**
   * Structured Claude runner CLI request.
   *
   * @param promptFile the prompt-file path
   * @param model the requested model
   * @param effort the requested effort level
   * @param cwd the nested runner working directory
   * @param pluginSource the optional plugin source path for isolated execution
   * @param jlinkBin the optional jlink binary directory for isolated execution
   * @param pluginVersion the plugin version to advertise during isolated execution
   * @param agent the optional Claude agent name
   * @param appendSystemPrompt the optional appended system prompt fragment
   * @param outputPath the optional parsed-output artifact path
   * @param sessionFile the optional CAT-managed session file
   */
  private record CliRequest(Path promptFile, String model, String effort, Path cwd,
    Path pluginSource, Path jlinkBin, String pluginVersion, String agent,
    String appendSystemPrompt, Path outputPath, Path sessionFile)
  {
  }
}
