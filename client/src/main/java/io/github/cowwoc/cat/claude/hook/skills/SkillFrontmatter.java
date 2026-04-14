/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting and validating skill YAML frontmatter fields.
 */
public final class SkillFrontmatter
{
  /**
   * The maximum number of characters allowed in a skill description.
   * Matches the Claude Code skill listing cap introduced in Claude Code 2.1.105.
   */
  public static final int MAX_DESCRIPTION_LENGTH = 1536;

  /**
   * Pattern to extract the description field from YAML frontmatter.
   * Handles both single-line and block scalar (>) formats.
   */
  private static final Pattern DESCRIPTION_PATTERN =
    Pattern.compile("^description:\\s*>?\\s*(.+?)(?=^\\w|^---)", Pattern.MULTILINE | Pattern.DOTALL);

  private SkillFrontmatter()
  {
  }

  /**
   * Extracts the description value from YAML frontmatter.
   *
   * @param content   the full SKILL.md content
   * @param skillPath the file path (for error messages)
   * @return the extracted description text, with leading/trailing whitespace removed
   * @throws IllegalArgumentException if no description field is found, or if the description
   *                                  exceeds {@value #MAX_DESCRIPTION_LENGTH} characters after
   *                                  whitespace normalization
   */
  public static String extractDescription(String content, String skillPath)
  {
    // Extract YAML frontmatter block (between --- delimiters)
    int firstDash = content.indexOf("---");
    if (firstDash < 0)
      throw new IllegalArgumentException(
        "No YAML frontmatter found in skill file: " + skillPath +
        ". SKILL.md files must start with --- frontmatter.");

    int secondDash = content.indexOf("---", firstDash + 3);
    if (secondDash < 0)
      throw new IllegalArgumentException(
        "Unclosed YAML frontmatter in skill file: " + skillPath +
        ". Frontmatter must be closed with ---.");

    String frontmatter = content.substring(firstDash + 3, secondDash);

    Matcher matcher = DESCRIPTION_PATTERN.matcher(frontmatter);
    if (!matcher.find())
      throw new IllegalArgumentException(
        "No 'description:' field found in frontmatter of: " + skillPath +
        ". Every SKILL.md must have a description field for intent routing.");

    // Normalize whitespace: collapse newlines and multiple spaces from block scalar
    String rawDescription = matcher.group(1);
    String description = rawDescription.replaceAll("\\s+", " ").strip();
    if (description.length() > MAX_DESCRIPTION_LENGTH)
      throw new IllegalArgumentException(
        "Description exceeds " + MAX_DESCRIPTION_LENGTH + "-character limit: " + description.length() +
        " characters in '" + skillPath + "'. Shorten the description before using this tool.");
    return description;
  }
}
