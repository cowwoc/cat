/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.ClaudeHook;

/**
 * Injects the session ID into Claude's context.
 * <p>
 * This handler fires on every SessionStart (including after compaction), ensuring
 * the session ID is always available in context.
 */
public final class EchoSessionId implements SessionStartHandler
{
  /**
   * Creates a new EchoSessionId handler.
   */
  public EchoSessionId()
  {
  }

  /**
   * Returns the session ID as additional context.
   *
   * @return a result containing "Session ID: {id}" as context, or empty if no session ID
   */
  @Override
  public Result handle(ClaudeHook scope)
  {
    return Result.context("Session ID: " + scope.getSessionId());
  }
}
