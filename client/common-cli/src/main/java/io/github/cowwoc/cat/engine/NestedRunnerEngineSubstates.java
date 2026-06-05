/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.engine;

/**
 * Shared engine-substate values exposed through {@link NestedRunnerState#engineSubstate()}.
 * <p>
 * These values are optional hints grounded by real engine events. Callers must tolerate an empty
 * string when an engine does not expose a finer-grained waiting substate.
 * <p>
 * Compatibility policy:
 * only the constants defined by this type are guaranteed to be stable across CAT releases. Future
 * engines may expose additional non-empty values; callers must treat unknown values as opaque
 * engine-specific hints rather than exhaustively matching on every possible string.
 */
public final class NestedRunnerEngineSubstates
{
  /**
   * The nested engine acknowledged the turn and is waiting for model output to begin.
   */
  public static final String WAITING_FOR_MODEL = "waiting_for_model";
  /**
   * The nested engine emitted a tool request and is waiting for a corresponding tool result.
   */
  public static final String WAITING_FOR_TOOL_RESULT = "waiting_for_tool_result";

  private NestedRunnerEngineSubstates()
  {
  }
}
