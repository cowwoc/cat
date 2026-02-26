/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.util.Locale;

/**
 * Canonical status values for CAT issues and versions.
 * <p>
 * This is the single source of truth for valid status values across the system.
 * All components that validate or compare status values should reference this enum.
 */
public enum IssueStatus
{
  OPEN("open"),
  IN_PROGRESS("in-progress"),
  CLOSED("closed"),
  BLOCKED("blocked");

  private final String value;

  /**
   * Creates a new status value.
   *
   * @param value the string representation used in STATE.md files
   */
  IssueStatus(String value)
  {
    this.value = value;
  }

  /**
   * Returns the string representation used in STATE.md files.
   *
   * @return the string representation
   */
  @Override
  public String toString()
  {
    return value;
  }

  /**
   * Parses a string into a IssueStatus enum constant.
   *
   * @param text the status string to parse (case-insensitive)
   * @return the matching status, or {@code null} if no match
   */
  public static IssueStatus fromString(String text)
  {
    if (text == null)
      return null;
    String normalized = text.strip().toLowerCase(Locale.ROOT);
    for (IssueStatus status : values())
    {
      if (status.value.equals(normalized))
        return status;
    }
    return null;
  }

  /**
   * Returns a comma-separated list of all valid status values, sorted alphabetically.
   *
   * @return the formatted list of valid values
   */
  public static String asCommaSeparated()
  {
    StringBuilder sb = new StringBuilder();
    IssueStatus[] sorted = values().clone();
    java.util.Arrays.sort(sorted, java.util.Comparator.comparing(IssueStatus::toString));
    for (int i = 0; i < sorted.length; ++i)
    {
      if (i > 0)
        sb.append(", ");
      sb.append(sorted[i].value);
    }
    return sb.toString();
  }
}
