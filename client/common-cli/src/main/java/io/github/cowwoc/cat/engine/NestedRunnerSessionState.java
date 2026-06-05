/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.engine;

/**
 * High-level lifecycle states for a nested engine session.
 */
public enum NestedRunnerSessionState
{
  /**
   * The engine has not yet emitted enough evidence to classify the session lifecycle.
   */
  UNKNOWN,
  /**
   * The engine is still processing the current turn.
   */
  WORKING,
  /**
   * The current turn is complete and the caller may submit another turn.
   */
  WAITING_FOR_NEXT_REQUEST,
  /**
   * The session has been closed cleanly and will not accept more turns.
   */
  COMPLETED,
  /**
   * The engine timed out before completing the current turn.
   */
  TIMEOUT,
  /**
   * The engine failed before reaching a clean turn/session boundary.
   */
  ERROR
}
