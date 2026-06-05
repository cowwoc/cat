/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * A streamed nested-runner event coupled with the latest derived state snapshot.
 * <p>
 * Event callbacks reflect only runner output that was actually emitted by the nested engine.
 * One-shot callers should expect the final {@link NestedRunnerState} returned by
 * {@code executeProcess(...)} to normalize a resumable {@code WAITING_FOR_NEXT_REQUEST} boundary
 * into terminal {@code COMPLETED} after the process exits, even though the last streamed callback
 * still reflects the pre-finalization engine event.
 *
 * @param rawLine the raw engine event line
 * @param state   the latest state after processing {@code rawLine}
 */
public record NestedRunnerEvent(String rawLine, NestedRunnerState state)
{
  /**
   * Creates a new streamed event.
   */
  public NestedRunnerEvent
  {
    requireThat(rawLine, "rawLine").isNotNull();
    requireThat(state, "state").isNotNull();
  }
}
