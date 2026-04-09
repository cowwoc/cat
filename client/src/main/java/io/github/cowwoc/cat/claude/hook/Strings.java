/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.node.ObjectNode;

/**
 * String utility methods.
 */
public final class Strings
{
  /**
   * The maximum length for truncated description text in display output.
   */
  public static final int DESCRIPTION_MAX_LENGTH = 60;

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

  /**
   * Builds a hook block decision JSON string using the scope's JSON mapper.
   *
   * @param scope  the JVM scope providing a JSON mapper
   * @param reason the reason for blocking
   * @return JSON string with {@code decision=block}
   * @throws NullPointerException     if {@code scope} or {@code reason} are null
   * @throws IllegalArgumentException if {@code reason} is blank
   */
  public static String block(JvmScope scope, String reason)
  {
    requireThat(reason, "reason").isNotBlank();
    ObjectNode response = scope.getJsonMapper().createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    try
    {
      return scope.getJsonMapper().writeValueAsString(response);
    }
    catch (Exception _)
    {
      return "{}";
    }
  }

  /**
   * Returns an empty hook response (allow the operation).
   *
   * @return the empty JSON object string {@code "{}"}
   */
  public static String empty()
  {
    return "{}";
  }

  /**
   * Wraps content in {@code <system-reminder>} tags for injection into Claude's system prompt.
   *
   * @param content the content to wrap
   * @return the content wrapped in system-reminder tags
   * @throws NullPointerException     if {@code content} is null
   * @throws IllegalArgumentException if {@code content} is blank
   */
  public static String wrapSystemReminder(String content)
  {
    requireThat(content, "content").isNotBlank();
    return "<system-reminder>\n" + content + "\n</system-reminder>";
  }
}
