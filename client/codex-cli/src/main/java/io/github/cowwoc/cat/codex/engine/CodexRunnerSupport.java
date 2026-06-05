/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AgentPluginScope;
import io.github.cowwoc.cat.engine.NestedRunnerEvent;
import io.github.cowwoc.cat.engine.NestedRunnerSessionState;
import io.github.cowwoc.cat.engine.NestedRunnerState;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CLI support wrapper around {@link CodexRunner}.
 */
public final class CodexRunnerSupport
{
  private final CodexRunner delegate;

  /**
   * Creates a new runner.
   * <p>
   * Equivalent to {@code new CodexRunnerSupport(scope, Duration.ofMinutes(10))}.
   *
   * @param scope the scope providing the JSON mapper
   */
  public CodexRunnerSupport(AgentPluginScope scope)
  {
    this(scope, Duration.ofMinutes(10));
  }

  /**
   * Creates a new runner.
   * <p>
   * Equivalent to {@code new CodexRunnerSupport(scope, timeout, System.getenv())}.
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
   * Builds a Codex resume command for an existing session.
   *
   * @param sessionId             the native Codex session id
   * @param model                 the model to use
   * @param effort                the reasoning effort level
   * @param cwd                   the working directory for Codex
   * @param lastMessageOutputPath the file that receives the final assistant message
   * @return the command as a list of strings
   */
  public List<String> buildResumeCommand(String sessionId, String model, String effort, Path cwd,
    Path lastMessageOutputPath)
  {
    return delegate.buildResumeCommand(sessionId, model, effort, cwd, lastMessageOutputPath);
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
    return new ProcessResult(result.parsed(), result.state(), result.elapsed(), result.error(),
      result.exitCode());
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
    return new ProcessResult(result.parsed(), result.state(), result.elapsed(), result.error(),
      result.exitCode());
  }

  /**
   * Executes the Codex CLI process with the given prompt, optionally preserving resumable state.
   *
   * @param command                the command to execute
   * @param prompt                 the prompt to send to standard input
   * @param cwd                    the working directory
   * @param lastMessageOutputPath  the file that receives the final assistant message
   * @param jsonlOutputPath        optional file that receives the raw JSONL stream
   * @param preserveResumableState whether to preserve a resumable waiting boundary
   * @return the process result
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd,
    Path lastMessageOutputPath, Path jsonlOutputPath, boolean preserveResumableState)
  {
    CodexRunner.ProcessResult result = delegate.executeProcess(command, prompt, cwd,
      lastMessageOutputPath, jsonlOutputPath, preserveResumableState);
    return new ProcessResult(result.parsed(), result.state(), result.elapsed(), result.error(),
      result.exitCode());
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
   * @param preserveResumableState whether to preserve a resumable waiting boundary
   * @param eventListener          receives state snapshots as relevant engine events arrive;
   *                               listeners must return promptly and should honor interruption
   * @return the process result
   */
  public ProcessResult executeProcess(List<String> command, String prompt, Path cwd,
    Path lastMessageOutputPath, Path jsonlOutputPath, boolean preserveResumableState,
    Consumer<NestedRunnerEvent> eventListener)
  {
    CodexRunner.ProcessResult result = delegate.executeProcess(command, prompt, cwd,
      lastMessageOutputPath, jsonlOutputPath, preserveResumableState, eventListener);
    return new ProcessResult(result.parsed(), result.state(), result.elapsed(), result.error(),
      result.exitCode());
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
   * Loads a managed Codex session from disk.
   *
   * @param sessionFile the session file path
   * @param model       the expected model id
   * @param effort      the expected effort level
   * @param cwd         the working directory boundary
   * @return the loaded session, or an empty session if the file does not exist
   * @throws IOException if the session cannot be read
   */
  public CodexRunner.CodexSession loadSession(Path sessionFile, String model, String effort, Path cwd)
    throws IOException
  {
    return delegate.loadSession(sessionFile, model, effort, cwd);
  }

  /**
   * Saves a managed Codex session to disk.
   *
   * @param sessionFile the destination file
   * @param session     the session snapshot
   * @throws IOException if the session cannot be written
   */
  public void saveSession(Path sessionFile, CodexRunner.CodexSession session) throws IOException
  {
    delegate.saveSession(sessionFile, session);
  }

  /**
   * Removes a persisted managed session.
   *
   * @param sessionFile the session file to remove
   * @param cwd         the working directory boundary
   * @throws IOException if deletion fails
   */
  public void closeSession(Path sessionFile, Path cwd) throws IOException
  {
    delegate.closeSession(sessionFile, cwd);
  }

  /**
   * Starts a managed Codex session for direct turn-by-turn interaction.
   * <p>
   * Equivalent to
   * {@code startSession(sessionFile, model, effort, cwd, lastMessageOutputPath, null)}.
   * <p>
   * The returned handle owns the resumable session lifecycle. Successful turns may persist a
   * resumable snapshot to {@code sessionFile}, but calling {@link ManagedSession#close()} is
   * terminal: it finalizes the in-memory handle and deletes the persisted session file.
   * Callers that need to resume later must do so before closing the handle.
   *
   * @param sessionFile            the persisted session file, or {@code null} for in-memory only
   * @param model                  the model to use
   * @param effort                 the reasoning effort level
   * @param cwd                    the working directory boundary
   * @param lastMessageOutputPath  the file that receives the final assistant message
   * @return the managed session handle
   * @throws IOException if the session cannot be loaded
   */
  public ManagedSession startSession(Path sessionFile, String model, String effort, Path cwd,
    Path lastMessageOutputPath) throws IOException
  {
    return startSession(sessionFile, model, effort, cwd, lastMessageOutputPath, null);
  }

  /**
   * Starts a managed Codex session for direct turn-by-turn interaction.
   * <p>
   * The returned handle owns the resumable session lifecycle. Successful turns may persist a
   * resumable snapshot to {@code sessionFile}, but calling {@link ManagedSession#close()} is
   * terminal: it finalizes the in-memory handle and deletes the persisted session file.
   * Callers that need to resume later must do so before closing the handle.
   *
   * @param sessionFile            the persisted session file, or {@code null} for in-memory only
   * @param model                  the model to use
   * @param effort                 the reasoning effort level
   * @param cwd                    the working directory boundary
   * @param lastMessageOutputPath  the file that receives the final assistant message
   * @param jsonlOutputPath        optional file that receives the raw JSONL stream
   * @return the managed session handle
   * @throws IOException if the session cannot be loaded
   */
  public ManagedSession startSession(Path sessionFile, String model, String effort, Path cwd,
    Path lastMessageOutputPath, Path jsonlOutputPath) throws IOException
  {
    requireThat(model, "model").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();
    requireThat(lastMessageOutputPath, "lastMessageOutputPath").isNotNull();
    CodexRunner.CodexSession session = delegate.loadSession(sessionFile, model, effort, cwd);
    return new ManagedSession(sessionFile, cwd, lastMessageOutputPath, jsonlOutputPath, session);
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
    return CodexRunner.run(args, scope, out);
  }

  /**
   * A direct managed Codex session for turn-by-turn interaction.
   * <p>
   * This handle is the live owner of any resumable state. Successful turns may persist resumable
   * session metadata while the handle remains open, but {@link #close()} is destructive: it
   * finalizes the handle state and removes the persisted session file.
   */
  public final class ManagedSession implements AutoCloseable
  {
    private final Path sessionFile;
    private final Path cwd;
    private final Path lastMessageOutputPath;
    private final Path jsonlOutputPath;
    private volatile CodexRunner.CodexSession session;
    private volatile NestedRunnerState latestState;
    private boolean closed;
    private boolean invalidated;

    private ManagedSession(Path sessionFile, Path cwd, Path lastMessageOutputPath,
      Path jsonlOutputPath, CodexRunner.CodexSession session)
    {
      this.sessionFile = sessionFile;
      this.cwd = cwd;
      this.lastMessageOutputPath = lastMessageOutputPath;
      this.jsonlOutputPath = jsonlOutputPath;
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
    public CodexRunner.CodexSession snapshot()
    {
      return session;
    }

    /**
     * Submits a turn using the standard Codex runner command for this session.
     *
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
     * Submits a turn using the standard Codex runner command for this session while streaming
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
      String sessionId = session.sessionId();
      List<String> command;
      if (!sessionId.isBlank() && !session.turns().isEmpty())
      {
        command = delegate.buildResumeCommand(sessionId, session.model(), session.effort(), cwd,
          lastMessageOutputPath);
      }
      else
      {
        command = delegate.buildCommand(session.model(), session.effort(), cwd,
          lastMessageOutputPath);
      }
      return submitTurn(prompt, command, eventListener);
    }

    /**
     * Submits a turn using a caller-supplied command.
     * <p>
     * This is primarily intended for tests and advanced callers that need to drive a custom engine
     * shim while still using CAT-managed session persistence and state updates.
     *
     * @param prompt        the prompt to persist for this turn
     * @param command       the command to execute
     * @param eventListener receives state updates during execution; listeners must return
     *                      promptly and should honor interruption
     * @return the process result
     * @throws IOException if session persistence fails
     */
    public ProcessResult submitTurn(String prompt, List<String> command,
      Consumer<NestedRunnerEvent> eventListener) throws IOException
    {
      requireOpen();
      requireThat(prompt, "prompt").isNotNull();
      requireThat(command, "command").isNotNull();
      requireThat(eventListener, "eventListener").isNotNull();
      CodexRunner.ProcessResult result = delegate.executeProcess(command, prompt, cwd,
        lastMessageOutputPath, jsonlOutputPath, true, event ->
        {
          latestState = event.state();
          eventListener.accept(event);
        });
      CodexRunner.CodexSession updatedLatestState = session.withLatestState(result.state());
      if (!result.error().isEmpty() || !CodexRunner.reachedExpectedBoundary(result.state(), true))
      {
        session = updatedLatestState;
        latestState = session.latestState();
        invalidated = true;
        if (sessionFile != null)
          delegate.closeSession(sessionFile, cwd);
      }
      else
      {
        CodexRunner.CodexSession updatedSession = session.appendTurn(prompt, result.parsed(),
          result.state());
        try
        {
          if (sessionFile != null)
            delegate.saveSession(sessionFile, updatedSession);
          session = updatedSession;
        }
        catch (IOException e)
        {
          latestState = new NestedRunnerState(result.state().sessionId(),
            result.state().currentTurnId(), result.state().latestEventType(),
            result.state().latestEventTimestamp(), result.state().turnState(),
            NestedRunnerSessionState.ERROR, false, result.state().engineSubstate(), e.getMessage());
          session = session.withLatestState(latestState);
          invalidated = true;
          if (sessionFile != null)
          {
            try
            {
              delegate.closeSession(sessionFile, cwd);
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
      return new ProcessResult(result.parsed(), result.state(), result.elapsed(), result.error(),
        result.exitCode());
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
        delegate.closeSession(sessionFile, cwd);
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

  /**
   * Process execution result.
   *
   * @param parsed   parsed JSONL output
   * @param state    latest nested runner state
   * @param elapsed  process duration
   * @param error    non-empty on failure
   * @param exitCode nested process exit code
   */
  public record ProcessResult(CodexRunner.ParsedOutput parsed, NestedRunnerState state,
                              Duration elapsed, String error, int exitCode)
  {
  }
}
