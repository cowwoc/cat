/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utility methods for parsing YAML frontmatter from Markdown files.
 */
public final class FrontmatterUtils
{
  static final int FRONTMATTER_SCAN_LIMIT = 4096;
  private static final Pattern MARKDOWN_LICENSE_HEADER = Pattern.compile(
    "\\A<!--\\R" +
      "Copyright \\(c\\) 2026 Gili Tzabari\\. All rights reserved\\.\\R" +
      "Licensed under the CAT Commercial License\\.\\R" +
      "See LICENSE\\.md in the project root for license terms\\.\\R" +
      "-->\\R?",
    Pattern.DOTALL);

  private FrontmatterUtils()
  {
  }

  /**
   * Extracts the YAML frontmatter block from file content.
   * <p>
   * The source Markdown license header may appear before the frontmatter.
   * <p>
   * If the closing {@code ---} line does not appear within the first
   * {@value #FRONTMATTER_SCAN_LIMIT} characters after the frontmatter opening, the file is treated as
   * having no frontmatter.
   *
   * @param content the file content
   * @return the frontmatter string (between the {@code ---} markers), or null if none
   */
  public static String extractFrontmatter(String content)
  {
    int frontmatterStart = findFrontmatterStart(content);
    if (!content.startsWith("---", frontmatterStart))
      return null;
    int scanEnd = Math.min(content.length(), frontmatterStart + FRONTMATTER_SCAN_LIMIT);
    int end = content.indexOf("\n---", frontmatterStart + 3);
    if (end < 0 || end > scanEnd)
      return null;
    return content.substring(frontmatterStart + 3, end).strip();
  }

  /**
   * Returns the body content with frontmatter removed.
   * <p>
   * The source Markdown license header may appear before the frontmatter. It is preserved because only
   * source frontmatter is removed here.
   * <p>
   * If the closing {@code ---} line does not appear within the first
   * {@value #FRONTMATTER_SCAN_LIMIT} characters after the frontmatter opening, the full content is returned unchanged
   * (consistent with {@link #extractFrontmatter(String)}).
   *
   * @param content the file content
   * @return content without frontmatter block
   */
  public static String stripFrontmatter(String content)
  {
    int frontmatterStart = findFrontmatterStart(content);
    if (!content.startsWith("---", frontmatterStart))
      return content;
    int scanEnd = Math.min(content.length(), frontmatterStart + FRONTMATTER_SCAN_LIMIT);
    int end = content.indexOf("\n---", frontmatterStart + 3);
    if (end < 0 || end > scanEnd)
      return content;
    int bodyStart = end + 4;
    if (bodyStart < content.length() && content.charAt(bodyStart) == '\n')
      ++bodyStart;
    return (content.substring(0, frontmatterStart) + content.substring(bodyStart)).strip();
  }

  /**
   * Returns the offset at which YAML frontmatter may begin.
   *
   * @param content the file content
   * @return zero, or the first character after a standard Markdown source license header
   */
  private static int findFrontmatterStart(String content)
  {
    Matcher matcher = MARKDOWN_LICENSE_HEADER.matcher(content);
    if (matcher.find())
      return matcher.end();
    return 0;
  }
}
