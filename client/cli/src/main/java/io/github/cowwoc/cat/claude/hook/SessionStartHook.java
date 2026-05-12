/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.CheckDataMigration;
import io.github.cowwoc.cat.agent.InjectCriticalThinking;
import io.github.cowwoc.cat.agent.InjectMainAgentRules;
import io.github.cowwoc.cat.agent.SessionStartDispatcher;
import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.claude.hook.session.CheckRetrospectiveDue;
import io.github.cowwoc.cat.claude.hook.session.CheckUpdateAvailable;
import io.github.cowwoc.cat.claude.hook.session.EchoSessionId;
import io.github.cowwoc.cat.claude.hook.session.InjectEnv;
import io.github.cowwoc.cat.claude.hook.session.InjectSkillListing;
import io.github.cowwoc.cat.claude.hook.session.WarnUnknownTerminal;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * SessionStart hook dispatcher.
 * <p>
 * Consolidates all session start handlers into a single Java dispatcher. Each handler contributes
 * additional context for Claude and/or stderr messages for the user. The combined additional context from
 * all handlers is output as a single hookSpecificOutput JSON response.
 */
public final class SessionStartHook implements HookHandler
{
  private final List<SessionStartHandler> handlers;
  private final ClaudeHook scope;

  /**
   * Creates a new SessionStartHook with the default handler list.
   *
   * @param scope the hook scope providing environment configuration
   * @param envFile the path to the CLAUDE_ENV_FILE
   * @throws NullPointerException if {@code scope} or {@code envFile} are null
   */
  public SessionStartHook(ClaudeHook scope, Path envFile)
  {
    this(scope, List.of(
      new CheckDataMigration(scope),
      new CheckUpdateAvailable(scope),
      new WarnUnknownTerminal(scope),
      new EchoSessionId(scope),
      new CheckRetrospectiveDue(scope),
      new InjectMainAgentRules(scope),
      new InjectSkillListing(scope),
      new InjectCriticalThinking(),
      new InjectEnv(scope, envFile)));
  }

  /**
   * Creates a new SessionStartHook with custom handlers (for testing).
   *
   * @param scope    the hook scope providing environment configuration
   * @param handlers the handlers to run
   * @throws NullPointerException if {@code scope} or {@code handlers} are null
   */
  public SessionStartHook(ClaudeHook scope, List<SessionStartHandler> handlers)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(handlers, "handlers").isNotNull();
    this.scope = scope;
    this.handlers = handlers;
  }

  /**
   * Entry point for the session start hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    String envFileValue = System.getenv("CLAUDE_ENV_FILE");
    if (envFileValue == null || envFileValue.isBlank())
      throw new AssertionError("CLAUDE_ENV_FILE is not set");
    Path envFile = Path.of(envFileValue);
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, envFile, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(SessionStartHook.class).error("Failed to create JVM scope", e);
      System.err.println("Hook failed: " + e.getMessage());
    }
  }

  /**
   * Testable entry point with injectable I/O.
   *
   * @param scope the hook scope
   * @param envFile the path to the Claude environment file
   * @param args command line arguments (unused)
   * @param in input stream (unused)
   * @param out output stream for writing JSON response
   * @throws NullPointerException if any argument is null
   */
  public static void run(ClaudeHook scope, Path envFile, String[] args, InputStream in, PrintStream out)
  {
    HookRunner.execute(hookScope -> new SessionStartHook(hookScope, envFile), scope, out);
  }

  /**
   * Processes hook data by running all session start handlers and combining their output.
   *
   * @return the hook result containing JSON output and warnings
   */
  @Override
  public HookResult run()
  {
    String sessionId = scope.getSessionId();
    if (sessionId.isBlank())
    {
      throw new IllegalArgumentException(
        "session_id is blank. SessionStart hook requires a valid session ID.");
    }

    SessionStartDispatcher.Result result = SessionStartDispatcher.run(handlers);
    String combinedContext = result.additionalContext();
    String jsonOutput;
    if (combinedContext.isBlank())
      jsonOutput = scope.empty();
    else
      jsonOutput = scope.additionalContext("SessionStart", combinedContext);

    return new HookResult(jsonOutput, result.warnings());
  }
}
