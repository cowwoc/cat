/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import static io.github.cowwoc.cat.hooks.Strings.block;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.ClaudePluginScope;
import io.github.cowwoc.cat.hooks.MainClaudeTool;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.node.ObjectNode;

/**
 * Launches Claude CLI processes with optional config directory isolation.
 * <p>
 * Handles building stream-json input, spawning the {@code claude} CLI process, parsing
 * stream-json output, and optionally creating an isolated config directory with updated
 * plugin cache.
 */
public final class ClaudeRunner implements AutoCloseable
{
  /**
   * Allowed model name values for the {@code --model} flag.
   * <p>
   * Both short aliases ({@code haiku}, {@code sonnet}, {@code opus}) and their corresponding
   * fully-qualified model identifiers (as returned by {@code extract-model}) are accepted.
   */
  static final Set<String> ALLOWED_MODELS = Set.of(
    "haiku", "claude-haiku-4-5-20251001",
    "sonnet", "claude-sonnet-4-6",
    "opus", "claude-opus-4-6");
  /**
   * Default timeout for the claude CLI process.
   */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(180);
private final ClaudePluginScope scope;
  private final ObjectWriter compactWriter;
  private Path isolatedConfigDir;

  /**
   * Creates a new process launcher without config isolation.
   *
   * @param scope the scope providing JSON mapper and config paths
   * @throws NullPointerException if {@code scope} is null
   */
  public ClaudeRunner(ClaudePluginScope scope)
  {
    this.scope = scope;
    this.compactWriter = scope.getJsonMapper().writer().without(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Creates an isolated copy of the Claude config directory with updated plugin cache.
   * <p>
   * Copies the entire config directory to a temporary location, then overwrites the plugin
   * cache with files from the specified plugin source and jlink binary directories. The
   * isolated config is used for subsequent process launches and cleaned up on {@link #close()}.
   *
   * @param sourceConfigDir the source Claude config directory to copy (e.g., {@code ~/.config/claude})
   * @param pluginSourceDir the directory containing plugin source files to copy into the cache
   *                        (e.g., {@code worktree/plugin/})
   * @param jlinkBinDir     the directory containing built jlink binaries (e.g.,
   *                        {@code worktree/client/target/jlink/bin/})
   * @param pluginVersion   the plugin version string (e.g., "2.1")
   * @throws NullPointerException     if any parameter is null
   * @throws IllegalArgumentException if {@code pluginVersion} is blank
   * @throws IOException              if the copy fails
   */
  public void createIsolatedConfig(Path sourceConfigDir, Path pluginSourceDir,
    Path jlinkBinDir, String pluginVersion) throws IOException
  {
    requireThat(sourceConfigDir, "sourceConfigDir").isNotNull();
    requireThat(pluginSourceDir, "pluginSourceDir").isNotNull();
    requireThat(jlinkBinDir, "jlinkBinDir").isNotNull();
    requireThat(pluginVersion, "pluginVersion").isNotBlank();

    // Create temp directory for isolated config
    isolatedConfigDir = Files.createTempDirectory("claude-isolated-config-");

    // Copy entire config directory
    copyDirectoryRecursively(sourceConfigDir, isolatedConfigDir);

    // Update plugin cache with current plugin source files
    Path cachePluginDir = isolatedConfigDir.resolve("plugins").resolve("cache").
      resolve("cat").resolve("cat").resolve(pluginVersion);
    // Remove old plugin files and replace with current source
    if (Files.isDirectory(cachePluginDir))
      deleteDirectoryContents(cachePluginDir);
    else
      Files.createDirectories(cachePluginDir);
    copyDirectoryRecursively(pluginSourceDir, cachePluginDir);

    // Update jlink binaries in the cache
    Path cacheBinDir = cachePluginDir.resolve("client").resolve("bin");
    if (Files.isDirectory(jlinkBinDir))
    {
      Files.createDirectories(cacheBinDir);
      copyDirectoryRecursively(jlinkBinDir, cacheBinDir);
    }
  }

  /**
   * Returns the isolated config directory, or empty string if not isolated.
   *
   * @return the isolated config directory path, or empty string
   */
  public String getIsolatedConfigDir()
  {
    if (isolatedConfigDir == null)
      return "";
    return isolatedConfigDir.toString();
  }

  /**
   * Builds the claude CLI command with appropriate flags.
   *
   * @param model        the model name (haiku, sonnet, or opus)
   * @param systemPrompt the system prompt to append via CLI flag, or empty string for none
   * @return the command as a list of strings
   * @throws NullPointerException     if {@code model} or {@code systemPrompt} are null
   * @throws IllegalArgumentException if {@code model} is not in the allowed set
   */
  public List<String> buildCommand(String model, String systemPrompt)
  {
    requireThat(model, "model").isNotBlank();
    if (!ALLOWED_MODELS.contains(model))
    {
      throw new IllegalArgumentException("Invalid model '" + model +
        "'. Valid values: " + ALLOWED_MODELS);
    }
    requireThat(systemPrompt, "systemPrompt").isNotNull();
    List<String> command = new ArrayList<>();
    command.add("claude");
    command.add("-p");
    command.add("--model");
    command.add(model);
    command.add("--input-format");
    command.add("stream-json");
    command.add("--output-format");
    command.add("stream-json");
    command.add("--verbose");
    command.add("--dangerously-skip-permissions");
    if (!systemPrompt.isEmpty())
    {
      command.add("--append-system-prompt");
      command.add(systemPrompt);
    }
    return command;
  }

  /**
   * Builds stream-json input from priming messages and prompt strings.
   * <p>
   * System reminders are appended to each prompt as {@code <system-reminder>} tags.
   *
   * @param primingMessages the priming messages to send before the prompts
   * @param prompts         the prompt strings to send as user messages
   * @param systemReminders system reminder strings to append to each prompt
   * @return the stream-json input string
   * @throws NullPointerException if any parameter is null
   */
  public String buildInput(List<PrimingMessage> primingMessages, List<String> prompts,
    List<String> systemReminders)
  {
    requireThat(primingMessages, "primingMessages").isNotNull();
    requireThat(prompts, "prompts").isNotNull();
    requireThat(systemReminders, "systemReminders").isNotNull();
    StringJoiner joiner = new StringJoiner("\n");
    int toolUseCounter = 0;
    for (PrimingMessage msg : primingMessages)
    {
      switch (msg)
      {
        case PrimingMessage.UserMessage userMsg ->
          joiner.add(makeUserMessage(userMsg.text()));
        case PrimingMessage.ToolUse toolUse ->
        {
          String toolUseId = "toolu_priming_" + toolUseCounter;
          ++toolUseCounter;
          joiner.add(makeToolUseMessage(toolUseId, toolUse.tool(), toolUse.input()));
          joiner.add(makeToolResultMessage(toolUseId, toolUse.output()));
        }
      }
    }
    for (String prompt : prompts)
    {
      String finalPrompt = prompt;
      if (!systemReminders.isEmpty())
      {
        StringBuilder sb = new StringBuilder(prompt);
        for (String reminder : systemReminders)
        {
          sb.append("\n<system-reminder>\n").
            append(reminder).
            append("\n</system-reminder>");
        }
        finalPrompt = sb.toString();
      }
      joiner.add(makeUserMessage(finalPrompt));
    }
    return joiner.toString();
  }

  /**
   * Builds a {@link ProcessBuilder} configured with the correct environment for launching the
   * claude CLI.
   * <p>
   * Removes the {@code CLAUDECODE} env var so the spawned process does not inherit the
   * hook-suppression flag. Sets {@code CLAUDE_CONFIG_DIR} when isolation is active.
   *
   * @param command the command to execute
   * @param cwd     the working directory
   * @return the configured process builder
   * @throws NullPointerException if {@code command} or {@code cwd} are null
   */
  public ProcessBuilder buildProcessBuilder(List<String> command, Path cwd)
  {
    ProcessBuilder pb = new ProcessBuilder(command);
    Map<String, String> env = pb.environment();
    env.remove("CLAUDECODE");
    if (isolatedConfigDir != null)
      env.put("CLAUDE_CONFIG_DIR", isolatedConfigDir.toString());
    pb.directory(cwd.toFile());
    pb.redirectErrorStream(true);
    return pb;
  }

  /**
   * Executes the claude CLI process with the given input, streaming output line-by-line
   * to avoid buffering the full response in memory.
   * <p>
   * If an isolated config directory has been created via {@link #createIsolatedConfig},
   * the process will use it via the {@code CLAUDE_CONFIG_DIR} environment variable.
   *
   * @param command the command to execute
   * @param input   the stream-json input to send to the process
   * @param cwd     the working directory
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if {@code command}, {@code input}, or {@code cwd} are null
   */
  public ProcessResult executeProcess(List<String> command, String input, Path cwd)
  {
    requireThat(command, "command").isNotNull();
    requireThat(input, "input").isNotNull();
    requireThat(cwd, "cwd").isNotNull();
    long startTime = System.currentTimeMillis();
    ParsedOutput empty = new ParsedOutput(List.of(), List.of(), List.of(), List.of(), "");
    try
    {
      try (Process process = buildProcessBuilder(command, cwd).start())
      {
        try (OutputStreamWriter writer = new OutputStreamWriter(
          process.getOutputStream(), StandardCharsets.UTF_8))
        {
          writer.write(input);
        }

        ParsedOutput parsed;
        try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
          parsed = parseOutput(reader);
        }

        boolean completed = process.waitFor(DEFAULT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        if (!completed)
        {
          process.destroyForcibly();
          return new ProcessResult(empty, elapsed, "timeout");
        }

        return new ProcessResult(parsed, elapsed, "");
      }
    }
    catch (IOException | InterruptedException e)
    {
      long elapsed = (System.currentTimeMillis() - startTime) / 1000;
      return new ProcessResult(empty, elapsed, e.getMessage());
    }
  }

  /**
   * Parses stream-json output to extract assistant text blocks and tool uses.
   *
   * @param output the raw output from claude CLI
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
      // StringReader never throws IOException
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Parses stream-json output line by line from a reader, without buffering the full content.
   * <p>
   * Each line is parsed and discarded immediately, keeping memory proportional to the extracted
   * data rather than the raw JSON events.
   *
   * @param reader the reader supplying stream-json lines
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
    List<TurnOutput> turns = new ArrayList<>();
    List<String> currentTurnTexts = new ArrayList<>();
    List<String> currentTurnToolUses = new ArrayList<>();
    List<String> currentTurnWriteContents = new ArrayList<>();
    String sessionId = "";

    String line = reader.readLine();
    while (line != null)
    {
      String trimmed = line.strip();
      if (!trimmed.isEmpty())
      {
        // Only assistant and result events contain data we need to extract.
        // Skipping other events (particularly the user echo, which contains the full input
        // and can be hundreds of KB) avoids allocating large JsonNode trees for unused content.
        if (!trimmed.startsWith("{\"type\":\"assistant\"") &&
          !trimmed.startsWith("{\"type\": \"assistant\"") &&
          !trimmed.startsWith("{\"type\":\"result\"") &&
          !trimmed.startsWith("{\"type\": \"result\""))
        {
          line = reader.readLine();
          continue;
        }
        JsonNode event = scope.getJsonMapper().readTree(trimmed);
        String type = event.path("type").asString("");

        if (sessionId.isEmpty())
        {
          String id = event.path("session_id").asString("");
          if (!id.isEmpty())
            sessionId = id;
        }

        if (type.equals("assistant"))
        {
          if (!currentTurnTexts.isEmpty() || !currentTurnToolUses.isEmpty() ||
            !currentTurnWriteContents.isEmpty())
          {
            turns.add(new TurnOutput(List.copyOf(currentTurnTexts),
              List.copyOf(currentTurnToolUses), List.copyOf(currentTurnWriteContents)));
            currentTurnTexts = new ArrayList<>();
            currentTurnToolUses = new ArrayList<>();
            currentTurnWriteContents = new ArrayList<>();
          }
          JsonNode content = event.path("message").path("content");
          if (content.isArray())
          {
            for (JsonNode block : content)
            {
              String blockType = block.path("type").asString("");
              if (blockType.equals("text"))
              {
                String text = block.path("text").asString("");
                texts.add(text);
                currentTurnTexts.add(text);
              }
              else if (blockType.equals("tool_use"))
              {
                String name = block.path("name").asString("");
                toolUses.add(name);
                currentTurnToolUses.add(name);
                if (name.equals("Write"))
                {
                  // Capture the content written so callers can verify file-level behavior
                  // without needing to access the filesystem.
                  String writeContent = block.path("input").path("content").asString("");
                  if (!writeContent.isEmpty())
                  {
                    writeContents.add(writeContent);
                    currentTurnWriteContents.add(writeContent);
                  }
                }
              }
            }
          }
        }
        else if (type.equals("result"))
        {
          String result = event.path("result").asString("");
          if (!result.isEmpty())
          {
            texts.add(result);
            currentTurnTexts.add(result);
          }
        }
      }
      line = reader.readLine();
    }

    if (!currentTurnTexts.isEmpty() || !currentTurnToolUses.isEmpty() ||
      !currentTurnWriteContents.isEmpty())
    {
      turns.add(new TurnOutput(List.copyOf(currentTurnTexts),
        List.copyOf(currentTurnToolUses), List.copyOf(currentTurnWriteContents)));
    }

    return new ParsedOutput(texts, toolUses, writeContents, turns, sessionId);
  }

  @Override
  public void close() throws IOException
  {
    if (isolatedConfigDir != null)
    {
      deleteDirectoryRecursively(isolatedConfigDir);
      isolatedConfigDir = null;
    }
  }

  /**
   * Creates a stream-json message with the common envelope structure.
   *
   * @param envelopeType the type field for the outer envelope ("user" or "assistant")
   * @param role         the role field for the inner message ("user" or "assistant")
   * @param contentBlock the content block to include in the message
   * @return the compact JSON string
   */
  private String buildMessage(String envelopeType, String role, ObjectNode contentBlock)
  {
    ObjectNode message = scope.getJsonMapper().createObjectNode();
    message.put("type", envelopeType);

    ObjectNode msg = scope.getJsonMapper().createObjectNode();
    msg.put("role", role);
    msg.set("content", scope.getJsonMapper().createArrayNode().add(contentBlock));
    message.set("message", msg);

    return compactWriter.writeValueAsString(message);
  }

  /**
   * Creates a stream-json user message.
   *
   * @param text the message text
   * @return the JSON message string
   */
  private String makeUserMessage(String text)
  {
    ObjectNode content = scope.getJsonMapper().createObjectNode();
    content.put("type", "text");
    content.put("text", text);
    return buildMessage("user", "user", content);
  }

  /**
   * Creates a stream-json assistant message with tool_use.
   *
   * @param toolUseId the unique ID for the tool use
   * @param toolName  the name of the tool
   * @param toolInput the tool input as a map
   * @return the JSON message string
   */
  private String makeToolUseMessage(String toolUseId, String toolName,
    Map<String, Object> toolInput)
  {
    ObjectNode content = scope.getJsonMapper().createObjectNode();
    content.put("type", "tool_use");
    content.put("id", toolUseId);
    content.put("name", toolName);
    content.set("input", scope.getJsonMapper().valueToTree(toolInput));
    return buildMessage("assistant", "assistant", content);
  }

  /**
   * Creates a stream-json user message with tool_result.
   *
   * @param toolUseId  the ID from the tool_use message
   * @param toolOutput the tool output content
   * @return the JSON message string
   */
  private String makeToolResultMessage(String toolUseId, String toolOutput)
  {
    ObjectNode content = scope.getJsonMapper().createObjectNode();
    content.put("type", "tool_result");
    content.put("tool_use_id", toolUseId);
    content.put("content", toolOutput);
    return buildMessage("user", "user", content);
  }

  /**
   * Copies a directory tree recursively.
   *
   * @param source the source directory
   * @param target the target directory
   * @throws IOException if the copy fails
   */
  private static void copyDirectoryRecursively(Path source, Path target) throws IOException
  {
    Files.walkFileTree(source, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException
      {
        Path targetDir = target.resolve(source.relativize(dir));
        Files.createDirectories(targetDir);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        throws IOException
      {
        Files.copy(file, target.resolve(source.relativize(file)),
          StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Deletes all contents of a directory without deleting the directory itself.
   *
   * @param dir the directory to clear
   * @throws IOException if the deletion fails
   */
  private static void deleteDirectoryContents(Path dir) throws IOException
  {
    Files.walkFileTree(dir, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException
      {
        if (exception != null)
          throw exception;
        if (!directory.equals(dir))
          Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Deletes a directory and all its contents recursively.
   *
   * @param dir the directory to delete
   * @throws IOException if the deletion fails
   */
  private static void deleteDirectoryRecursively(Path dir) throws IOException
  {
    Files.walkFileTree(dir, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException
      {
        if (exception != null)
          throw exception;
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Result of executing the claude CLI process.
   *
   * @param parsed  the parsed output
   * @param elapsed the elapsed time in seconds
   * @param error   the error message, or empty string if none
   */
  public record ProcessResult(ParsedOutput parsed, long elapsed, String error)
  {
    /**
     * Creates a new process result.
     *
     * @param parsed  the parsed output
     * @param elapsed the elapsed time in seconds
     * @param error   the error message, or empty string if none
     * @throws NullPointerException if {@code parsed} or {@code error} are null
     */
    public ProcessResult
    {
      requireThat(parsed, "parsed").isNotNull();
      requireThat(error, "error").isNotNull();
    }
  }

  /**
   * Parsed output containing text blocks, tool uses, and per-turn breakdown.
   *
   * @param texts         the list of text outputs (flat, all turns combined)
   * @param toolUses      the list of tool use names (flat, all turns combined)
   * @param writeContents the list of content strings passed to Write tool calls (flat, all turns
   *                      combined)
   * @param turns         the per-turn breakdown of output
   * @param sessionId     the session ID extracted from the output, or empty string if not found
   */
  public record ParsedOutput(List<String> texts, List<String> toolUses, List<String> writeContents,
    List<TurnOutput> turns, String sessionId)
  {
    /**
     * Creates a new parsed output.
     *
     * @param texts         the list of text outputs (flat, all turns combined)
     * @param toolUses      the list of tool use names (flat, all turns combined)
     * @param writeContents the list of content strings passed to Write tool calls (flat, all turns
     *                      combined)
     * @param turns         the per-turn breakdown of output
     * @param sessionId     the session ID extracted from the output, or empty string if not found
     * @throws NullPointerException if {@code texts}, {@code toolUses}, {@code writeContents},
     *                              {@code turns}, or {@code sessionId} are null
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
   * Output from a single conversation turn.
   *
   * @param texts         the text blocks from this turn
   * @param toolUses      the tool use names from this turn
   * @param writeContents the content strings passed to Write tool calls in this turn
   */
  public record TurnOutput(List<String> texts, List<String> toolUses, List<String> writeContents)
  {
    /**
     * Creates a new turn output.
     *
     * @param texts         the text blocks from this turn
     * @param toolUses      the tool use names from this turn
     * @param writeContents the content strings passed to Write tool calls in this turn
     * @throws NullPointerException if {@code texts}, {@code toolUses}, or {@code writeContents} are null
     */
    public TurnOutput
    {
      requireThat(texts, "texts").isNotNull();
      requireThat(toolUses, "toolUses").isNotNull();
      requireThat(writeContents, "writeContents").isNotNull();
    }
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        int exitCode = run(scope, args, System.out);
        System.exit(exitCode);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(ClaudeRunner.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the claude runner logic with a caller-provided output stream.
   *
   * @param scope the scope providing access to session paths and shared services
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @return the exit code (0 for success, non-zero for failure)
   * @throws NullPointerException     if {@code scope}, {@code args}, or {@code out} are null
   * @throws IllegalArgumentException if arguments are invalid
   * @throws IOException              if an I/O error occurs
   */
  public static int run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
    {
      out.println("""
        Usage: claude-runner --prompt <text> [OPTIONS]

        Options:
          --prompt <text>          The prompt to send (required)
          --model <name>           Model: haiku|sonnet|opus (default: haiku)
          --cwd <path>             Working directory (default: current directory)
          --plugin-source <path>   Plugin source directory to copy into cache
          --jlink-bin <path>       jlink binary directory to copy into cache
          --plugin-version <ver>   Plugin version string (default: 2.1)
          --system-prompt <text>   System prompt to append
          --output <path>          Write JSON results to file""");
      return 0;
    }

    String prompt = null;
    String model = "haiku";
    Path cwd = Path.of(".");
    Path pluginSource = null;
    Path jlinkBin = null;
    String pluginVersion = "2.1";
    String systemPrompt = "";
    Path outputPath = null;

    for (int i = 0; i < args.length; ++i)
    {
      if (i + 1 >= args.length)
        continue;
      switch (args[i])
      {
        case "--prompt" ->
        {
          prompt = args[i + 1];
          ++i;
        }
        case "--model" ->
        {
          model = args[i + 1];
          ++i;
        }
        case "--cwd" ->
        {
          cwd = Path.of(args[i + 1]);
          ++i;
        }
        case "--plugin-source" ->
        {
          pluginSource = Path.of(args[i + 1]);
          ++i;
        }
        case "--jlink-bin" ->
        {
          jlinkBin = Path.of(args[i + 1]);
          ++i;
        }
        case "--plugin-version" ->
        {
          pluginVersion = args[i + 1];
          ++i;
        }
        case "--system-prompt" ->
        {
          systemPrompt = args[i + 1];
          ++i;
        }
        case "--output" ->
        {
          outputPath = Path.of(args[i + 1]);
          ++i;
        }
        default -> throw new IllegalArgumentException(
          "Unknown argument: " + args[i] + ". Valid arguments: --prompt, --model, --cwd, " +
            "--plugin-source, --jlink-bin, --plugin-version, --system-prompt, --output");
      }
    }

    if (prompt == null)
      throw new IllegalArgumentException("--prompt argument is required");

    try (ClaudeRunner runner = new ClaudeRunner(scope))
    {
      if (pluginSource != null && jlinkBin != null)
      {
        runner.createIsolatedConfig(scope.getClaudeConfigPath(), pluginSource, jlinkBin,
          pluginVersion);
      }

      List<String> command = runner.buildCommand(model, systemPrompt);
      String input = runner.buildInput(List.of(), List.of(prompt), List.of());

      try (Process process = runner.buildProcessBuilder(command, cwd).start())
      {
        try (OutputStreamWriter writer = new OutputStreamWriter(
          process.getOutputStream(), StandardCharsets.UTF_8))
        {
          writer.write(input);
        }

        ParsedOutput parsed;
        try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
          parsed = runner.parseOutput(reader);
        }

        boolean completed = process.waitFor(DEFAULT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!completed)
        {
          process.destroyForcibly();
          out.println("ERROR: timeout after " + DEFAULT_TIMEOUT.toSeconds() + "s");
          return 1;
        }

        String fullText = String.join("\n", parsed.texts());
        out.println(fullText);

        if (outputPath != null)
        {
          try (OutputStream fileOut = Files.newOutputStream(outputPath))
          {
            scope.getJsonMapper().writeValue(fileOut, parsed);
          }
          out.println("Results written to: " + outputPath);
        }

        return 0;
      }
      catch (InterruptedException e)
      {
        out.println("ERROR: " + e.getMessage());
        return 1;
      }
    }
  }
}
