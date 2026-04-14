/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

/**
 * Shared utility methods for parsing YAML frontmatter from Markdown files.
 */
public final class FrontmatterUtils
{
  static final int FRONTMATTER_SCAN_LIMIT = 4096;

  private FrontmatterUtils()
  {
  }

  /**
   * Extracts the YAML frontmatter block from file content.
   * <p>
   * If the closing {@code ---} line does not appear within the first
   * {@value #FRONTMATTER_SCAN_LIMIT} characters, the file is treated as having no frontmatter.
   *
   * @param content the file content
   * @return the frontmatter string (between the {@code ---} markers), or null if none
   */
  public static String extractFrontmatter(String content)
  {
    if (!content.startsWith("---"))
      return null;
    int scanEnd = Math.min(content.length(), FRONTMATTER_SCAN_LIMIT);
    int end = content.indexOf("\n---", 3);
    if (end < 0 || end > scanEnd)
      return null;
    return content.substring(3, end).strip();
  }

  /**
   * Returns the body content with frontmatter removed.
   * <p>
   * If the closing {@code ---} line does not appear within the first
   * {@value #FRONTMATTER_SCAN_LIMIT} characters, the full content is returned unchanged
   * (consistent with {@link #extractFrontmatter(String)}).
   *
   * @param content the file content
   * @return content without frontmatter block
   */
  public static String stripFrontmatter(String content)
  {
    if (!content.startsWith("---"))
      return content;
    int scanEnd = Math.min(content.length(), FRONTMATTER_SCAN_LIMIT);
    int end = content.indexOf("\n---", 3);
    if (end < 0 || end > scanEnd)
      return content;
    int bodyStart = end + 4;
    if (bodyStart < content.length() && content.charAt(bodyStart) == '\n')
      ++bodyStart;
    return content.substring(bodyStart).strip();
  }
}
