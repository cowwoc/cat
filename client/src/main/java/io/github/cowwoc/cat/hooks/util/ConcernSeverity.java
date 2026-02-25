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
 * Severity level for stakeholder review concerns.
 * <p>
 * Ordered from most severe to least severe: CRITICAL > HIGH > MEDIUM > LOW.
 */
public enum ConcernSeverity
{
  /**
   * Blocks release. Causes data loss, security breach, or breaks core functionality. Must fix before merge.
   */
  CRITICAL,
  /**
   * Significant issue that should be fixed soon. Does not block release but degrades quality materially.
   */
  HIGH,
  /**
   * Improvement that would meaningfully benefit the codebase. Acceptable to defer.
   */
  MEDIUM,
  /**
   * Minor suggestion, stylistic preference, or nice-to-have. No material impact if deferred indefinitely.
   */
  LOW;

  /**
   * Parses a concern severity from a string value.
   *
   * @param value the string value to parse (case-insensitive)
   * @return the parsed concern severity
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is blank or does not match any severity level
   */
  public static ConcernSeverity fromString(String value)
  {
    requireThat(value, "value").isNotBlank();
    return valueOf(value.toUpperCase(Locale.ROOT));
  }

  /**
   * Returns true if this severity is at least as severe as the given threshold.
   * <p>
   * CRITICAL is the highest severity; LOW is the lowest. A concern "passes" the threshold if it is at least
   * as severe as the threshold level.
   *
   * @param threshold the minimum severity threshold to compare against
   * @return {@code true} if this severity is greater than or equal to {@code threshold}
   * @throws NullPointerException if {@code threshold} is null
   */
  public boolean isAtLeast(ConcernSeverity threshold)
  {
    requireThat(threshold, "threshold").isNotNull();
    return this.ordinal() <= threshold.ordinal();
  }

  @Override
  public String toString()
  {
    return name().toLowerCase(Locale.ROOT);
  }
}
