/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

/**
 * String utility methods.
 */
public final class Strings
{
  private Strings()
  {
    // Prevent instantiation
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
