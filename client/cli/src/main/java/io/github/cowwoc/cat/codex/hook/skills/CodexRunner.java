/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.agent.AbstractAgentPluginScope;
import io.github.cowwoc.cat.agent.AgentPluginScope;
import io.github.cowwoc.cat.agent.TerminalType;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;

/**
 * Launches Codex CLI processes and parses JSONL event output.
 * <p>
 * Codex accepts a single prompt on standard input and writes the final response to the
 * {@code --output-last-message} file. The JSONL event stream is parsed for deterministic
 * test assertions such as text output, tool use, and patch content.
 */
public final class CodexRunner
{
  /**
   * Effort levels accepted by Codex.
   */
  private static final List<String> EFFORT_LEVELS = List.of("minimal", "low", "medium",
    "high", "xhigh");
  /**
   * Default timeout for the Codex CLI process.
   */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);
  private final AgentPluginScope scope;
  private final Duration timeout;

  /**
   * Creates a new Codex process launcher.
   *
   * @param scope the scope providing the JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public CodexRunner(AgentPluginScope scope)
  {
    this(scope, DEFAULT_TIMEOUT);
  }

  /**
   * Creates a new Codex process launcher.
   *
   * @param scope   the scope providing the JSON mapper
   * @param timeout the process timeout
   * @throws NullPointerException if {@code scope} or {@code timeout} is null
   * @throws IllegalArgumentException if {@code timeout} is not positive
   */
  public CodexRunner(AgentPluginScope scope, Duration timeout)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(timeout, "timeout").isNotNull();
    if (!timeout.isPositive())
      throw new IllegalArgumentException("timeout must be positive");
    this.scope = scope;
    this.timeout = timeout;
  }

  /**
   * Builds the Codex CLI command.
   *
   * @param model                 the model to use
   * @param effort                the reasoning effort level
   * @param cwd                   the working directory for Codex
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @return the command as a list of strings
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if {@code model} or {@code effort} is invalid
   */
  public List<String> buildCommand(String model, String effort, Path cwd,
    Path lastMessageOutputPath)
  {
    requireThat(model, "model").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    if (!EFFORT_LEVELS.contains(effort))
    {
      throw new IllegalArgumentException("Invalid effort '" + effort +
        "'. Valid values: " + EFFORT_LEVELS);
    }
    requireThat(cwd, "cwd").isNotNull();
    requireThat(lastMessageOutputPath, "lastMessageOutputPath").isNotNull();

    List<String> command = new ArrayList<>();
    command.add("codex");
    command.add("exec");
    command.add("--json");
    command.add("--output-last-message");
    command.add(lastMessageOutputPath.toString());
    command.add("--cd");
    command.add(cwd.toString());
    command.add("--model");
    command.add(model);
    command.add("-c");
    command.add("model_reasoning_effort=\"" + effort + "\"");
    command.add("-");
    return command;
  }

  /**
   * Builds a process builder for the supplied command.
   *
   * @param command the command to execute
   * @param cwd     the working directory
   * @return the process builder
   * @throws NullPointerException if {@code command} or {@code cwd} are null
   */
  public ProcessBuilder buildProcessBuilder(List<String> command, Path cwd)
  {
    requireThat(command, "command").isNotNull();
    requireThat(cwd, "cwd").isNotNull();

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(cwd.toFile());
    Map<String, String> environment = builder.environment();
    environment.remove("LD_PRELOAD");
    environment.remove("LD_LIBRARY_PATH");
    environment.remove("JAVA_TOOL_OPTIONS");
    builder.redirectErrorStream(true);
    return builder;
  }

  /**
   * Executes the Codex CLI process with the given prompt.
   *
   * @param command               the command to execute
   * @param prompt                the prompt to send to standard input
   * @param cwd                   the working directory
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if any parameter is null
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd,
    Path lastMessageOutputPath)
  {
    return executeProcess(command, prompt, cwd, lastMessageOutputPath, null);
  }

  /**
   * Executes the Codex CLI process with the given prompt.
   *
   * @param command               the command to execute
   * @param prompt                the prompt to send to standard input
   * @param cwd                   the working directory
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @param jsonlOutputPath       optional file that receives the raw JSONL stream
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if {@code command}, {@code prompt}, {@code cwd}, or
   *                              {@code lastMessageOutputPath} are null
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd,
    Path lastMessageOutputPath, Path jsonlOutputPath)
  {
    requireThat(command, "command").isNotNull();
    requireThat(prompt, "prompt").isNotNull();
    requireThat(cwd, "cwd").isNotNull();
    requireThat(lastMessageOutputPath, "lastMessageOutputPath").isNotNull();

    long startTime = System.nanoTime();
    ParsedOutput empty = new ParsedOutput(List.of(), List.of(), List.of(), List.of(), "");
    try
    {
      try (Process process = buildProcessBuilder(command, cwd).start())
      {
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), UTF_8))
        {
          writer.write(prompt);
        }

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);
        if (!completed)
        {
          process.destroyForcibly();
          outputFuture.cancel(true);
          return new ProcessResult(empty, elapsed, "timeout");
        }

        String output = outputFuture.get();
        if (jsonlOutputPath != null)
          Files.writeString(jsonlOutputPath, output, UTF_8);

        ParsedOutput parsed = appendLastMessage(parseOutput(output), lastMessageOutputPath);
        return new ProcessResult(parsed, elapsed, "");
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);
      return new ProcessResult(empty, elapsed, e.getMessage());
    }
    catch (IOException | ExecutionException e)
    {
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);
      return new ProcessResult(empty, elapsed, e.getMessage());
    }
  }

  private static String readProcessOutput(Process process)
  {
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), UTF_8)))
    {
      return readAll(reader);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Parses Codex JSONL output.
   *
   * @param output the raw JSONL output
   * @return the parsed output
   * @throws NullPointerException if {@code output} is null
   */
  public ParsedOutput parseOutput(String output)
  {
    requireThat(output, "output").isNotNull();
    try (BufferedReader reader = new BufferedReader(new StringReader(output)))
    {
      return parseOutput(reader);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Parses Codex JSONL output line by line.
   *
   * @param reader the reader supplying JSONL lines
   * @return the parsed output
   * @throws NullPointerException if {@code reader} is null
   * @throws IOException          if reading from {@code reader} fails
   */
  public ParsedOutput parseOutput(BufferedReader reader) throws IOException
  {
    requireThat(reader, "reader").isNotNull();
    List<String> texts = new ArrayList<>();
    List<String> toolUses = new ArrayList<>();
    List<String> writeContents = new ArrayList<>();
    String sessionId = "";

    String line = reader.readLine();
    while (line != null)
    {
      String trimmed = line.strip();
      if (!trimmed.isEmpty() && trimmed.charAt(0) == '{')
      {
        JsonNode event = scope.getJsonMapper().readTree(trimmed);
        if (sessionId.isEmpty())
          sessionId = firstText(event, "session_id", "sessionId", "conversation_id");
        collectText(event, texts);
        collectToolUse(event, toolUses, writeContents);
      }
      line = reader.readLine();
    }

    List<TurnOutput> turns;
    if (texts.isEmpty() && toolUses.isEmpty() && writeContents.isEmpty())
      turns = List.of();
    else
      turns = List.of(new TurnOutput(List.copyOf(texts), List.copyOf(toolUses),
        List.copyOf(writeContents)));
    return new ParsedOutput(List.copyOf(texts), List.copyOf(toolUses), List.copyOf(writeContents),
      turns, sessionId);
  }

  private static String readAll(BufferedReader reader) throws IOException
  {
    StringBuilder builder = new StringBuilder();
    String line = reader.readLine();
    while (line != null)
    {
      builder.append(line).append('\n');
      line = reader.readLine();
    }
    return builder.toString();
  }

  private ParsedOutput appendLastMessage(ParsedOutput parsed, Path lastMessageOutputPath)
    throws IOException
  {
    if (!Files.isRegularFile(lastMessageOutputPath))
      return parsed;
    String lastMessage = Files.readString(lastMessageOutputPath, UTF_8).strip();
    if (lastMessage.isEmpty() || parsed.texts().contains(lastMessage))
      return parsed;

    List<String> texts = new ArrayList<>(parsed.texts());
    texts.add(lastMessage);
    List<TurnOutput> turns = new ArrayList<>(parsed.turns());
    turns.add(new TurnOutput(List.of(lastMessage), List.of(), List.of()));
    return new ParsedOutput(List.copyOf(texts), parsed.toolUses(), parsed.writeContents(),
      List.copyOf(turns), parsed.sessionId());
  }

  private static void collectText(JsonNode event, List<String> texts)
  {
    String type = event.path("type").asString("").toLowerCase(Locale.ROOT);
    if (!type.contains("message") && !type.contains("assistant") && !type.contains("response") &&
      !type.contains("result"))
    {
      return;
    }

    String text = firstText(event, "message", "text", "content", "delta", "output");
    if (!text.isEmpty())
    {
      texts.add(text);
      return;
    }

    JsonNode item = event.path("item");
    if (!item.isMissingNode())
    {
      text = firstText(item, "message", "text", "content", "delta", "output");
      if (!text.isEmpty())
        texts.add(text);
    }
  }

  private static void collectToolUse(JsonNode event, List<String> toolUses,
    List<String> writeContents)
  {
    String type = event.path("type").asString("").toLowerCase(Locale.ROOT);
    String toolName = firstText(event, "tool_name", "toolName", "name");
    if (toolName.isEmpty())
      toolName = firstText(event.path("tool"), "name");
    if (toolName.isEmpty())
      toolName = firstText(event.path("item"), "tool_name", "toolName", "name");
    if (toolName.isEmpty() || (!type.contains("tool") && !type.contains("function") &&
      !event.has("tool_name") && !event.has("toolName")))
    {
      return;
    }

    toolUses.add(toolName);
    if (!isWriteTool(toolName))
      return;
    String content = firstText(event.path("arguments"), "patch", "content", "cmd", "command");
    if (content.isEmpty())
      content = firstText(event.path("input"), "patch", "content", "cmd", "command");
    if (content.isEmpty())
      content = firstText(event.path("tool_input"), "patch", "content", "cmd", "command");
    if (!content.isEmpty())
      writeContents.add(content);
  }

  private static boolean isWriteTool(String toolName)
  {
    String lowerCaseToolName = toolName.toLowerCase(Locale.ROOT);
    return lowerCaseToolName.contains("apply_patch") || lowerCaseToolName.equals("write") ||
      lowerCaseToolName.equals("edit") || lowerCaseToolName.endsWith(".write") ||
      lowerCaseToolName.endsWith(".edit");
  }

  private static String firstText(JsonNode node, String... fieldNames)
  {
    if (node == null || node.isMissingNode() || node.isNull())
      return "";
    for (String fieldName : fieldNames)
    {
      JsonNode value = node.path(fieldName);
      if (value.isString())
        return value.asString("");
    }
    return "";
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
          --effort <level>      Effort: minimal|low|medium|high|xhigh (required)
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

    CodexRunner runner = new CodexRunner(scope);
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
   * Main entry point for CLI invocation.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (AgentPluginScope scope = new CommandLineScope(Path.of(System.getProperty("user.dir"))))
    {
      try
      {
        System.exit(run(args, scope, System.out));
      }
      catch (IOException e)
      {
        System.err.println("ERROR: " + e.getMessage());
        System.exit(1);
      }
    }
  }

  /**
   * Process result.
   *
   * @param parsed  the parsed output
   * @param elapsed the elapsed time
   * @param error   the error message, or empty string if none
   */
  public record ProcessResult(ParsedOutput parsed, Duration elapsed, String error)
  {
    /**
     * Creates a new process result.
     *
     * @param parsed  the parsed output
     * @param elapsed the elapsed time
     * @param error   the error message, or empty string if none
     * @throws NullPointerException if {@code parsed} or {@code error} are null
     */
    public ProcessResult
    {
      requireThat(parsed, "parsed").isNotNull();
      requireThat(elapsed, "elapsed").isNotNull();
      requireThat(error, "error").isNotNull();
    }
  }

  /**
   * Parsed output containing text blocks, tool uses, and per-turn breakdown.
   *
   * @param texts         the list of text outputs
   * @param toolUses      the list of tool names invoked
   * @param writeContents the patch or write contents
   * @param turns         the per-turn breakdown
   * @param sessionId     the session ID extracted from output, or empty string
   */
  public record ParsedOutput(List<String> texts, List<String> toolUses, List<String> writeContents,
    List<TurnOutput> turns, String sessionId)
  {
    /**
     * Creates a parsed output.
     *
     * @param texts         the list of text outputs
     * @param toolUses      the list of tool names invoked
     * @param writeContents the patch or write contents
     * @param turns         the per-turn breakdown
     * @param sessionId     the session ID extracted from output, or empty string
     * @throws NullPointerException if any parameter is null
     */
    public ParsedOutput
    {
      requireThat(texts, "texts").isNotNull();
      requireThat(toolUses, "toolUses").isNotNull();
      requireThat(writeContents, "writeContents").isNotNull();
      requireThat(turns, "turns").isNotNull();
      requireThat(sessionId, "sessionId").isNotNull();
    }
  }

  /**
   * Output from a single Codex turn.
   *
   * @param texts         the text blocks from this turn
   * @param toolUses      the tool names invoked
   * @param writeContents the patch or write contents
   */
  public record TurnOutput(List<String> texts, List<String> toolUses, List<String> writeContents)
  {
    /**
     * Creates a turn output.
     *
     * @param texts         the text blocks from this turn
     * @param toolUses      the tool names invoked
     * @param writeContents the patch or write contents
     * @throws NullPointerException if any parameter is null
     */
    public TurnOutput
    {
      requireThat(texts, "texts").isNotNull();
      requireThat(toolUses, "toolUses").isNotNull();
      requireThat(writeContents, "writeContents").isNotNull();
    }
  }

  private static final class CommandLineScope extends AbstractAgentPluginScope
  {
    private final Path workDir;

    private CommandLineScope(Path projectPath)
    {
      super(projectPath.toAbsolutePath(), projectPath.toAbsolutePath(), projectPath.toAbsolutePath(),
        Path.of(".codex-plugin").resolve("plugin.json"), List.of(), Path.of(".codex-plugin").
          resolve("plugin.json"));
      this.workDir = projectPath.toAbsolutePath();
    }

    @Override
    public Path getWorkDir()
    {
      ensureOpen();
      return workDir;
    }

    @Override
    public TerminalType getTerminalType()
    {
      ensureOpen();
      return TerminalType.detect();
    }

    @Override
    public String getTimezone()
    {
      ensureOpen();
      return "UTC";
    }
  }
}
