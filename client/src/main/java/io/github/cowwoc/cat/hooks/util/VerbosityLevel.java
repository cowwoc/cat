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
 * How much CAT explains itself during task execution.
 */
public enum VerbosityLevel
{
  /**
   * Progress banners and errors only — no reasoning, no summaries beyond phase markers.
   */
  LOW,
  /**
   * Phase-transition summaries — what was done, key decisions.
   */
  MEDIUM,
  /**
   * Full reasoning — alternatives considered, tradeoffs noted, rationale for each decision.
   */
  HIGH;

  /**
   * Parses a verbosity level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed verbosity level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any verbosity level
   */
  public static VerbosityLevel fromString(String value)
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
