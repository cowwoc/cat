/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;
import io.github.cowwoc.cat.hooks.util.SkillDiscovery;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for SkillDiscovery utility methods.
 */
public final class SkillDiscoveryTest
{
  /**
   * Verifies extractFrontmatter returns null when no frontmatter markers present.
   */
  @Test
  public void extractFrontmatterReturnsNullWhenNone()
  {
    String content = "No frontmatter here\njust content";
    String result = SkillDiscovery.extractFrontmatter(content);
    requireThat(result, "result").isNull();
  }

  /**
   * Verifies extractFrontmatter returns the content between markers.
   */
  @Test
  public void extractFrontmatterReturnsContent()
  {
    String content = "---\ndescription: Test skill\n---\nBody here";
    String result = SkillDiscovery.extractFrontmatter(content);
    requireThat(result, "result").contains("description: Test skill");
  }

  /**
   * Verifies isModelInvocableFalse returns true when model-invocable: false is present.
   */
  @Test
  public void isModelInvocableFalseReturnsTrueWhenFalse()
  {
    String frontmatter = "description: Some skill\nmodel-invocable: false\n";
    requireThat(SkillDiscovery.isModelInvocableFalse(frontmatter), "result").isTrue();
  }

  /**
   * Verifies isModelInvocableFalse returns false when model-invocable: true is present.
   */
  @Test
  public void isModelInvocableFalseReturnsFalseWhenTrue()
  {
    String frontmatter = "description: Some skill\nmodel-invocable: true\n";
    requireThat(SkillDiscovery.isModelInvocableFalse(frontmatter), "result").isFalse();
  }

  /**
   * Verifies isModelInvocableFalse returns false when model-invocable is absent.
   */
  @Test
  public void isModelInvocableFalseReturnsFalseWhenAbsent()
  {
    String frontmatter = "description: Some skill\n";
    requireThat(SkillDiscovery.isModelInvocableFalse(frontmatter), "result").isFalse();
  }

  /**
   * Verifies extractDescription handles inline description.
   */
  @Test
  public void extractDescriptionHandlesInlineValue()
  {
    String frontmatter = "description: Add a task.\nother: value";
    String result = SkillDiscovery.extractDescription(frontmatter);
    requireThat(result, "result").isEqualTo("Add a task.");
  }

  /**
   * Verifies extractDescription handles block scalar (>) format.
   */
  @Test
  public void extractDescriptionHandlesBlockScalar()
  {
    String frontmatter = "description: >\n  First line.\n  Second line.\nother: value";
    String result = SkillDiscovery.extractDescription(frontmatter);
    requireThat(result, "result").contains("First line.");
    requireThat(result, "result").contains("Second line.");
  }

  /**
   * Verifies extractDescription returns null when description is absent.
   */
  @Test
  public void extractDescriptionReturnsNullWhenAbsent()
  {
    String frontmatter = "allowed-tools:\n  - Read\n";
    String result = SkillDiscovery.extractDescription(frontmatter);
    requireThat(result, "result").isNull();
  }
}
