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
 * The verify level, controlling which tests are run during the verify phase.
 */
public enum VerifyLevel
{
  /**
   * No verification: skip all tests.
   */
  NONE,
  /**
   * Changed verification: run tests only for changed files.
   */
  CHANGED,
  /**
   * All verification: run all tests.
   */
  ALL;

  /**
   * Parses a verify level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed verify level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any verify level
   */
  public static VerifyLevel fromString(String value)
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
