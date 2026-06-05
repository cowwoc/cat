/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Snapshot of the latest known nested runner state.
 *
 * @param sessionId            the engine session identifier, or empty string if unavailable
 * @param currentTurnId        the current turn identifier when the engine exposes one, or empty string otherwise
 * @param latestEventType      the event type that most recently updated this state
 * @param latestEventTimestamp the event timestamp, or empty string if the engine did not expose one
 * @param turnState            the current turn state
 * @param sessionState         the current session state
 * @param canSubmitTurn        whether the caller may submit the next turn
 * @param engineSubstate       optional engine-specific substate grounded by a real event
 *                             (for example, {@link NestedRunnerEngineSubstates#WAITING_FOR_MODEL}
 *                             or
 *                             {@link NestedRunnerEngineSubstates#WAITING_FOR_TOOL_RESULT});
 *                             callers must treat unknown non-empty values as opaque engine hints
 * @param error                the latest error text, or empty string if none
 */
public record NestedRunnerState(String sessionId, String currentTurnId, String latestEventType,
  String latestEventTimestamp, NestedRunnerTurnState turnState,
  NestedRunnerSessionState sessionState, boolean canSubmitTurn, String engineSubstate, String error)
{
  /**
   * Creates a new state snapshot.
   */
  public NestedRunnerState
  {
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(currentTurnId, "currentTurnId").isNotNull();
    requireThat(latestEventType, "latestEventType").isNotNull();
    requireThat(latestEventTimestamp, "latestEventTimestamp").isNotNull();
    requireThat(turnState, "turnState").isNotNull();
    requireThat(sessionState, "sessionState").isNotNull();
    requireThat(engineSubstate, "engineSubstate").isNotNull();
    requireThat(error, "error").isNotNull();
  }
}
