/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import java.util.Locale;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * The effort level, controlling how thoroughly the agent investigates during planning and review.
 */
public enum EffortLevel
{
  /**
   * Low effort: minimal investigation, quick approach.
   */
  LOW,
  /**
   * Medium effort: moderate investigation and exploration.
   */
  MEDIUM,
  /**
   * High effort: thorough investigation and deep analysis.
   */
  HIGH;

  /**
   * Parses an effort level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed effort level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any effort level
   */
  public static EffortLevel fromString(String value)
  {
    requireThat(value, "value").isNotBlank();
    return valueOf(value.toUpperCase(Locale.ROOT));
  }

  @Override
  public String toString()
  {
    return name().toLowerCase(Locale.ROOT);
  }
}
