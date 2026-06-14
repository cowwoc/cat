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
import io.github.cowwoc.cat.engine.NestedRunnerEvent;
import io.github.cowwoc.cat.engine.NestedRunnerSessionState;
import io.github.cowwoc.cat.engine.NestedRunnerState;
import io.github.cowwoc.cat.engine.NestedRunnerTurnState;
import io.github.cowwoc.cat.codex.tool.MainCodexTool;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
  private static final List<String> EFFORT_LEVELS = List.of("low", "medium", "high", "xhigh");
  /**
   * Default timeout for the Codex CLI process.
   */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
  private static final Duration WAIT_POLL = Duration.ofMillis(50);
  private static final String ADDITIONAL_WORKDIRS_ENV = "ADDITIONAL_WORKDIRS";
  private final AgentPluginScope scope;
  private final CodexSessionOutputParser sessionOutputParser;
  private final Duration timeout;
  private final Map<String, String> environment;

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
    this(scope, timeout, commandPolicyEnvironment(scope));
  }

  /**
   * Creates a new Codex process launcher.
   *
   * @param scope       the scope providing the JSON mapper
   * @param timeout     the process timeout
   * @param environment the environment used for command policy decisions
   * @throws NullPointerException     if {@code scope}, {@code timeout}, or {@code environment} are null
   * @throws IllegalArgumentException if {@code timeout} is not positive
   */
  public CodexRunner(AgentPluginScope scope, Duration timeout, Map<String, String> environment)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(timeout, "timeout").isNotNull();
    requireThat(environment, "environment").isNotNull();
    if (!timeout.isPositive())
      throw new IllegalArgumentException("timeout must be positive");
    this.scope = scope;
    this.sessionOutputParser = new CodexSessionOutputParser(scope.getJsonMapper());
    this.timeout = timeout;
    this.environment = Map.copyOf(environment);
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
    appendExecutionPolicy(command, cwd);
    command.add("--model");
    command.add(model);
    command.add("-c");
    command.add("model_reasoning_effort=\"" + effort + "\"");
    command.add("-");
    return command;
  }

  /**
   * Builds a Codex resume command for an existing session.
   *
   * @param sessionId             the session ID to resume
   * @param model                 the model to use
   * @param effort                the reasoning effort level
   * @param cwd                   the working directory for Codex
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @return the command as a list of strings
   */
  public List<String> buildResumeCommand(String sessionId, String model, String effort, Path cwd,
    Path lastMessageOutputPath)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    validateResumeSessionId(sessionId);
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
    command.add("resume");
    command.add("--json");
    command.add("--output-last-message");
    command.add(lastMessageOutputPath.toString());
    appendExecutionPolicy(command, cwd);
    command.add("--model");
    command.add(model);
    command.add("-c");
    command.add("model_reasoning_effort=\"" + effort + "\"");
    command.add("--");
    command.add(sessionId);
    command.add("-");
    return command;
  }

  /**
   * Appends cwd and sandbox policy flags to a Codex CLI command.
   *
   * @param command the command under construction
   * @param cwd the execution working directory
   */
  private void appendExecutionPolicy(List<String> command, Path cwd)
  {
    requireThat(command, "command").isNotNull();
    requireThat(cwd, "cwd").isNotNull();
    command.add("--cd");
    command.add(cwd.toString());
    appendSharedWorkdirs(command);
    if (CodexSandboxPolicy.shouldInheritYoloMode(environment))
    {
      command.add(CodexSandboxPolicy.YOLO_FLAG);
    }
    else if (CodexSandboxPolicy.isExternallySandboxedEngine(environment))
    {
      command.add("--sandbox");
      command.add(CodexSandboxPolicy.NESTED_SANDBOX_MODE);
    }
  }

  /**
   * Appends additional work directories that nested Codex runs may access alongside their primary cwd.
   *
   * @param command the command under construction
   */
  private void appendSharedWorkdirs(List<String> command)
  {
    requireThat(command, "command").isNotNull();
    String raw = environment.getOrDefault(ADDITIONAL_WORKDIRS_ENV, "").trim();
    if (raw.isEmpty())
      return;
    Set<String> uniquePaths = new LinkedHashSet<>();
    for (String token : raw.split(":"))
    {
      String path = token.trim();
      if (!path.isEmpty())
        uniquePaths.add(path);
    }
    for (String path : uniquePaths)
    {
      command.add("--add-dir");
      command.add(path);
    }
  }

  /**
   * Returns the command-policy environment exposed by production Codex scopes.
   *
   * @param scope the scope that may provide command-policy environment values
   * @return the environment values used for command policy decisions
   * @throws NullPointerException if {@code scope} is null
   */
  private static Map<String, String> commandPolicyEnvironment(AgentPluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    if (scope instanceof MainCodexTool mainCodexTool)
      return mainCodexTool.getCommandEnvironment();
    return Map.of();
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
    return executeProcess(command, prompt, cwd, lastMessageOutputPath, null, false, _ -> {});
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
    return executeProcess(command, prompt, cwd, lastMessageOutputPath, jsonlOutputPath, false,
      _ -> {});
  }

  /**
   * Executes the Codex CLI process with the given prompt.
   *
   * @param command                the command to execute
   * @param prompt                 the prompt to send to standard input
   * @param cwd                    the working directory
   * @param lastMessageOutputPath  the file that receives the final assistant message
   * @param jsonlOutputPath        optional file that receives the raw JSONL stream
   * @param preserveResumableState whether to preserve a resumable waiting boundary instead of
   *                               converting it into terminal completion once the process exits
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if {@code command}, {@code prompt}, {@code cwd}, or
   *                              {@code lastMessageOutputPath} are null
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd,
    Path lastMessageOutputPath, Path jsonlOutputPath, boolean preserveResumableState)
  {
    return executeProcess(command, prompt, cwd, lastMessageOutputPath, jsonlOutputPath,
      preserveResumableState, _ -> {});
  }

  /**
   * Executes the Codex CLI process with the given prompt while streaming state updates to a
   * listener.
   *
   * @param command                the command to execute
   * @param prompt                 the prompt to send to standard input
   * @param cwd                    the working directory
   * @param lastMessageOutputPath  the file that receives the final assistant message
   * @param jsonlOutputPath        optional file that receives the raw JSONL stream
   * @param preserveResumableState whether to preserve a resumable waiting boundary instead of
   *                               converting it into terminal completion once the process exits
   * @param eventListener          receives state snapshots as relevant engine events arrive;
   *                               listeners must return promptly and should honor interruption,
   *                               because timeout cleanup can only interrupt callback code, not
   *                               force-stop arbitrary blocking user logic
   * @return the process result with parsed output, elapsed time, and error
   * @throws NullPointerException if {@code command}, {@code prompt}, {@code cwd},
   *                              {@code lastMessageOutputPath}, or {@code eventListener} are null
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd,
    Path lastMessageOutputPath, Path jsonlOutputPath, boolean preserveResumableState,
    Consumer<NestedRunnerEvent> eventListener)
  {
    requireThat(command, "command").isNotNull();
    requireThat(prompt, "prompt").isNotNull();
    requireThat(cwd, "cwd").isNotNull();
    requireThat(lastMessageOutputPath, "lastMessageOutputPath").isNotNull();
    requireThat(eventListener, "eventListener").isNotNull();

    long startTimeNanos = System.nanoTime();
    ParsedOutput empty = new ParsedOutput(List.of(), List.of(), List.of(), List.of(), "");
    NestedRunnerState emptyState = new NestedRunnerState("", "", "", "",
      NestedRunnerTurnState.UNKNOWN, NestedRunnerSessionState.UNKNOWN, false, "", "");
    try
    {
      try (Process process = buildProcessBuilder(command, cwd).start())
      {
        RunningProcess running = startProcessIo(process, prompt, eventListener);
        long deadlineNanos = startTimeNanos + timeout.toNanos();
        ProcessResult earlyResult = awaitEarlyProcessResult(process, running, deadlineNanos,
          startTimeNanos, empty, emptyState);
        if (earlyResult != null)
          return earlyResult;
        return completeProcessResult(process, running, deadlineNanos, startTimeNanos, empty,
          emptyState, lastMessageOutputPath, jsonlOutputPath, preserveResumableState);
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startTimeNanos);
      return new ProcessResult(empty, emptyState, elapsed, e.getMessage(), -1);
    }
    catch (IOException e)
    {
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startTimeNanos);
      return new ProcessResult(empty, emptyState, elapsed, e.getMessage(), -1);
    }
  }

  /**
   * Starts the stdout reader and writes the prompt into the nested process.
   *
   * @param process the started nested process
   * @param prompt the prompt to write to standard input
   * @param eventListener receives streamed state updates
   * @return the running-process handles used by the lifecycle helpers
   * @throws IOException if writing the prompt fails
   */
  private RunningProcess startProcessIo(Process process, String prompt,
    Consumer<NestedRunnerEvent> eventListener) throws IOException
  {
    StreamParseResult[] resultHolder = new StreamParseResult[1];
    AtomicReference<RuntimeException> readerRuntimeError = new AtomicReference<>();
    Thread stdoutReader = Thread.ofVirtual().start(() ->
    {
      try
      {
        resultHolder[0] = readAndParseProcessOutput(process, eventListener);
      }
      catch (RuntimeException e)
      {
        readerRuntimeError.set(e);
      }
    });
    try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), UTF_8))
    {
      writer.write(prompt);
    }
    return new RunningProcess(stdoutReader, resultHolder, readerRuntimeError);
  }

  /**
   * Waits for the nested process to finish or fail before stdout join/parse finalization.
   *
   * @param process the nested process
   * @param running the running-process handles
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @param startTimeNanos the execution start time from {@link System#nanoTime()}
   * @param empty empty parsed output for failure cases
   * @param emptyState empty runner state for failure cases
   * @return an early failure result, or {@code null} if the process completed normally
   * @throws InterruptedException if interrupted while waiting
   */
  private ProcessResult awaitEarlyProcessResult(Process process, RunningProcess running,
    long deadlineNanos, long startTimeNanos, ParsedOutput empty, NestedRunnerState emptyState)
    throws InterruptedException
  {
    boolean completed = waitForProcessOrReaderFailure(process, running.readerRuntimeError(),
      deadlineNanos);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startTimeNanos);
    RuntimeException readerFailure = running.readerRuntimeError().get();
    if (readerFailure != null)
    {
      process.destroyForcibly();
      stopReaderThread(running.stdoutReader());
      return new ProcessResult(empty, emptyState, elapsed, readerFailure.getMessage(), -1);
    }
    if (completed)
      return null;
    process.destroyForcibly();
    stopReaderThread(running.stdoutReader());
    NestedRunnerState timeoutState = new NestedRunnerState("", "", "", "",
      NestedRunnerTurnState.TIMEOUT, NestedRunnerSessionState.TIMEOUT, false, "", "timeout");
    return new ProcessResult(empty, timeoutState, elapsed, "timeout", -1);
  }

  /**
   * Joins the stdout reader and builds the successful process result.
   *
   * @param process the completed nested process
   * @param running the running-process handles
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @param startTimeNanos the execution start time from {@link System#nanoTime()}
   * @param empty empty parsed output for failure cases
   * @param emptyState empty runner state for failure cases
   * @param lastMessageOutputPath the persisted final-assistant-message file
   * @param jsonlOutputPath optional JSONL output artifact
   * @param preserveResumableState whether to preserve resumable waiting state
   * @return the completed process result
   */
  private ProcessResult completeProcessResult(Process process, RunningProcess running,
    long deadlineNanos, long startTimeNanos, ParsedOutput empty, NestedRunnerState emptyState,
    Path lastMessageOutputPath, Path jsonlOutputPath, boolean preserveResumableState)
  {
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startTimeNanos);
    int exitCode = process.exitValue();
    ProcessResult joinFailure = joinReaderThread(running.stdoutReader(), deadlineNanos, process,
      elapsed, empty, emptyState);
    if (joinFailure != null)
      return joinFailure;
    RuntimeException readerFailure = running.readerRuntimeError().get();
    if (readerFailure != null)
      return new ProcessResult(empty, emptyState, elapsed, readerFailure.getMessage(), -1);
    return buildCompletedResult(running.resultHolder()[0], elapsed, exitCode, lastMessageOutputPath,
      jsonlOutputPath, preserveResumableState, empty);
  }

  /**
   * Joins the stdout reader within the remaining timeout budget.
   *
   * @param stdoutReader the stdout reader thread
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @param process the nested process
   * @param elapsed the elapsed execution time measured so far
   * @param empty empty parsed output for failure cases
   * @param emptyState empty runner state for failure cases
   * @return a failure result, or {@code null} if the join completed successfully
   */
  private ProcessResult joinReaderThread(Thread stdoutReader, long deadlineNanos, Process process,
    Duration elapsed, ParsedOutput empty, NestedRunnerState emptyState)
  {
    try
    {
      long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
      stdoutReader.join(Duration.ofNanos(remainingNanos).toMillis());
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      stopReaderThread(stdoutReader);
      return new ProcessResult(empty, emptyState, elapsed, e.getMessage(), -1);
    }
    if (!stdoutReader.isAlive())
      return null;
    process.destroyForcibly();
    stopReaderThread(stdoutReader);
    NestedRunnerState timeoutState = new NestedRunnerState("", "", "", "",
      NestedRunnerTurnState.TIMEOUT, NestedRunnerSessionState.TIMEOUT, false, "", "timeout");
    return new ProcessResult(empty, timeoutState, elapsed, "timeout", -1);
  }

  /**
   * Builds the final completed process result after process and reader success.
   *
   * @param parsedStream the raw stream plus parsed session output
   * @param elapsed the elapsed execution time
   * @param exitCode the nested process exit code
   * @param lastMessageOutputPath the persisted final-assistant-message file
   * @param jsonlOutputPath optional JSONL output artifact
   * @param preserveResumableState whether to preserve resumable waiting state
   * @param empty empty parsed output used when the stream contains no parseable output
   * @return the completed process result
   */
  private ProcessResult buildCompletedResult(StreamParseResult parsedStream, Duration elapsed,
    int exitCode, Path lastMessageOutputPath, Path jsonlOutputPath, boolean preserveResumableState,
    ParsedOutput empty)
  {
    try
    {
      if (jsonlOutputPath != null)
        Files.writeString(jsonlOutputPath, parsedStream.rawOutput(), UTF_8);
      ParsedSessionOutput parsedSession = parsedStream.parsedSession();
      ParsedOutput parsed = appendLastMessage(parsedSession.parsed(), lastMessageOutputPath);
      NestedRunnerState state = resolveCompletedState(parsed, parsedSession.state());
      if (parsed.turns().isEmpty() && parsed.texts().isEmpty())
      {
        String snippet = parsedStream.rawOutput().strip();
        if (snippet.length() > 500)
          snippet = snippet.substring(0, 500) + "...";
        return new ProcessResult(empty, state, elapsed,
          "codex produced no parseable output. jsonl=" + snippet, exitCode);
      }
      String error = "";
      if (exitCode != 0 && !isReadyForNextTurn(state))
        error = "codex exited with code " + exitCode;
      if (!preserveResumableState)
        state = finalizeCompletedState(state);
      return new ProcessResult(parsed, state, elapsed, error, exitCode);
    }
    catch (IOException e)
    {
      NestedRunnerState emptyState = new NestedRunnerState("", "", "", "",
        NestedRunnerTurnState.UNKNOWN, NestedRunnerSessionState.UNKNOWN, false, "", "");
      return new ProcessResult(empty, emptyState, elapsed, e.getMessage(), -1);
    }
  }

  /**
   * Resolves the final session id and state from parsed stream output.
   *
   * @param parsed the parsed output
   * @param parsedState the state derived directly from the JSONL stream
   * @return the completed runner state with a resolved session id
   */
  private static NestedRunnerState resolveCompletedState(ParsedOutput parsed,
    NestedRunnerState parsedState)
  {
    String resolvedSessionId = parsed.sessionId();
    if (resolvedSessionId.isBlank())
      resolvedSessionId = parsedState.sessionId();
    return new NestedRunnerState(resolvedSessionId, parsedState.currentTurnId(),
      parsedState.latestEventType(), parsedState.latestEventTimestamp(), parsedState.turnState(),
      parsedState.sessionState(), parsedState.canSubmitTurn(), parsedState.engineSubstate(),
      parsedState.error());
  }

  /**
   * Waits until the nested process exits, the stdout reader fails, or the deadline expires.
   *
   * @param process the nested process
   * @param readerRuntimeError captures reader-thread failures
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @return {@code true} if the process exited before the deadline; otherwise {@code false}
   * @throws InterruptedException if interrupted while waiting
   */
  private static boolean waitForProcessOrReaderFailure(Process process,
    AtomicReference<RuntimeException> readerRuntimeError, long deadlineNanos)
    throws InterruptedException
  {
    while (true)
    {
      if (readerRuntimeError.get() != null)
        return process.waitFor(0, TimeUnit.MILLISECONDS);
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0)
        return false;
      long waitMillis = Math.min(Duration.ofNanos(remainingNanos).toMillis(), WAIT_POLL.toMillis());
      if (waitMillis <= 0)
        waitMillis = 1;
      if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS))
        return true;
    }
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
   * Reads and parses the nested process output stream.
   *
   * @param process the nested process
   * @param eventListener receives streamed runner events
   * @return the raw stream plus parsed session output
   */
  private StreamParseResult readAndParseProcessOutput(Process process,
    Consumer<NestedRunnerEvent> eventListener)
  {
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), UTF_8)))
    {
      StringBuilder rawOutput = new StringBuilder();
      ParsedSessionOutput parsed = sessionOutputParser.parseSessionOutput(reader, eventListener,
        rawOutput);
      return new StreamParseResult(rawOutput.toString(), parsed);
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
      return sessionOutputParser.parseSessionOutput(reader, _ -> {}, null).parsed();
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
    return sessionOutputParser.parseSessionOutput(reader, _ -> {}, null).parsed();
  }

  /**
   * Parses Codex JSONL output and derives the latest session state.
   *
   * @param output the raw JSONL output
   * @return the parsed output and derived state
   */
  public ParsedSessionOutput parseSessionOutput(String output)
  {
    requireThat(output, "output").isNotNull();
    try (BufferedReader reader = new BufferedReader(new StringReader(output)))
    {
      return sessionOutputParser.parseSessionOutput(reader, _ -> {}, null);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Parses Codex JSONL output and derives the latest session state.
   *
   * @param reader the reader supplying JSONL lines
   * @return the parsed output and derived state
   * @throws IOException if reading from {@code reader} fails
   */
  public ParsedSessionOutput parseSessionOutput(BufferedReader reader) throws IOException
  {
    return sessionOutputParser.parseSessionOutput(reader, _ -> {}, null);
  }

  /**
   * Parses Codex JSONL output and derives the latest session state, streaming state updates as
   * relevant events arrive.
   *
   * @param reader        the reader supplying JSONL lines
   * @param eventListener receives state snapshots as relevant engine events arrive; listeners
   *                      must return promptly and should honor interruption
   * @return the parsed output and derived state
   * @throws IOException if reading from {@code reader} fails
   */
  public ParsedSessionOutput parseSessionOutput(BufferedReader reader,
    Consumer<NestedRunnerEvent> eventListener) throws IOException
  {
    return sessionOutputParser.parseSessionOutput(reader, eventListener, null);
  }

  /**
   * Appends a persisted final assistant message when it is not already present in parsed output.
   *
   * @param parsed the parsed stream output
   * @param lastMessageOutputPath the persisted last-message file
   * @return the parsed output, possibly with the final assistant message appended
   * @throws IOException if the last-message file cannot be read
   */
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
    return CodexRunnerCli.run(args, scope, out);
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (AgentPluginScope scope = new MainCodexTool())
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
   * @param state   the latest derived state
   * @param elapsed the elapsed time
   * @param error   the error message, or empty string if none
   * @param exitCode the process exit code, or {@code -1} if unavailable
   */
  public record ProcessResult(ParsedOutput parsed, NestedRunnerState state, Duration elapsed,
    String error, int exitCode)
  {
    /**
     * Creates a new process result.
     *
     * @param parsed  the parsed output
     * @param state   the latest derived state
     * @param elapsed the elapsed time
     * @param error   the error message, or empty string if none
     * @param exitCode the process exit code, or {@code -1} if unavailable
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
   * Raw process output paired with the parsed session view derived from it.
   *
   * @param rawOutput the raw process output
   * @param parsedSession the parsed session output
   */
  private record StreamParseResult(String rawOutput, ParsedSessionOutput parsedSession)
  {
  }

  /**
   * Running-process handles needed while the nested Codex process is executing.
   *
   * @param stdoutReader the stdout reader thread
   * @param resultHolder parsed stream results produced by the reader
   * @param readerRuntimeError uncaught reader-thread failures
   */
  private record RunningProcess(Thread stdoutReader, StreamParseResult[] resultHolder,
                                AtomicReference<RuntimeException> readerRuntimeError)
  {
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

  /**
   * Parsed output plus the latest derived runner state.
   *
   * @param parsed the parsed output
   * @param state  the latest derived runner state
   */
  public record ParsedSessionOutput(ParsedOutput parsed, NestedRunnerState state)
  {
    /**
     * Creates a parsed session output.
     *
     * @param parsed the parsed output
     * @param state  the latest derived state
     */
    public ParsedSessionOutput
    {
      requireThat(parsed, "parsed").isNotNull();
      requireThat(state, "state").isNotNull();
    }
  }

  /**
   * A persisted turn inside a managed Codex session.
   *
   * @param prompt         the submitted prompt
   * @param assistantTexts assistant text blocks emitted for this turn
   * @param toolUses       tools used during this turn
   * @param writeContents  write or patch contents emitted during this turn
   */
  public record CodexSessionTurn(String prompt, List<String> assistantTexts, List<String> toolUses,
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
    public CodexSessionTurn
    {
      requireThat(prompt, "prompt").isNotNull();
      requireThat(assistantTexts, "assistantTexts").isNotNull();
      requireThat(toolUses, "toolUses").isNotNull();
      requireThat(writeContents, "writeContents").isNotNull();
    }
  }

  /**
   * A managed Codex session persisted by CAT between turns.
   *
   * @param sessionId   the Codex session identifier, or empty string until the first turn resolves one
   * @param model       the model bound to this session
   * @param effort      the effort bound to this session
   * @param cwd         the working directory bound to this session
   * @param turns       the completed turns in order
   * @param latestState the latest derived runner state
   */
  public record CodexSession(String sessionId, String model, String effort, String cwd,
    List<CodexSessionTurn> turns, NestedRunnerState latestState)
  {
    /**
     * Creates a managed session snapshot.
     *
     * @param sessionId   the Codex session identifier
     * @param model       the model bound to this session
     * @param effort      the effort bound to this session
     * @param cwd         the working directory bound to this session
     * @param turns       the completed turns in order
     * @param latestState the latest derived runner state
     */
    public CodexSession
    {
      requireThat(sessionId, "sessionId").isNotNull();
      requireThat(model, "model").isNotNull();
      requireThat(effort, "effort").isNotNull();
      requireThat(cwd, "cwd").isNotNull();
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
    public CodexSession appendTurn(String prompt, ParsedOutput parsed, NestedRunnerState state)
    {
      requireThat(prompt, "prompt").isNotNull();
      requireThat(parsed, "parsed").isNotNull();
      requireThat(state, "state").isNotNull();
      List<CodexSessionTurn> updatedTurns = new ArrayList<>(turns);
      updatedTurns.add(new CodexSessionTurn(prompt, List.copyOf(parsed.texts()),
        List.copyOf(parsed.toolUses()), List.copyOf(parsed.writeContents())));
      String resolvedSessionId = parsed.sessionId();
      if (resolvedSessionId.isBlank())
        resolvedSessionId = sessionId;
      return new CodexSession(resolvedSessionId, model, effort, cwd, List.copyOf(updatedTurns),
        new NestedRunnerState(resolvedSessionId, state.currentTurnId(), state.latestEventType(),
          state.latestEventTimestamp(), state.turnState(), state.sessionState(),
          state.canSubmitTurn(), state.engineSubstate(), state.error()));
    }

    /**
     * Returns a copy of this session with an updated latest state.
     *
     * @param state the replacement latest state
     * @return the updated session
     */
    public CodexSession withLatestState(NestedRunnerState state)
    {
      requireThat(state, "state").isNotNull();
      String resolvedSessionId = sessionId;
      if (resolvedSessionId.isBlank())
        resolvedSessionId = state.sessionId();
      return new CodexSession(resolvedSessionId, model, effort, cwd, turns,
        new NestedRunnerState(resolvedSessionId, state.currentTurnId(), state.latestEventType(),
          state.latestEventTimestamp(), state.turnState(), state.sessionState(),
          state.canSubmitTurn(), state.engineSubstate(), state.error()));
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
      for (CodexSessionTurn turn : turns)
      {
        texts.addAll(turn.assistantTexts());
        toolUses.addAll(turn.toolUses());
        writeContents.addAll(turn.writeContents());
        parsedTurns.add(new TurnOutput(turn.assistantTexts(), turn.toolUses(), turn.writeContents()));
      }
      return new ParsedOutput(List.copyOf(texts), List.copyOf(toolUses), List.copyOf(writeContents),
        List.copyOf(parsedTurns), sessionId);
    }
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
  public CodexSession loadSession(Path sessionFile, String model, String effort, Path cwd) throws IOException
  {
    requireThat(model, "model").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();
    Path resolvedSessionFile = resolveSessionFile(sessionFile, cwd);
    if (resolvedSessionFile == null || Files.notExists(resolvedSessionFile))
    {
      return new CodexSession("", model, effort, normalizeCwd(cwd), List.of(),
        new NestedRunnerState("", "", "", "", NestedRunnerTurnState.UNKNOWN,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));
    }
    rejectSymlinkSessionFile(resolvedSessionFile);
    CodexSession session = scope.getJsonMapper().readValue(Files.readString(resolvedSessionFile),
      CodexSession.class);
    if (!session.model().equals(model) || !session.effort().equals(effort))
      throw new IllegalArgumentException("Session file model/effort does not match current request");
    if (!session.cwd().equals(normalizeCwd(cwd)))
      throw new IllegalArgumentException("Session file cwd does not match current request");
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
   * Resolves the CLI exit code for a Codex runner invocation.
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
  public void saveSession(Path sessionFile, CodexSession session) throws IOException
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
    try
    {
      Files.writeString(tempFile, scope.getJsonMapper().writeValueAsString(
        normalizeLoadedSession(session)), UTF_8);
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
   * Validates internal consistency of a persisted Codex session.
   *
   * @param session the loaded session
   */
  private static void validateLoadedSession(CodexSession session)
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
  private static CodexSession normalizeLoadedSession(CodexSession session)
  {
    if (session.sessionId().isBlank() || !session.latestState().sessionId().isBlank())
      return session;
    NestedRunnerState state = session.latestState();
    return new CodexSession(session.sessionId(), session.model(), session.effort(), session.cwd(),
      session.turns(), new NestedRunnerState(session.sessionId(), state.currentTurnId(),
      state.latestEventType(), state.latestEventTimestamp(), state.turnState(),
      state.sessionState(), state.canSubmitTurn(), state.engineSubstate(), state.error()));
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
   * Validates a native resume session id before it is passed to the Codex CLI.
   *
   * @param sessionId the native session id
   */
  private static void validateResumeSessionId(String sessionId)
  {
    if (sessionId.startsWith("-"))
      throw new IllegalArgumentException("Session id must not start with '-': " + sessionId);
  }
}
