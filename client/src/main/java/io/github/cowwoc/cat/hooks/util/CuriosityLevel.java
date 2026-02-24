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
 * The curiosity level, controlling how thoroughly the agent investigates before acting.
 */
public enum CuriosityLevel
{
  /**
   * Low curiosity: minimal investigation, acts quickly.
   */
  LOW,
  /**
   * Medium curiosity: moderate investigation before acting.
   */
  MEDIUM,
  /**
   * High curiosity: thorough investigation before acting.
   */
  HIGH;

  /**
   * Parses a curiosity level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed curiosity level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any curiosity level
   */
  public static CuriosityLevel fromString(String value)
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
