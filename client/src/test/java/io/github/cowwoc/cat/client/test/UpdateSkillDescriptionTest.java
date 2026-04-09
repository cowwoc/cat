/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.skills.UpdateSkillDescription;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link UpdateSkillDescription}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class UpdateSkillDescriptionTest
{
  private static final String SIMPLE_SKILL = """
    ---
    description: Use when user wants to squash commits
    user-invocable: false
    ---
    # Skill
    """;

  /**
   * Verifies that getDescription extracts a simple single-line description.
   */
  @Test
  public void extractsSimpleDescription()
  {
    String description = UpdateSkillDescription.getDescription(SIMPLE_SKILL);
    requireThat(description, "description").isEqualTo("Use when user wants to squash commits");
  }

  /**
   * Verifies that getDescription returns a description of exactly 250 characters without throwing.
   */
  @Test
  public void getDescriptionAcceptsExactly250Chars()
  {
    String description250 = "A".repeat(250);
    String content = "---\ndescription: " + description250 + "\nuser-invocable: false\n---\n# Skill\n";
    String result = UpdateSkillDescription.getDescription(content);
    requireThat(result.length(), "length").isEqualTo(250);
  }

  /**
   * Verifies that getDescription returns a description exceeding 250 characters without throwing.
   * Extract mode does not enforce the length limit; it is the caller's responsibility to check.
   */
  @Test
  public void getDescriptionReturnsDescriptionExceeding250Chars()
  {
    String description251 = "A".repeat(251);
    String content = "---\ndescription: " + description251 + "\nuser-invocable: false\n---\n# Skill\n";
    String result = UpdateSkillDescription.getDescription(content);
    requireThat(result.length(), "length").isEqualTo(251);
  }

  /**
   * Verifies that getDescription throws when no YAML frontmatter is present.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*No YAML frontmatter.*")
  public void getDescriptionThrowsOnMissingFrontmatter()
  {
    UpdateSkillDescription.getDescription("# No frontmatter here");
  }

  /**
   * Verifies that getDescription throws when the frontmatter has no description field.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*No 'description:' field.*")
  public void getDescriptionThrowsOnMissingDescriptionField()
  {
    UpdateSkillDescription.getDescription("---\nmodel: sonnet\n---\n# No description");
  }

  /**
   * Verifies that replaceDescription substitutes the description field with new text.
   */
  @Test
  public void replacesSimpleDescription()
  {
    String updated = UpdateSkillDescription.replaceDescription(SIMPLE_SKILL, "New description");
    requireThat(updated, "updated").contains("description: New description");
    requireThat(updated, "updated").doesNotContain("Use when user wants to squash commits");
  }

  /**
   * Verifies that replaceDescription preserves all other frontmatter fields after replacement.
   */
  @Test
  public void replacementPreservesOtherFrontmatterFields()
  {
    String updated = UpdateSkillDescription.replaceDescription(SIMPLE_SKILL, "New description");
    requireThat(updated, "updated").contains("user-invocable: false");
  }

  /**
   * Verifies that replaceDescription accepts a new description of exactly 250 characters.
   */
  @Test
  public void replaceDescriptionAcceptsExactly250Chars()
  {
    String description250 = "A".repeat(250);
    String updated = UpdateSkillDescription.replaceDescription(SIMPLE_SKILL, description250);
    requireThat(updated, "updated").contains("description: " + description250);
  }

  /**
   * Verifies that replaceDescription rejects a new description exceeding 250 characters.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*exceeds 250-character limit.*")
  public void replaceDescriptionRejectsDescriptionExceeding250Chars()
  {
    UpdateSkillDescription.replaceDescription(SIMPLE_SKILL, "B".repeat(251));
  }

  /**
   * Verifies that replaceDescription throws when no YAML frontmatter is present.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*No YAML frontmatter.*")
  public void replaceDescriptionThrowsOnMissingFrontmatter()
  {
    UpdateSkillDescription.replaceDescription("# No frontmatter here", "New description");
  }

  /**
   * Verifies that replaceDescription throws when the frontmatter has no description field.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*No 'description:' field.*")
  public void replaceDescriptionThrowsOnMissingDescriptionField()
  {
    UpdateSkillDescription.replaceDescription("---\nmodel: sonnet\n---\n# No description", "New");
  }

  /**
   * Verifies that replaceDescription collapses a block scalar description to a single-line value.
   */
  @Test
  public void replacesBlockScalarDescription()
  {
    String content = """
      ---
      description: >
        Line one of the description.
        Line two of the description.
      user-invocable: false
      ---
      # Skill
      """;
    String updated = UpdateSkillDescription.replaceDescription(content, "Short description");
    requireThat(updated, "updated").contains("description: Short description");
    requireThat(updated, "updated").doesNotContain("Line one");
    requireThat(updated, "updated").doesNotContain("Line two");
    requireThat(updated, "updated").contains("user-invocable: false");
  }
}
