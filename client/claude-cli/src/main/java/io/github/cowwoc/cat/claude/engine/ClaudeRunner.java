/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import static io.github.cowwoc.cat.tool.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.engine.NestedRunnerSessionState;
import io.github.cowwoc.cat.engine.NestedRunnerEvent;
import io.github.cowwoc.cat.engine.NestedRunnerState;
import io.github.cowwoc.cat.engine.NestedRunnerTurnState;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.util.FileUtils;
import io.github.cowwoc.cat.tool.MainCliTool;
import io.github.cowwoc.cat.tool.util.ProcessWaitHelper;
import io.github.cowwoc.cat.tool.skills.ModelIdResolver;
import io.github.cowwoc.cat.tool.skills.PrimingMessage;
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
import java.nio.file.LinkOption;
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
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ObjectWriter;

/**
 * Launches Claude Code CLI processes with optional config directory isolation.
 * <p>
 * Handles building stream-json input, spawning the Claude CLI binary process,
 * parsing stream-json output, and optionally creating an isolated config directory
 * with updated plugin cache.
 */
public final class ClaudeRunner implements AutoCloseable
{
  /**
   * Effort levels accepted by Claude Code.
   */
  private static final List<String> EFFORT_LEVELS = List.of("low", "medium", "high", "xhigh",
    "max");
  /**
   * Default timeout for the Claude CLI process.
   */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
  /**
   * Internal process-supervision cadence. This is not user-facing polling; it balances timeout and
   * reader-failure responsiveness against unnecessary wakeups.
   */
  private static final Duration WAIT_POLL = Duration.ofMillis(50);
  private final CliTool scope;
  private final ObjectWriter compactWriter;
  private final ClaudeStreamJsonBuilder streamJsonBuilder;
  private final ClaudeSessionOutputParser sessionOutputParser;
  private final Duration timeout;
  private Path isolatedConfigDir;
  private Path isolatedPluginRoot;

  /**
   * Creates a new process launcher without config isolation.
   * <p>
   * Equivalent to {@code new ClaudeRunner(scope, DEFAULT_TIMEOUT)}.
   *
   * @param scope the scope providing JSON mapper and config paths
   * @throws NullPointerException if {@code scope} is null
   */
  public ClaudeRunner(CliTool scope)
  {
    this(scope, DEFAULT_TIMEOUT);
  }

  /**
   * Creates a new process launcher without config isolation.
   *
   * @param scope   the scope providing JSON mapper and config paths
   * @param timeout the process timeout
   * @throws NullPointerException     if {@code scope} or {@code timeout} is null
   * @throws IllegalArgumentException if {@code timeout} is not positive
   */
  public ClaudeRunner(CliTool scope, Duration timeout)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(timeout, "timeout").isNotNull();
    if (!timeout.isPositive())
      throw new IllegalArgumentException("timeout must be positive");
    this.scope = scope;
    this.compactWriter = scope.getJsonMapper().writer().without(SerializationFeature.INDENT_OUTPUT);
    this.streamJsonBuilder = new ClaudeStreamJsonBuilder(scope.getJsonMapper(), compactWriter);
    this.sessionOutputParser = new ClaudeSessionOutputParser(scope.getJsonMapper());
    this.timeout = timeout;
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
   * @param jlinkBinDir     the directory containing built Claude jlink binaries (e.g.,
   *                        {@code worktree/client/distribution/target/jlink/claude/bin/})
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
    FileUtils.copyDirectoryRecursively(sourceConfigDir, isolatedConfigDir);

    // Update plugin cache with current plugin source files
    isolatedPluginRoot = isolatedConfigDir.resolve("plugins").resolve("cache").
      resolve("cat").resolve("cat").resolve(pluginVersion);
    // Remove old plugin files and replace with current source
    if (Files.isDirectory(isolatedPluginRoot))
      deleteDirectoryContents(isolatedPluginRoot);
    else
      Files.createDirectories(isolatedPluginRoot);
    FileUtils.copyDirectoryRecursively(pluginSourceDir, isolatedPluginRoot);

    // Update jlink binaries in the cache
    Path cacheBinDir = isolatedPluginRoot.resolve("client").resolve("bin");
    if (Files.isDirectory(jlinkBinDir))
    {
      Files.createDirectories(cacheBinDir);
      FileUtils.copyDirectoryRecursively(jlinkBinDir, cacheBinDir);
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
   * <p>
   * Constructs a command that invokes the native Claude CLI binary directly. Equivalent to
   * {@code buildCommand(model, effort, appendSystemPrompt, agent, "")}.
   *
   * @param model              the model name (haiku, sonnet, or opus)
   * @param effort             the reasoning effort level
   * @param appendSystemPrompt the text to append to the system prompt via
   *                           {@code --append-system-prompt}, or empty string for none
   * @param agent              the agent type name to pass via {@code --agent}, or empty string for
   *                           none
   * @return the command as a list of strings
   * @throws NullPointerException     if {@code model}, {@code effort},
   *                                  {@code appendSystemPrompt}, or {@code agent} are null
   * @throws IllegalArgumentException if {@code model} or {@code effort} is not in the allowed set
   */
  public List<String> buildCommand(String model, String effort, String appendSystemPrompt,
    String agent)
  {
    return buildCommand(model, effort, appendSystemPrompt, agent, "");
  }

  /**
   * Builds the claude CLI command with appropriate flags.
   *
   * @param model              the model name (haiku, sonnet, or opus)
   * @param effort             the reasoning effort level
   * @param appendSystemPrompt the text to append to the system prompt via
   *                           {@code --append-system-prompt}, or empty string for none
   * @param agent              the agent type name to pass via {@code --agent}, or empty string for
   *                           none
   * @param resumeSessionId    the Claude session to resume, or empty string to start a new one
   * @return the command as a list of strings
   */
  public List<String> buildCommand(String model, String effort, String appendSystemPrompt,
    String agent, String resumeSessionId)
  {
    requireThat(model, "model").isNotBlank();
    if (!ModelIdResolver.knownModels().contains(model))
    {
      throw new IllegalArgumentException("Invalid model '" + model +
        "'. Valid values: " + ModelIdResolver.knownModels());
    }
    requireThat(effort, "effort").isNotBlank();
    if (!EFFORT_LEVELS.contains(effort))
    {
      throw new IllegalArgumentException("Invalid effort '" + effort +
        "'. Valid values: " + EFFORT_LEVELS);
    }
    requireThat(appendSystemPrompt, "appendSystemPrompt").isNotNull();
    requireThat(agent, "agent").isNotNull();
    requireThat(resumeSessionId, "resumeSessionId").isNotNull();
    validateResumeSessionId(resumeSessionId);
    List<String> command = new ArrayList<>();
    command.add("claude");
    command.add("-p");
    command.add("--model");
    command.add(model);
    command.add("--effort");
    command.add(effort);
    command.add("--input-format");
    command.add("stream-json");
    command.add("--output-format");
    command.add("stream-json");
    command.add("--verbose");
    command.add("--dangerously-skip-permissions");
    if (!resumeSessionId.isBlank())
    {
      command.add("--resume");
      command.add(resumeSessionId);
    }
    if (!appendSystemPrompt.isEmpty())
    {
      command.add("--append-system-prompt");
      command.add(appendSystemPrompt);
    }
    if (!agent.isBlank())
    {
      command.add("--agent");
      command.add(agent);
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
    return streamJsonBuilder.buildInput(primingMessages, prompts, systemReminders);
  }

  /**
   * Builds a {@link ProcessBuilder} configured with the correct environment for launching the
   * Claude Code CLI.
   * <p>
   * Removes the {@code CLAUDECODE} env var so the spawned process does not inherit the
   * hook-suppression flag. Sets {@code CLAUDE_CONFIG_DIR} and {@code CLAUDE_PLUGIN_ROOT} when
   * isolation is active so that all consumers read from the isolated plugin copy.
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
    // Remove env vars that can be used to inject malicious code into the spawned process.
    env.remove("LD_PRELOAD");
    env.remove("LD_LIBRARY_PATH");
    env.remove("JAVA_TOOL_OPTIONS");
    // Override CLAUDE_PROJECT_DIR so the Claude Code process and its agents resolve relative
    // file paths against the runner worktree, not the main workspace.
    env.put("CLAUDE_PROJECT_DIR", cwd.toAbsolutePath().toString());

    if (isolatedConfigDir != null)
    {
      env.put("CLAUDE_CONFIG_DIR", isolatedConfigDir.toString());
      env.put("CLAUDE_PLUGIN_ROOT", isolatedPluginRoot.toString());
    }
    pb.directory(cwd.toFile());
    pb.redirectErrorStream(true);
    return pb;
  }

  /**
   * Executes the Claude CLI process with the given input, streaming output
   * line-by-line to avoid buffering the full response in memory.
   * <p>
   * If an isolated config directory has been created via {@link #createIsolatedConfig},
   * the process will use it via the {@code CLAUDE_CONFIG_DIR} environment variable. Equivalent to
   * {@code executeProcess(command, input, cwd, false, _ -> {})}.
   *
   * @param command the command to execute
   * @param input   the stream-json input to send to the process
   * @param cwd     the working directory
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if {@code command}, {@code input}, or {@code cwd} are null
   */
  public ProcessResult executeProcess(List<String> command, String input, Path cwd)
  {
    return executeProcess(command, input, cwd, false, _ -> {});
  }

  /**
   * Executes the Claude CLI process with the given input, optionally preserving resumable state.
   * <p>
   * Equivalent to
   * {@code executeProcess(command, input, cwd, preserveResumableState, _ -> {})}.
   *
   * @param command                the command to execute
   * @param input                  the stream-json input to send to the process
   * @param cwd                    the working directory
   * @param preserveResumableState whether to preserve a resumable waiting boundary instead of
   *                               converting it into terminal completion once the process exits
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if {@code command}, {@code input}, or {@code cwd} are null
   */
  public ProcessResult executeProcess(List<String> command, String input, Path cwd,
    boolean preserveResumableState)
  {
    return executeProcess(command, input, cwd, preserveResumableState, _ -> {});
  }

  /**
   * Executes the Claude CLI process with the given input, optionally preserving resumable state,
   * while streaming state updates to a listener.
   *
   * @param command                the command to execute
   * @param input                  the stream-json input to send to the process
   * @param cwd                    the working directory
   * @param preserveResumableState whether to preserve a resumable waiting boundary instead of
   *                               converting it into terminal completion once the process exits
   * @param eventListener          receives state snapshots as relevant engine events arrive;
   *                               callbacks reflect only emitted engine events, while the returned
   *                               result may further normalize one-shot completion after exit;
   *                               listeners must return promptly and should honor interruption
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if {@code command}, {@code input}, {@code cwd}, or
   *                              {@code eventListener} are null
   */
  public ProcessResult executeProcess(List<String> command, String input, Path cwd,
    boolean preserveResumableState, Consumer<NestedRunnerEvent> eventListener)
  {
    requireThat(command, "command").isNotNull();
    requireThat(input, "input").isNotNull();
    requireThat(cwd, "cwd").isNotNull();
    requireThat(eventListener, "eventListener").isNotNull();
    long startTimeNanos = System.nanoTime();
    ParsedOutput empty = new ParsedOutput(List.of(), List.of(), List.of(), List.of(), "");
    NestedRunnerState emptyState = new NestedRunnerState("", "", "", "",
      NestedRunnerTurnState.UNKNOWN, NestedRunnerSessionState.UNKNOWN, false, "", "");
    try
    {
      try (Process process = buildProcessBuilder(command, cwd).start())
      {
        ParsedSessionOutput[] resultHolder = new ParsedSessionOutput[1];
        AtomicReference<Exception> readerFailure = new AtomicReference<>();
        Thread stdoutReader = startOutputReader(process, eventListener, resultHolder,
          readerFailure);
        writeProcessInput(process, input);

        AwaitedProcess awaited = awaitProcess(process, stdoutReader, readerFailure, resultHolder,
          startTimeNanos, empty, emptyState);
        if (awaited.earlyResult() != null)
          return awaited.earlyResult();
        return toProcessResult(awaited, preserveResumableState);
      }
    }
    catch (IOException | InterruptedException e)
    {
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startTimeNanos);
      return new ProcessResult(empty, emptyState, elapsed, e.getMessage(), -1);
    }
  }

  /**
   * Starts the stdout reader thread that parses Claude stream-json events.
   *
   * @param process the running Claude process
   * @param eventListener receives streamed state updates
   * @param resultHolder stores the parsed session output
   * @param readerFailure stores reader-thread failures
   * @return the started reader thread
   */
  private Thread startOutputReader(Process process, Consumer<NestedRunnerEvent> eventListener,
    ParsedSessionOutput[] resultHolder, AtomicReference<Exception> readerFailure)
  {
    return Thread.ofVirtual().start(() ->
    {
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        resultHolder[0] = parseSessionOutput(reader, eventListener);
      }
      catch (IOException | RuntimeException e)
      {
        readerFailure.set(e);
      }
    });
  }

  /**
   * Writes the stream-json input payload to the nested Claude process.
   *
   * @param process the running Claude process
   * @param input the stream-json input payload
   * @throws IOException if writing fails
   */
  private static void writeProcessInput(Process process, String input) throws IOException
  {
    try (OutputStreamWriter writer = new OutputStreamWriter(
      process.getOutputStream(), StandardCharsets.UTF_8))
    {
      writer.write(input);
    }
  }

  /**
   * Waits for process completion, reader completion, or the first fatal failure.
   *
   * @param process the running Claude process
   * @param stdoutReader the stdout reader thread
   * @param readerFailure stores reader-thread failures
   * @param resultHolder stores the parsed session output
   * @param startTimeNanos the start time from {@link System#nanoTime()}
   * @param empty the empty parsed-output sentinel
   * @param emptyState the empty state sentinel
   * @return the awaited process outcome
   * @throws InterruptedException if interrupted while waiting
   */
  private AwaitedProcess awaitProcess(Process process, Thread stdoutReader,
    AtomicReference<Exception> readerFailure, ParsedSessionOutput[] resultHolder,
    long startTimeNanos, ParsedOutput empty, NestedRunnerState emptyState)
    throws InterruptedException
  {
    long deadlineNanos = startTimeNanos + timeout.toNanos();
    boolean completed = waitForProcessOrReaderFailure(process, readerFailure, deadlineNanos);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startTimeNanos);
    Exception processReaderFailure = readerFailure.get();
    if (processReaderFailure != null)
    {
      process.destroyForcibly();
      stopReaderThread(stdoutReader);
      return AwaitedProcess.early(failureResult(empty, emptyState, elapsed, processReaderFailure));
    }
    if (!completed)
    {
      process.destroyForcibly();
      stopReaderThread(stdoutReader);
      return AwaitedProcess.early(timeoutResult(empty, elapsed));
    }
    ProcessResult readerJoinFailure = joinReaderThread(stdoutReader, deadlineNanos, process,
      empty, emptyState, elapsed);
    if (readerJoinFailure != null)
      return AwaitedProcess.early(readerJoinFailure);
    processReaderFailure = readerFailure.get();
    if (processReaderFailure != null)
      return AwaitedProcess.early(failureResult(empty, emptyState, elapsed, processReaderFailure));
    return AwaitedProcess.completed(resultHolder[0], elapsed, process.exitValue());
  }

  /**
   * Waits for the stdout reader thread to finish within the remaining timeout budget.
   *
   * @param stdoutReader the stdout reader thread
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @param process the running Claude process
   * @param empty the empty parsed-output sentinel
   * @param emptyState the empty state sentinel
   * @param elapsed the elapsed runtime observed so far
   * @return a failure result, or {@code null} if the reader finished successfully
   */
  private ProcessResult joinReaderThread(Thread stdoutReader, long deadlineNanos, Process process,
    ParsedOutput empty, NestedRunnerState emptyState, Duration elapsed)
  {
    try
    {
      long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
      stdoutReader.join(Duration.ofNanos(remainingNanos).toMillis());
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      return new ProcessResult(empty, emptyState, elapsed, e.getMessage(), -1);
    }
    if (!stdoutReader.isAlive())
      return null;
    process.destroyForcibly();
    stopReaderThread(stdoutReader);
    return timeoutResult(empty, elapsed);
  }

  /**
   * Converts a completed awaited process into the final public process result.
   *
   * @param awaited the completed awaited process
   * @param preserveResumableState whether to preserve resumable state
   * @return the final process result
   */
  private static ProcessResult toProcessResult(AwaitedProcess awaited,
    boolean preserveResumableState)
  {
    ParsedSessionOutput parsedSession = awaited.parsedSession();
    ParsedOutput parsed = parsedSession.parsed();
    NestedRunnerState state = parsedSession.state();
    if (!preserveResumableState)
      state = finalizeCompletedState(state);
    String error = "";
    if (awaited.exitCode() != 0 && !reachedExpectedBoundary(state, preserveResumableState))
      error = "claude exited with code " + awaited.exitCode();
    return new ProcessResult(parsed, state, awaited.elapsed(), error, awaited.exitCode());
  }

  /**
   * Creates a failure process result from a reader or callback exception.
   *
   * @param empty the empty parsed-output sentinel
   * @param emptyState the empty state sentinel
   * @param elapsed the elapsed runtime
   * @param failure the underlying reader or callback failure
   * @return the failure process result
   */
  private static ProcessResult failureResult(ParsedOutput empty, NestedRunnerState emptyState,
    Duration elapsed, Exception failure)
  {
    return new ProcessResult(empty, emptyState, elapsed, failure.getMessage(), -1);
  }

  /**
   * Creates a timeout process result.
   *
   * @param empty the empty parsed-output sentinel
   * @param elapsed the elapsed runtime
   * @return the timeout process result
   */
  private static ProcessResult timeoutResult(ParsedOutput empty, Duration elapsed)
  {
    NestedRunnerState timeoutState = new NestedRunnerState("", "", "", "",
      NestedRunnerTurnState.TIMEOUT, NestedRunnerSessionState.TIMEOUT, false, "", "timeout");
    return new ProcessResult(empty, timeoutState, elapsed, "timeout", -1);
  }

  /**
   * Waits until the nested process exits, the stdout reader fails, or the deadline expires.
   *
   * @param process the nested process
   * @param readerFailure captures reader-thread failures
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @return {@code true} if the process exited before the deadline; otherwise {@code false}
   * @throws InterruptedException if interrupted while waiting
   */
  private static boolean waitForProcessOrReaderFailure(Process process,
    AtomicReference<Exception> readerFailure, long deadlineNanos)
    throws InterruptedException
  {
    return ProcessWaitHelper.waitForProcessOrFailure(process, () -> readerFailure.get() != null,
      deadlineNanos, WAIT_POLL);
  }

  /**
   * Converts a resumable waiting boundary into terminal completion for one-shot execution.
   *
   * @param state the derived runner state
   * @return the terminalized state for one-shot callers
   */
  private static NestedRunnerState finalizeCompletedState(NestedRunnerState state)
  {
    if (state.sessionState() != NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST ||
      state.turnState() != NestedRunnerTurnState.COMPLETED)
    {
      return state;
    }
    return new NestedRunnerState(state.sessionId(), state.currentTurnId(),
      state.latestEventType(), state.latestEventTimestamp(), state.turnState(),
      NestedRunnerSessionState.COMPLETED, false, state.engineSubstate(), state.error());
  }

  /**
   * Interrupts and briefly joins the stdout reader thread during cleanup.
   *
   * @param stdoutReader the reader thread to stop
   */
  private void stopReaderThread(Thread stdoutReader)
  {
    stdoutReader.interrupt();
    try
    {
      stdoutReader.join(100);
    }
    catch (InterruptedException _)
    {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Awaited process state for {@link #executeProcess(List, String, Path, boolean, Consumer)}.
   *
   * @param earlyResult the early failure result, or {@code null} when execution completed normally
   * @param parsedSession the parsed session output when execution completed normally
   * @param elapsed the elapsed runtime
   * @param exitCode the nested process exit code
   */
  private record AwaitedProcess(ProcessResult earlyResult, ParsedSessionOutput parsedSession,
    Duration elapsed, int exitCode)
  {
    /**
     * Returns an awaited process that completed early with a terminal result.
     *
     * @param earlyResult the early terminal result
     * @return the awaited process wrapper
     */
    private static AwaitedProcess early(ProcessResult earlyResult)
    {
      return new AwaitedProcess(earlyResult, null, earlyResult.elapsed(), earlyResult.exitCode());
    }

    /**
     * Returns an awaited process that completed normally.
     *
     * @param parsedSession the parsed session output
     * @param elapsed the elapsed runtime
     * @param exitCode the nested process exit code
     * @return the awaited process wrapper
     */
    private static AwaitedProcess completed(ParsedSessionOutput parsedSession, Duration elapsed,
      int exitCode)
    {
      return new AwaitedProcess(null, parsedSession, elapsed, exitCode);
    }
  }

  /**
   * Parses stream-json output to extract assistant text blocks and tool uses.
   *
   * @param output the raw output from Claude Code CLI
   * @return the parsed output
   * @throws NullPointerException if {@code output} is null
   */
  public ParsedOutput parseOutput(String output)
  {
    requireThat(output, "output").isNotNull();
    try (BufferedReader reader = new BufferedReader(new StringReader(output)))
    {
      return parseSessionOutput(reader).parsed();
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
    return parseSessionOutput(reader).parsed();
  }

  /**
   * Parses stream-json output and derives the latest session state.
   *
   * @param output the raw output from Claude Code CLI
   * @return the parsed output and derived state
   * @throws NullPointerException if {@code output} is null
   */
  public ParsedSessionOutput parseSessionOutput(String output)
  {
    requireThat(output, "output").isNotNull();
    try (BufferedReader reader = new BufferedReader(new StringReader(output)))
    {
      return parseSessionOutput(reader);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Parses stream-json output and derives the latest session state.
   *
   * @param reader the reader supplying stream-json lines
   * @return the parsed output and derived state
   * @throws NullPointerException if {@code reader} is null
   * @throws IOException          if reading from {@code reader} fails
   */
  public ParsedSessionOutput parseSessionOutput(BufferedReader reader) throws IOException
  {
    return parseSessionOutput(reader, _ -> {});
  }

  /**
   * Parses stream-json output and derives the latest session state, streaming state updates as
   * relevant events arrive.
   *
   * @param reader        the reader supplying stream-json lines
   * @param eventListener receives state snapshots as relevant engine events arrive; listeners
   *                      must return promptly and should honor interruption
   * @return the parsed output and derived state
   * @throws NullPointerException if {@code reader} or {@code eventListener} are null
   * @throws IOException          if reading from {@code reader} fails
   */
  public ParsedSessionOutput parseSessionOutput(BufferedReader reader,
    Consumer<NestedRunnerEvent> eventListener) throws IOException
  {
    return sessionOutputParser.parseSessionOutput(reader, eventListener);
  }

  @Override
  public void close() throws IOException
  {
    if (isolatedConfigDir != null)
    {
      FileUtils.deleteDirectoryRecursively(isolatedConfigDir);
      isolatedConfigDir = null;
    }
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
   * Result of executing the Claude CLI process.
   *
   * @param parsed  the parsed output
   * @param state   the latest derived runner state
   * @param elapsed the elapsed time
   * @param error   the error message, or empty string if none
   * @param exitCode the nested Claude CLI exit code, or {@code -1} if unavailable
   */
  public record ProcessResult(ParsedOutput parsed, NestedRunnerState state, Duration elapsed,
                              String error, int exitCode)
  {
    /**
     * Creates a new process result.
     *
     * @param parsed  the parsed output
     * @param state   the latest derived runner state
     * @param elapsed the elapsed time
     * @param error   the error message, or empty string if none
     * @param exitCode the nested Claude CLI exit code, or {@code -1} if unavailable
     * @throws NullPointerException if {@code parsed}, {@code state}, or {@code error} are null
     */
    public ProcessResult
    {
      requireThat(parsed, "parsed").isNotNull();
      requireThat(state, "state").isNotNull();
      requireThat(elapsed, "elapsed").isNotNull();
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
     * @throws NullPointerException if any argument is null
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
    try (CliTool scope = new MainCliTool())
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
  public static int run(CliTool scope, String[] args, PrintStream out) throws IOException
  {
    return ClaudeRunnerCli.run(scope, args, out);
  }

  /**
   * Loads a managed session from disk, or returns a ready-to-start empty session if none exists.
   *
   * @param sessionFile the persisted session file, or {@code null} for an ephemeral session
   * @param model       the model for the requested turn
   * @param effort      the effort for the requested turn
   * @param cwd         the working directory for the requested turn
   * @return the loaded or initialized session
   * @throws IOException if the session file cannot be read
   */
  public ClaudeSession loadSession(Path sessionFile, String model, String effort, Path cwd) throws IOException
  {
    return loadSession(sessionFile, model, effort, cwd, "", "");
  }

  /**
   * Loads a managed session from disk and validates the runner settings that define resume semantics.
   *
   * @param sessionFile         the persisted session file, or {@code null} for an ephemeral session
   * @param model               the model for the requested turn
   * @param effort              the effort for the requested turn
   * @param cwd                 the working directory for the requested turn
   * @param appendSystemPrompt  the appended system prompt fragment for the requested turn
   * @param agent               the Claude agent name for the requested turn
   * @return the loaded or initialized session
   * @throws IOException if the session file cannot be read
   */
  public ClaudeSession loadSession(Path sessionFile, String model, String effort, Path cwd,
    String appendSystemPrompt, String agent) throws IOException
  {
    requireThat(model, "model").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();
    requireThat(appendSystemPrompt, "appendSystemPrompt").isNotNull();
    requireThat(agent, "agent").isNotNull();
    Path resolvedSessionFile = resolveSessionFile(sessionFile, cwd);
    if (resolvedSessionFile == null || Files.notExists(resolvedSessionFile))
      return new ClaudeSession("", model, effort, normalizeCwd(cwd), appendSystemPrompt, agent,
        List.of(), new NestedRunnerState("", "", "", "", NestedRunnerTurnState.UNKNOWN,
        NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));
    rejectSymlinkSessionFile(resolvedSessionFile);
    ClaudeSession session = scope.getJsonMapper().readValue(Files.readString(resolvedSessionFile),
      ClaudeSession.class);
    if (!session.model().equals(model) || !session.effort().equals(effort))
    {
      throw new IllegalArgumentException("Session file model/effort does not match current request");
    }
    if (!session.cwd().equals(normalizeCwd(cwd)))
      throw new IllegalArgumentException("Session file cwd does not match current request");
    if (!session.appendSystemPrompt().equals(appendSystemPrompt) || !session.agent().equals(agent))
    {
      throw new IllegalArgumentException(
        "Session file prompt/agent settings do not match current request");
    }
    validateLoadedSession(session);
    if (!session.sessionId().isBlank() && !isReadyForNextTurn(session.latestState()))
      throw new IllegalArgumentException("Session file is not ready for another turn");
    return normalizeLoadedSession(session);
  }

  /**
   * Normalizes a working directory to a stable absolute string form.
   *
   * @param cwd the working directory
   * @return the normalized directory string
   * @throws IOException if resolving an existing directory fails
   */
  private static String normalizeCwd(Path cwd) throws IOException
  {
    if (Files.exists(cwd))
      return cwd.toRealPath().toString();
    return cwd.toAbsolutePath().normalize().toString();
  }

  /**
   * Returns whether a runner state is resumable for another managed-session turn.
   *
   * @param state the state to inspect
   * @return {@code true} if the state can accept another turn
   */
  private static boolean isReadyForNextTurn(NestedRunnerState state)
  {
    return state.sessionState() == NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST &&
      state.turnState() == NestedRunnerTurnState.COMPLETED &&
      state.canSubmitTurn();
  }

  /**
   * Returns whether a runner state represents terminal one-shot completion.
   *
   * @param state the state to inspect
   * @return {@code true} if the state represents terminal completion
   */
  private static boolean isTerminalCompleted(NestedRunnerState state)
  {
    return state.sessionState() == NestedRunnerSessionState.COMPLETED &&
      state.turnState() == NestedRunnerTurnState.COMPLETED &&
      !state.canSubmitTurn();
  }

  /**
   * Returns whether a runner state reached the expected boundary for the current execution mode.
   *
   * @param state          the state to evaluate
   * @param managedSession {@code true} if the caller expects a resumable managed-session boundary;
   *                       {@code false} if the caller expects terminal one-shot completion
   * @return {@code true} if the state reached the expected boundary
   */
  public static boolean reachedExpectedBoundary(NestedRunnerState state, boolean managedSession)
  {
    if (managedSession)
      return isReadyForNextTurn(state);
    return isTerminalCompleted(state);
  }

  /**
   * Resolves the CLI exit code for a Claude runner invocation.
   *
   * @param result         the process result
   * @param managedSession {@code true} if the caller expects a resumable managed-session boundary;
   *                       {@code false} if the caller expects terminal one-shot completion
   * @return {@code 0} if the result reached the expected boundary without runner error;
   *         otherwise the nested exit code when non-zero, or {@code 1}
   */
  public static int resolveCliExitCode(ProcessResult result, boolean managedSession)
  {
    requireThat(result, "result").isNotNull();
    if (result.error().isEmpty() && reachedExpectedBoundary(result.state(), managedSession))
      return 0;
    if (result.exitCode() != 0)
      return result.exitCode();
    return 1;
  }

  /**
   * Saves a managed session to disk.
   *
   * @param sessionFile the destination file
   * @param session     the session snapshot
   * @throws IOException if the session cannot be written
   */
  public void saveSession(Path sessionFile, ClaudeSession session) throws IOException
  {
    requireThat(sessionFile, "sessionFile").isNotNull();
    requireThat(session, "session").isNotNull();
    Path resolvedSessionFile = resolveSessionFile(sessionFile, Path.of(session.cwd()));
    Path parent = resolvedSessionFile.toAbsolutePath().normalize().getParent();
    if (parent != null)
      Files.createDirectories(parent);
    rejectSymlinkSessionFile(resolvedSessionFile);
    Path tempFile = Files.createTempFile(parent, resolvedSessionFile.getFileName().toString(),
      ".tmp");
    try (OutputStream fileOut = Files.newOutputStream(tempFile))
    {
      scope.getJsonMapper().writeValue(fileOut, normalizeLoadedSession(session));
      Files.move(tempFile, resolvedSessionFile, StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Removes a persisted managed session.
   *
   * @param sessionFile the session file to remove
   * @param cwd         the working directory boundary that owns the session file
   * @throws IOException if deletion fails
   */
  public void closeSession(Path sessionFile, Path cwd) throws IOException
  {
    requireThat(cwd, "cwd").isNotNull();
    Path resolvedSessionFile = resolveSessionFile(sessionFile, cwd);
    rejectSymlinkSessionFile(resolvedSessionFile);
    Files.deleteIfExists(resolvedSessionFile);
  }

  /**
   * Starts a managed Claude session for direct turn-by-turn interaction.
   * <p>
   * Equivalent to {@code startSession(sessionFile, model, effort, cwd, "", "")}.
   * <p>
   * The returned handle owns the resumable session lifecycle. Successful turns may persist a
   * resumable snapshot to {@code sessionFile}, but calling {@link ManagedSession#close()} is
   * terminal: it finalizes the in-memory handle and deletes the persisted session file.
   * Callers that need to resume later must do so before closing the handle.
   *
   * @param sessionFile the persisted session file, or {@code null} for in-memory only
   * @param model       the model to use
   * @param effort      the reasoning effort level
   * @param cwd         the working directory boundary
   * @return the managed session handle
   * @throws IOException if the session cannot be loaded
   */
  public ManagedSession startSession(Path sessionFile, String model, String effort, Path cwd)
    throws IOException
  {
    return startSession(sessionFile, model, effort, cwd, "", "");
  }

  /**
   * Starts a managed Claude session for direct turn-by-turn interaction.
   * <p>
   * The returned handle owns the resumable session lifecycle. Successful turns may persist a
   * resumable snapshot to {@code sessionFile}, but calling {@link ManagedSession#close()} is
   * terminal: it finalizes the in-memory handle and deletes the persisted session file.
   * Callers that need to resume later must do so before closing the handle.
   *
   * @param sessionFile        the persisted session file, or {@code null} for in-memory only
   * @param model              the model to use
   * @param effort             the reasoning effort level
   * @param cwd                the working directory boundary
   * @param appendSystemPrompt the appended system prompt fragment bound to this session
   * @param agent              the Claude agent bound to this session
   * @return the managed session handle
   * @throws IOException if the session cannot be loaded
   */
  public ManagedSession startSession(Path sessionFile, String model, String effort, Path cwd,
    String appendSystemPrompt, String agent) throws IOException
  {
    ClaudeSession session = loadSession(sessionFile, model, effort, cwd, appendSystemPrompt, agent);
    return new ManagedSession(sessionFile, cwd, session);
  }

  /**
   * Validates internal consistency of a persisted Claude session.
   *
   * @param session the loaded session
   */
  private static void validateLoadedSession(ClaudeSession session)
  {
    if (session.turns().isEmpty() && !session.sessionId().isBlank())
      throw new IllegalArgumentException("Session file has a native session id but no turns");
    if (!session.turns().isEmpty() && session.sessionId().isBlank())
      throw new IllegalArgumentException("Session file has turns but no native session id");
    String stateSessionId = session.latestState().sessionId();
    if (!stateSessionId.isBlank() && !session.sessionId().isBlank() &&
      !stateSessionId.equals(session.sessionId()))
    {
      throw new IllegalArgumentException("Session file session id does not match latest state");
    }
  }

  /**
   * Normalizes a loaded session so the latest state carries the persisted session id when needed.
   *
   * @param session the loaded session
   * @return the normalized session
   */
  private static ClaudeSession normalizeLoadedSession(ClaudeSession session)
  {
    if (session.sessionId().isBlank() || !session.latestState().sessionId().isBlank())
      return session;
    NestedRunnerState state = session.latestState();
    return new ClaudeSession(session.sessionId(), session.model(), session.effort(), session.cwd(),
      session.appendSystemPrompt(), session.agent(), session.turns(),
      new NestedRunnerState(session.sessionId(), state.currentTurnId(), state.latestEventType(),
        state.latestEventTimestamp(), state.turnState(), state.sessionState(),
        state.canSubmitTurn(), state.engineSubstate(), state.error()));
  }

  /**
   * Rejects symlinked session files before reading or writing them.
   *
   * @param sessionFile the candidate session file
   * @throws IOException if the session file is a symbolic link
   */
  private static void rejectSymlinkSessionFile(Path sessionFile) throws IOException
  {
    if (Files.isSymbolicLink(sessionFile))
      throw new IOException("Session file must not be a symbolic link: " + sessionFile);
  }

  /**
   * Resolves a managed-session file under the requested cwd boundary.
   *
   * @param sessionFile the candidate session file, or {@code null}
   * @param cwd the cwd boundary
   * @return the normalized session path, or {@code null} if no session file was requested
   * @throws IOException if resolving an existing ancestor or file fails
   */
  private static Path resolveSessionFile(Path sessionFile, Path cwd) throws IOException
  {
    if (sessionFile == null)
      return null;
    Path boundary = Path.of(normalizeCwd(cwd));
    Path candidate = sessionFile;
    if (!candidate.isAbsolute())
      candidate = boundary.resolve(candidate);
    candidate = candidate.normalize();
    if (!candidate.startsWith(boundary))
      throw new IllegalArgumentException("Session file must be under cwd: " + boundary);
    Path parent = candidate.getParent();
    Path existingAncestor = parent;
    while (existingAncestor != null && Files.notExists(existingAncestor))
      existingAncestor = existingAncestor.getParent();
    if (existingAncestor != null)
    {
      Path resolvedAncestor = existingAncestor.toRealPath();
      if (!resolvedAncestor.startsWith(boundary))
        throw new IllegalArgumentException("Session file must be under cwd: " + boundary);
    }
    if (Files.exists(candidate) && !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Session file must be a regular file: " + candidate);
    return candidate;
  }

  /**
   * Validates a native resume session id before it is passed to the Claude CLI.
   *
   * @param sessionId the native session id
   */
  private static void validateResumeSessionId(String sessionId)
  {
    if (sessionId.startsWith("-"))
      throw new IllegalArgumentException("Session id must not start with '-': " + sessionId);
  }

  /**
   * Parsed output plus the derived latest state.
   *
   * @param parsed the parsed output
   * @param state  the derived latest state
   */
  public record ParsedSessionOutput(ParsedOutput parsed, NestedRunnerState state)
  {
    /**
     * Creates a parsed session output.
     *
     * @param parsed the parsed output
     * @param state  the derived latest state
     */
    public ParsedSessionOutput
    {
      requireThat(parsed, "parsed").isNotNull();
      requireThat(state, "state").isNotNull();
    }
  }

  /**
   * A persisted turn inside a managed Claude session.
   *
   * @param prompt         the submitted prompt
   * @param assistantTexts assistant text blocks emitted for this turn
   * @param toolUses       tools used during this turn
   * @param writeContents  write or patch contents emitted during this turn
   */
  public record ClaudeSessionTurn(String prompt, List<String> assistantTexts, List<String> toolUses,
    List<String> writeContents)
  {
    /**
     * Creates a persisted turn.
     *
     * @param prompt         the submitted prompt
     * @param assistantTexts assistant text blocks emitted for this turn
     * @param toolUses       tools used during this turn
     * @param writeContents  write or patch contents emitted during this turn
     */
    public ClaudeSessionTurn
    {
      requireThat(prompt, "prompt").isNotNull();
      requireThat(assistantTexts, "assistantTexts").isNotNull();
      requireThat(toolUses, "toolUses").isNotNull();
      requireThat(writeContents, "writeContents").isNotNull();
    }
  }

  /**
   * A managed Claude session persisted by CAT between turns.
   *
   * @param sessionId   the Claude session identifier
   * @param model       the model bound to this session
   * @param effort      the effort bound to this session
   * @param cwd         the working directory bound to this session
   * @param appendSystemPrompt the system prompt fragment bound to this session
   * @param agent       the Claude agent bound to this session
   * @param turns       the completed turns in order
   * @param latestState the latest derived runner state
   */
  public record ClaudeSession(String sessionId, String model, String effort, String cwd,
    String appendSystemPrompt, String agent, List<ClaudeSessionTurn> turns,
    NestedRunnerState latestState)
  {
    /**
     * Creates a managed session snapshot.
     *
     * @param sessionId   the Claude session identifier
     * @param model       the model bound to this session
     * @param effort      the effort bound to this session
     * @param cwd         the working directory bound to this session
     * @param appendSystemPrompt the system prompt fragment bound to this session
     * @param agent       the Claude agent bound to this session
     * @param turns       the completed turns in order
     * @param latestState the latest derived runner state
     */
    public ClaudeSession
    {
      requireThat(sessionId, "sessionId").isNotNull();
      requireThat(model, "model").isNotNull();
      requireThat(effort, "effort").isNotNull();
      requireThat(cwd, "cwd").isNotNull();
      requireThat(appendSystemPrompt, "appendSystemPrompt").isNotNull();
      requireThat(agent, "agent").isNotNull();
      requireThat(turns, "turns").isNotNull();
      requireThat(latestState, "latestState").isNotNull();
    }

    /**
     * Returns a copy of this session with an appended turn and updated latest state.
     *
     * @param prompt the submitted prompt
     * @param parsed the parsed output for the turn
     * @param state  the latest derived state for the turn
     * @return the updated session
     */
    public ClaudeSession appendTurn(String prompt, ParsedOutput parsed, NestedRunnerState state)
    {
      requireThat(prompt, "prompt").isNotNull();
      requireThat(parsed, "parsed").isNotNull();
      requireThat(state, "state").isNotNull();
      List<ClaudeSessionTurn> updatedTurns = new ArrayList<>(turns);
      TurnOutput turn;
      if (parsed.turns().isEmpty())
      {
        turn = new TurnOutput(List.copyOf(parsed.texts()), List.copyOf(parsed.toolUses()),
          List.copyOf(parsed.writeContents()));
      }
      else
      {
        turn = parsed.turns().getLast();
      }
      updatedTurns.add(new ClaudeSessionTurn(prompt, List.copyOf(turn.texts()),
        List.copyOf(turn.toolUses()), List.copyOf(turn.writeContents())));
      String resolvedSessionId = parsed.sessionId();
      if (resolvedSessionId.isBlank())
        resolvedSessionId = sessionId;
      return new ClaudeSession(resolvedSessionId, model, effort, cwd, appendSystemPrompt, agent,
        List.copyOf(updatedTurns), new NestedRunnerState(resolvedSessionId, state.currentTurnId(),
        state.latestEventType(), state.latestEventTimestamp(), state.turnState(),
        state.sessionState(), state.canSubmitTurn(), state.engineSubstate(), state.error()));
    }

    /**
     * Returns a copy of this session with an updated latest state.
     *
     * @param state the replacement latest state
     * @return the updated session
     */
    public ClaudeSession withLatestState(NestedRunnerState state)
    {
      requireThat(state, "state").isNotNull();
      String resolvedSessionId = sessionId;
      if (resolvedSessionId.isBlank())
        resolvedSessionId = state.sessionId();
      return new ClaudeSession(resolvedSessionId, model, effort, cwd, appendSystemPrompt, agent,
        turns, new NestedRunnerState(resolvedSessionId, state.currentTurnId(),
        state.latestEventType(), state.latestEventTimestamp(), state.turnState(),
        state.sessionState(), state.canSubmitTurn(), state.engineSubstate(), state.error()));
    }

    /**
     * Aggregates all turns into one-shot parsed output.
     *
     * @return the aggregated parsed output
     */
    public ParsedOutput toParsedOutput()
    {
      List<String> texts = new ArrayList<>();
      List<String> toolUses = new ArrayList<>();
      List<String> writeContents = new ArrayList<>();
      List<TurnOutput> parsedTurns = new ArrayList<>();
      for (ClaudeSessionTurn turn : turns)
      {
        texts.addAll(turn.assistantTexts());
        toolUses.addAll(turn.toolUses());
        writeContents.addAll(turn.writeContents());
        parsedTurns.add(new TurnOutput(turn.assistantTexts(), turn.toolUses(),
          turn.writeContents()));
      }
      return new ParsedOutput(List.copyOf(texts), List.copyOf(toolUses), List.copyOf(writeContents),
        List.copyOf(parsedTurns), sessionId);
    }
  }

  /**
   * A direct managed Claude session for turn-by-turn interaction.
   * <p>
   * This handle is the live owner of any resumable state. Successful turns may persist resumable
   * session metadata while the handle remains open, but {@link #close()} is destructive: it
   * finalizes the handle state and removes the persisted session file.
   */
  public final class ManagedSession implements AutoCloseable
  {
    private final Path sessionFile;
    private final Path cwd;
    private volatile ClaudeSession session;
    private volatile NestedRunnerState latestState;
    private boolean closed;
    private boolean invalidated;

    private ManagedSession(Path sessionFile, Path cwd, ClaudeSession session)
    {
      this.sessionFile = sessionFile;
      this.cwd = cwd;
      this.session = session;
      this.latestState = session.latestState();
    }

    /**
     * Returns the latest known state for the managed session.
     *
     * @return the latest known state
     */
    public NestedRunnerState latestState()
    {
      return latestState;
    }

    /**
     * Returns the current persisted or in-memory session snapshot.
     *
     * @return the current session snapshot
     */
    public ClaudeSession snapshot()
    {
      return session;
    }

    /**
     * Submits a turn using the standard Claude runner command for this session.
     * <p>
     * Equivalent to {@code submitTurn(prompt, _ -> {})}.
     *
     * @param prompt the prompt to submit
     * @return the process result
     * @throws IOException if session persistence fails
     */
    public ProcessResult submitTurn(String prompt) throws IOException
    {
      return submitTurn(prompt, _ -> {});
    }

    /**
     * Submits a turn using the standard Claude runner command for this session while streaming
     * state updates to the caller.
     *
     * @param prompt        the prompt to submit
     * @param eventListener receives state updates during execution; listeners must return
     *                      promptly and should honor interruption
     * @return the process result
     * @throws IOException if session persistence fails
     */
    public ProcessResult submitTurn(String prompt, Consumer<NestedRunnerEvent> eventListener)
      throws IOException
    {
      requireOpen();
      requireThat(prompt, "prompt").isNotNull();
      String resumeSessionId = "";
      if (!session.sessionId().isBlank() && !session.turns().isEmpty())
        resumeSessionId = session.sessionId();
      List<String> command = buildCommand(session.model(), session.effort(),
        session.appendSystemPrompt(), session.agent(), resumeSessionId);
      String input = buildInput(List.of(), List.of(prompt), List.of());
      return submitTurn(prompt, command, input, eventListener);
    }

    /**
     * Submits a turn using a caller-supplied command and input payload.
     * <p>
     * This is primarily intended for tests and advanced callers that need to drive a custom engine
     * shim while still using CAT-managed session persistence and state updates.
     *
     * @param prompt        the prompt to persist for this turn
     * @param command       the command to execute
     * @param input         the stream-json input to send
     * @param eventListener receives state updates during execution; listeners must return
     *                      promptly and should honor interruption
     * @return the process result
     * @throws IOException if session persistence fails
     */
    public ProcessResult submitTurn(String prompt, List<String> command, String input,
      Consumer<NestedRunnerEvent> eventListener) throws IOException
    {
      requireOpen();
      requireThat(prompt, "prompt").isNotNull();
      requireThat(command, "command").isNotNull();
      requireThat(input, "input").isNotNull();
      requireThat(eventListener, "eventListener").isNotNull();
      ProcessResult result = executeProcess(command, input, cwd, true, event ->
      {
        latestState = event.state();
        eventListener.accept(event);
      });
      ClaudeSession updatedLatestState = session.withLatestState(result.state());
      if (!result.error().isEmpty() || !reachedExpectedBoundary(result.state(), true))
      {
        session = updatedLatestState;
        latestState = session.latestState();
        invalidated = true;
        if (sessionFile != null)
          closeSession(sessionFile, cwd);
      }
      else
      {
        ClaudeSession updatedSession = session.appendTurn(prompt, result.parsed(), result.state());
        try
        {
          if (sessionFile != null)
            saveSession(sessionFile, updatedSession);
          session = updatedSession;
        }
        catch (IOException e)
        {
          latestState = new NestedRunnerState(result.state().sessionId(),
            result.state().currentTurnId(), result.state().latestEventType(),
            result.state().latestEventTimestamp(), result.state().turnState(),
            NestedRunnerSessionState.ERROR, false, result.state().engineSubstate(),
            e.getMessage());
          session = session.withLatestState(latestState);
          invalidated = true;
          if (sessionFile != null)
          {
            try
            {
              closeSession(sessionFile, cwd);
            }
            catch (IOException closeError)
            {
              e.addSuppressed(closeError);
            }
          }
          throw e;
        }
        latestState = session.latestState();
      }
      return result;
    }

    /**
     * Closes the managed session handle.
     * <p>
     * This operation is terminal. If the session is still valid, the in-memory state is
     * finalized to {@code COMPLETED}; any persisted session file is then deleted. Use this when
     * the caller is done with the session, not to pause it for later reuse.
     *
     * @throws IOException if deleting the persisted session file fails
     */
    @Override
    public void close() throws IOException
    {
      if (closed)
        return;
      closed = true;
      if (!invalidated)
      {
        NestedRunnerState completedState = finalizeClosedState(session.latestState());
        session = session.withLatestState(completedState);
        latestState = completedState;
      }
      if (sessionFile != null)
        closeSession(sessionFile, cwd);
    }

    /**
     * Ensures the managed session can still accept more turns.
     */
    private void requireOpen()
    {
      if (closed)
        throw new IllegalStateException("Session is already closed");
      if (invalidated)
        throw new IllegalStateException("Session is no longer resumable after the last turn");
    }

    /**
     * Converts a healthy managed-session state into terminal completion on close.
     *
     * @param state the latest session state
     * @return the finalized state
     */
    private NestedRunnerState finalizeClosedState(NestedRunnerState state)
    {
      if (state.sessionState() == NestedRunnerSessionState.ERROR ||
        state.sessionState() == NestedRunnerSessionState.TIMEOUT ||
        state.sessionState() == NestedRunnerSessionState.COMPLETED)
      {
        return state;
      }
      return new NestedRunnerState(state.sessionId(), state.currentTurnId(),
        state.latestEventType(), state.latestEventTimestamp(), state.turnState(),
        NestedRunnerSessionState.COMPLETED, false, state.engineSubstate(), state.error());
    }
  }
}
