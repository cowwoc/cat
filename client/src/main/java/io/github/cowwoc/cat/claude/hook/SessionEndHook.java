/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.session.SessionEndHandler;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified SessionEnd hook for CAT.
 * <p>
 * TRIGGER: SessionEnd
 * <p>
 * Handles session cleanup operations:
 * <ul>
 *   <li>Stale session work directory removal from
 *     {@code {claudeProjectDir}/.cat/work/sessions/} for sessions whose corresponding Claude
 *     session directory no longer exists
 *   </li>
 * </ul>
 */
public final class SessionEndHook implements HookHandler
{
  private final ClaudeHook scope;

  /**
   * Creates a new SessionEndHook instance.
   *
   * @param scope the hook scope
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionEndHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Entry point for the session end hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(SessionEndHook.class).error("Failed to create JVM scope", e);
      System.err.println("Hook failed: " + e.getMessage());
    }
  }

  /**
   * Testable entry point with injectable I/O.
   *
   * @param scope the hook scope
   * @param args command line arguments (unused)
   * @param in input stream (unused)
   * @param out output stream for writing JSON response
   * @throws NullPointerException if any argument is null
   */
  public static void run(ClaudeHook scope, String[] args, InputStream in, PrintStream out)
  {
    HookRunner.execute(SessionEndHook::new, scope, out);
  }

  /**
   * Processes hook data and returns the result with any warnings.
   *
   * @param scope the hook scope providing input data and output building
   * @return the hook result containing JSON output and warnings
   */
  @Override
  public HookResult run(ClaudeHook scope)
  {
    String sessionId = this.scope.getSessionId();
    List<String> messages = new ArrayList<>();

    new SessionEndHandler(this.scope).clean(sessionId);

    return new HookResult(this.scope.empty(), messages);
  }
}
