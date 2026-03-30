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
 * How much the agent pursues perfection in the current task.
 * <p>
 * High perfection means fixing every issue encountered; low perfection means staying focused on the primary goal.
 */
public enum PerfectionLevel
{
  /**
   * Stay focused on the primary goal, defer tangential improvements.
   */
  LOW,
  /**
   * Fix issues that are easy to address, defer complex ones.
   */
  MEDIUM,
  /**
   * Fix every issue encountered, even if tangential to the primary goal.
   */
  HIGH;

  /**
   * Parses a perfection level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed perfection level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any perfection level
   */
  public static PerfectionLevel fromString(String value)
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
