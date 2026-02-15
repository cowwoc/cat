/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.util.Locale;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Priority level for action items.
 */
public enum Priority
{
  /**
   * High priority.
   */
  HIGH(3),
  /**
   * Medium priority.
   */
  MEDIUM(2),
  /**
   * Low priority.
   */
  LOW(1);

  private final int value;

  /**
   * Creates a priority with the specified numeric value.
   *
   * @param value the numeric value (higher = more important)
   */
  Priority(int value)
  {
    this.value = value;
  }

  /**
   * Returns the numeric value of this priority.
   *
   * @return the numeric value (higher = more important)
   */
  public int getValue()
  {
    return value;
  }

  /**
   * Parses a priority from a string value.
   *
   * @param text the priority string (case-insensitive)
   * @return the corresponding Priority enum value
   * @throws NullPointerException     if {@code text} is null
   * @throws IllegalArgumentException if {@code text} does not match a valid priority
   */
  public static Priority fromString(String text)
  {
    requireThat(text, "text").isNotNull();
    String normalized = text.toLowerCase(Locale.ROOT);
    switch (normalized)
    {
      case "high":
        return HIGH;
      case "medium":
        return MEDIUM;
      case "low":
        return LOW;
      default:
        throw new IllegalArgumentException("Invalid priority: " + text +
          ". Valid values: high, medium, low");
    }
  }
}
