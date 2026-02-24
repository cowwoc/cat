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
 * The patience level, controlling how long the agent waits before timing out or giving up.
 */
public enum PatienceLevel
{
  /**
   * Low patience: short wait times, quick timeouts.
   */
  LOW,
  /**
   * Medium patience: moderate wait times.
   */
  MEDIUM,
  /**
   * High patience: long wait times, persistent retries.
   */
  HIGH;

  /**
   * Parses a patience level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed patience level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any patience level
   */
  public static PatienceLevel fromString(String value)
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
