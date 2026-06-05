/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.engine;

/**
 * The current nested-engine turn state.
 */
public enum NestedRunnerTurnState
{
  /**
   * The engine has not yet emitted enough evidence to classify the current turn.
   */
  UNKNOWN,
  /**
   * The engine is actively working on the turn.
   */
  WORKING,
  /**
   * The engine is waiting on model output to advance the turn.
   */
  WAITING_FOR_MODEL,
  /**
   * The engine emitted a tool request and is blocked on the tool result.
   */
  WAITING_FOR_TOOL_RESULT,
  /**
   * The current turn completed successfully.
   */
  COMPLETED,
  /**
   * The current turn timed out.
   */
  TIMEOUT,
  /**
   * The current turn failed.
   */
  ERROR
}
