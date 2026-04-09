/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import java.util.Locale;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * How cautiously the agent validates changes before the approval gate.
 */
public enum CautionLevel
{
  /**
   * Compile only (fastest feedback).
   */
  LOW,
  /**
   * Compile and unit tests (default).
   */
  MEDIUM,
  /**
   * Compile, unit tests, and E2E tests (maximum confidence).
   */
  HIGH;

  /**
   * Parses a caution level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed caution level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any caution level
   */
  public static CautionLevel fromString(String value)
  {
    requireThat(value, "value").isNotBlank();
    return valueOf(value.toUpperCase(Locale.ROOT));
  }

  /**
   * Returns whether unit tests should run at this caution level.
   *
   * @return {@code true} if unit tests are enabled (MEDIUM or HIGH), {@code false} if compile-only (LOW)
   */
  public boolean isUnitTestEnabled()
  {
    return this != LOW;
  }

  /**
   * Returns whether end-to-end tests should run at this caution level.
   *
   * @return {@code true} if E2E tests are enabled (HIGH only), {@code false} otherwise
   */
  public boolean isE2eEnabled()
  {
    return this == HIGH;
  }

  @Override
  public String toString()
  {
    return name().toLowerCase(Locale.ROOT);
  }
}
