/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.skills.ModelIdResolver;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link ModelIdResolver}.
 */
public final class ModelIdResolverTest
{
  /**
   * Verifies that {@code resolve} returns the expected fully-qualified {@code haiku} ID.
   */
  @Test
  public void resolvesHaiku()
  {
    String result = ModelIdResolver.resolve("2.1.87", "haiku");
    requireThat(result, "result").isEqualTo("claude-haiku-4-5-20251001");
  }

  /**
   * Verifies that {@code resolve} returns the expected fully-qualified {@code sonnet} ID.
   */
  @Test
  public void resolvesSonnet()
  {
    String result = ModelIdResolver.resolve("2.1.87", "sonnet");
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5-20250929");
  }

  /**
   * Verifies that {@code resolve} returns the expected fully-qualified {@code opus} ID.
   */
  @Test
  public void resolvesOpus()
  {
    String result = ModelIdResolver.resolve("2.1.87", "opus");
    requireThat(result, "result").isEqualTo("claude-opus-4-5-20251101");
  }

  /**
   * Verifies that {@code resolve} handles uppercase short names.
   */
  @Test
  public void resolvesUpperCase()
  {
    String result = ModelIdResolver.resolve("2.1.87", "HAIKU");
    requireThat(result, "result").isEqualTo("claude-haiku-4-5-20251001");
  }

  /**
   * Verifies that {@code resolve} handles mixed-case short names.
   */
  @Test
  public void resolvesMixedCase()
  {
    String result = ModelIdResolver.resolve("2.1.87", "Sonnet");
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5-20250929");
  }

  /**
   * Verifies that a fully-qualified model ID starting with {@code "claude-"} is passed through
   * unchanged.
   */
  @Test
  public void passesThroughFullyQualifiedId()
  {
    String result = ModelIdResolver.resolve("2.1.87", "claude-sonnet-4-5-20250929");
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5-20250929");
  }

  /**
   * Verifies that an unknown short name throws {@link IllegalArgumentException}.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*unknown model short name.*")
  public void throwsForUnknownModel()
  {
    ModelIdResolver.resolve("2.1.87", "gpt-4");
  }

  /**
   * Verifies that a version below the minimum supported version throws
   * {@link IllegalArgumentException}.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*below the minimum supported version.*")
  public void throwsForVersionBelowMinimum()
  {
    ModelIdResolver.resolve("1.0.0", "haiku");
  }

  /**
   * Verifies that a future version (beyond any known mapping) still resolves using the latest known
   * mapping.
   */
  @Test
  public void futureVersionUsesLatestMapping()
  {
    String result = ModelIdResolver.resolve("99.0.0", "sonnet");
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5-20250929");
  }

  /**
   * Verifies that the minimum supported version (2.1.0) resolves correctly.
   */
  @Test
  public void minimumVersionResolves()
  {
    String result = ModelIdResolver.resolve("2.1.0", "opus");
    requireThat(result, "result").isEqualTo("claude-opus-4-5-20251101");
  }
}
