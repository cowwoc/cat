/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;

/**
 * Injects the session ID into Claude's context.
 * <p>
 * This handler fires on every SessionStart (including after compaction), ensuring
 * the session ID is always available in context.
 */
public final class EchoSessionId implements SessionStartHandler
{
  private final ClaudeHook scope;

  /**
   * Creates a new EchoSessionId handler.
   *
   * @param scope the hook scope
   * @throws NullPointerException if scope is null
   */
  public EchoSessionId(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Returns the session ID as additional context.
   *
   * @return a result containing "Session ID: {id}" as context, or empty if no session ID
   */
  @Override
  public Result handle()
  {
    return Result.context("Session ID: " + scope.getSessionId());
  }
}
