/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * String utility methods.
 */
public final class Strings
{
  /**
   * The maximum length for truncated description text in display output.
   */
  public static final int DESCRIPTION_MAX_LENGTH = 60;

  /**
   * The subagent_type identifier for work-execute subagents spawned by the work-with-issue workflow.
   */
  public static final String WORK_EXECUTE_SUBAGENT_TYPE = "cat:work-execute";

  private Strings()
  {
    // Prevent instantiation
  }

  /**
   * Truncates a string to at most {@code maxLength} characters, appending {@code "..."} if truncated.
   *
   * @param str the string to truncate
   * @param maxLength the maximum allowed length of the result, must be at least 3
   * @return the original string if its length is within {@code maxLength}, or a truncated version ending with
   *   {@code "..."}
   * @throws NullPointerException if {@code str} is null
   * @throws IllegalArgumentException if {@code maxLength} is less than 3
   */
  public static String truncate(String str, int maxLength)
  {
    requireThat(maxLength, "maxLength").isGreaterThanOrEqualTo(3);
    if (str.length() <= maxLength)
      return str;
    return str.substring(0, maxLength - 3) + "...";
  }

  /**
   * Escapes a string for safe inclusion in a JSON string value.
   * <p>
   * Escapes backslash, double-quote, newline, and carriage-return characters.
   *
   * @param value the string to escape
   * @return the escaped string
   * @throws NullPointerException if {@code value} is null
   */
  public static String escapeJson(String value)
  {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  /**
   * Compares two strings for equality, ignoring case.
   * Similar to Objects.equals() but uses String.equalsIgnoreCase().
   *
   * @param first the first string (may be null)
   * @param second the second string (may be null)
   * @return true if both are null, or if both are non-null and equal ignoring case
   */
  @SuppressWarnings("PMD.UseEqualsToCompareStrings")
  public static boolean equalsIgnoreCase(String first, String second)
  {
    // Reference equality check handles both-null case and same-object optimization
    if (first == second)
      return true;
    if (first == null || second == null)
      return false;
    return first.equalsIgnoreCase(second);
  }
}
