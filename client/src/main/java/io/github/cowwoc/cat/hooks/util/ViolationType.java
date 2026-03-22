/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

/**
 * Enumerates the categories of "giving up" violations detected by {@link GivingUpDetector}.
 * <p>
 * Each constant corresponds to a distinct behavioral anti-pattern that the detector is trained
 * to identify in agent text.
 */
public enum ViolationType
{
  /**
   * The agent cites complexity, token budget, or other constraints to justify reducing scope
   * or abandoning work.
   */
  CONSTRAINT_RATIONALIZATION,

  /**
   * The agent disables, removes, or skips broken code instead of debugging and fixing it.
   */
  CODE_REMOVAL,

  /**
   * The agent avoids compilation or build failures by removing dependencies or "simplifying"
   * instead of debugging systematically.
   */
  COMPILATION_ABANDONMENT,

  /**
   * The agent asks the user for permission to continue a task that should be completed
   * autonomously.
   */
  PERMISSION_SEEKING,

  /**
   * The agent references token usage or context limits as justification for reducing work
   * scope or quality.
   */
  TOKEN_RATIONALIZATION
}
