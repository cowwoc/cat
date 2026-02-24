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
 * The trust level for execution, controlling how much agent autonomy is permitted.
 */
public enum TrustLevel
{
  /**
   * Low trust: requires explicit approval for all operations.
   */
  LOW,
  /**
   * Medium trust: requires approval for destructive operations.
   */
  MEDIUM,
  /**
   * High trust: all operations are auto-approved.
   */
  HIGH;

  /**
   * Parses a trust level from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed trust level
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any trust level
   */
  public static TrustLevel fromString(String value)
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
