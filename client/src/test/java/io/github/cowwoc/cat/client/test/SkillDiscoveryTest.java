/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery;
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
   * Verifies isModelInvocable returns false when disable-model-invocation: true is present.
   */
  @Test
  public void isModelInvocableReturnsFalseWhenDisabled()
  {
    String frontmatter = "description: Some skill\ndisable-model-invocation: true\n";
    requireThat(SkillDiscovery.isModelInvocable(frontmatter), "result").isFalse();
  }

  /**
   * Verifies isModelInvocable returns true when disable-model-invocation is absent.
   */
  @Test
  public void isModelInvocableReturnsTrueWhenAbsent()
  {
    String frontmatter = "description: Some skill\n";
    requireThat(SkillDiscovery.isModelInvocable(frontmatter), "result").isTrue();
  }

  /**
   * Verifies isModelInvocable handles extra whitespace around the colon.
   */
  @Test
  public void isModelInvocableHandlesExtraWhitespace()
  {
    String frontmatter = "description: Some skill\ndisable-model-invocation:  true\n";
    requireThat(SkillDiscovery.isModelInvocable(frontmatter), "result").isFalse();
  }

  /**
   * Verifies isModelInvocable handles uppercase True.
   */
  @Test
  public void isModelInvocableHandlesUppercaseTrue()
  {
    String frontmatter = "description: Some skill\ndisable-model-invocation: True\n";
    requireThat(SkillDiscovery.isModelInvocable(frontmatter), "result").isFalse();
  }

  /**
   * Verifies isModelInvocable ignores trailing YAML comments.
   */
  @Test
  public void isModelInvocableIgnoresTrailingComment()
  {
    String frontmatter = "description: Some skill\ndisable-model-invocation: true # user-only skill\n";
    requireThat(SkillDiscovery.isModelInvocable(frontmatter), "result").isFalse();
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
