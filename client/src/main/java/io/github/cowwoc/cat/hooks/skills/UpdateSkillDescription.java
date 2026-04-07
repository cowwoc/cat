/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLI tool for extracting and replacing the {@code description:} field in YAML frontmatter.
 * <p>
 * Reads file content from stdin. Operates in two modes:
 * <ul>
 *   <li><b>Extract mode (0 args):</b> prints the description to stdout; exits 0 if ≤250 characters,
 *       exits 1 if the description exceeds 250 characters.</li>
 *   <li><b>Replace mode (1 arg):</b> validates the new description is ≤250 characters, replaces the
 *       {@code description:} field in the frontmatter, and writes the updated content to stdout.
 *       Exits non-zero with an error message if validation fails.</li>
 * </ul>
 */
public final class UpdateSkillDescription
{
  /**
   * Extracts the description field value from frontmatter; handles both single-line and block
   * scalar ({@code >}) formats. Captures content up to the next top-level key or closing delimiter.
   */
  private static final Pattern DESCRIPTION_EXTRACT_PATTERN =
    Pattern.compile("^description:\\s*>?\\s*(.+?)(?=^\\w|^---)", Pattern.MULTILINE | Pattern.DOTALL);

  /**
   * Matches the entire {@code description:} line plus any indented continuation lines (block scalar).
   * Used to replace the full description block with a single-line value.
   */
  private static final Pattern DESCRIPTION_REPLACE_PATTERN =
    Pattern.compile("^description:.*(?:\\n[ \\t]+.*)*", Pattern.MULTILINE);

  private static final int MAX_DESCRIPTION_LENGTH = 250;

  private UpdateSkillDescription()
  {
  }

  /**
   * Extracts the description from YAML frontmatter without enforcing the length limit.
   * <p>
   * Unlike {@link SkillFrontmatter#extractDescription(String, String)}, this method returns the
   * description regardless of its length so the caller can decide how to handle overlong values.
   *
   * @param content the full file content including YAML frontmatter
   * @return the extracted and whitespace-normalized description text
   * @throws NullPointerException     if {@code content} is null
   * @throws IllegalArgumentException if the content has no YAML frontmatter or no description field
   */
  public static String getDescription(String content)
  {
    requireThat(content, "content").isNotNull();

    int firstDash = content.indexOf("---");
    if (firstDash < 0)
      throw new IllegalArgumentException("No YAML frontmatter found in content.");

    int secondDash = content.indexOf("---", firstDash + 3);
    if (secondDash < 0)
      throw new IllegalArgumentException("Unclosed YAML frontmatter in content.");

    String frontmatter = content.substring(firstDash + 3, secondDash);

    Matcher matcher = DESCRIPTION_EXTRACT_PATTERN.matcher(frontmatter);
    if (!matcher.find())
      throw new IllegalArgumentException("No 'description:' field found in frontmatter.");

    String rawDescription = matcher.group(1);
    return rawDescription.replaceAll("\\s+", " ").strip();
  }

  /**
   * Validates the new description and replaces the {@code description:} field in the YAML frontmatter.
   * <p>
   * Block scalar formats ({@code description: >}) are collapsed to a single-line value.
   *
   * @param content        the full file content including YAML frontmatter
   * @param newDescription the replacement description text
   * @return the updated content with the new description substituted
   * @throws NullPointerException     if {@code content} or {@code newDescription} are null
   * @throws IllegalArgumentException if the new description exceeds 250 characters, if the content has
   *                                  no YAML frontmatter, or if the frontmatter has no description field
   */
  public static String replaceDescription(String content, String newDescription)
  {
    requireThat(content, "content").isNotNull();
    requireThat(newDescription, "newDescription").isNotNull();

    if (newDescription.length() > MAX_DESCRIPTION_LENGTH)
      throw new IllegalArgumentException(
        "Description exceeds 250-character limit: " + newDescription.length() +
        " characters. Shorten the description before using this tool.");

    int firstDash = content.indexOf("---");
    if (firstDash < 0)
      throw new IllegalArgumentException("No YAML frontmatter found in content.");

    int secondDash = content.indexOf("---", firstDash + 3);
    if (secondDash < 0)
      throw new IllegalArgumentException("Unclosed YAML frontmatter in content.");

    String beforeFrontmatter = content.substring(0, firstDash + 3);
    String frontmatter = content.substring(firstDash + 3, secondDash);
    String afterFrontmatter = content.substring(secondDash);

    Matcher matcher = DESCRIPTION_REPLACE_PATTERN.matcher(frontmatter);
    if (!matcher.find())
      throw new IllegalArgumentException("No 'description:' field found in frontmatter.");

    String newFrontmatter = matcher.replaceFirst(
      "description: " + Matcher.quoteReplacement(newDescription));

    return beforeFrontmatter + newFrontmatter + afterFrontmatter;
  }

  /**
   * CLI entry point.
   * <p>
   * In extract mode (0 args), reads content from stdin, prints the description to stdout, and exits 0
   * if the description is ≤250 characters or exits 1 if it exceeds 250 characters.
   * <p>
   * In replace mode (1 arg), reads content from stdin, validates the new description length, replaces
   * the {@code description:} field, and writes the updated content to stdout. Exits 1 if validation
   * fails, exits 2 on structural errors or wrong argument count.
   *
   * @param args positional arguments: empty for extract mode, one description string for replace mode
   */
  public static void main(String[] args)
  {
    String content;
    try
    {
      content = new String(System.in.readAllBytes(), UTF_8);
    }
    catch (IOException e)
    {
      System.err.println("Failed to read stdin: " + e.getMessage());
      System.exit(2);
      return;
    }

    if (args.length == 0)
    {
      try
      {
        String description = getDescription(content);
        System.out.print(description);
        if (description.length() > MAX_DESCRIPTION_LENGTH)
          System.exit(1);
      }
      catch (IllegalArgumentException e)
      {
        System.err.println(e.getMessage());
        System.exit(2);
      }
    }
    else if (args.length == 1)
    {
      try
      {
        String updated = replaceDescription(content, args[0]);
        System.out.print(updated);
      }
      catch (IllegalArgumentException e)
      {
        System.err.println(e.getMessage());
        System.exit(1);
      }
    }
    else
    {
      System.err.println("Usage: update-skill-description [new-description]");
      System.exit(2);
    }
  }
}
