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
 * How cautious the agent is when validating changes.
 */
public enum CautionLevel
{
  /**
   * Minimal caution: skip validation.
   */
  LOW,
  /**
   * Moderate caution: validate only changed files.
   */
  MEDIUM,
  /**
   * Maximum caution: validate everything.
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

  @Override
  public String toString()
  {
    return name().toLowerCase(Locale.ROOT);
  }
}
