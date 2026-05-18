/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.skills.ModelIdResolver;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link ModelIdResolver}.
 */
@Test(singleThreaded = true)
public final class ModelIdResolverTest
{
  /**
   * Verifies that {@code resolve} returns the expected fully-qualified {@code haiku} ID.
   */
  @Test
  public void resolvesHaiku()
  {
    String result = ModelIdResolver.resolve("2.1.87", "haiku");
    requireThat(result, "result").isEqualTo("claude-haiku-4-5");
  }

  /**
   * Verifies that {@code resolve} returns the expected fully-qualified {@code sonnet} ID.
   */
  @Test
  public void resolvesSonnet()
  {
    String result = ModelIdResolver.resolve("2.1.87", "sonnet");
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5");
  }

  /**
   * Verifies that {@code resolve} returns the expected fully-qualified {@code opus} ID.
   */
  @Test
  public void resolvesOpus()
  {
    String result = ModelIdResolver.resolve("2.1.87", "opus");
    requireThat(result, "result").isEqualTo("claude-opus-4-5");
  }

  /**
   * Verifies that {@code resolve} handles uppercase short names.
   */
  @Test
  public void resolvesUpperCase()
  {
    String result = ModelIdResolver.resolve("2.1.87", "HAIKU");
    requireThat(result, "result").isEqualTo("claude-haiku-4-5");
  }

  /**
   * Verifies that {@code resolve} handles mixed-case short names.
   */
  @Test
  public void resolvesMixedCase()
  {
    String result = ModelIdResolver.resolve("2.1.87", "Sonnet");
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5");
  }

  /**
   * Verifies that a fully-qualified model ID starting with {@code "claude-"} is passed through
   * unchanged.
   */
  @Test
  public void passesThroughFullyQualifiedId()
  {
    String result = ModelIdResolver.resolve("2.1.87", "claude-sonnet-4-5");
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5");
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
    requireThat(result, "result").isEqualTo("claude-sonnet-4-5");
  }

  /**
   * Verifies that the minimum supported version (2.1.0) resolves correctly.
   */
  @Test
  public void minimumVersionResolves()
  {
    String result = ModelIdResolver.resolve("2.1.0", "opus");
    requireThat(result, "result").isEqualTo("claude-opus-4-5");
  }

  /**
   * Verifies that strict version detection fails when the Claude binary is unavailable.
   */
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*failed with exit code.*")
  public void detectClaudeCodeVersionFailsWhenBinaryUnavailable()
  {
    String originalValue = System.getProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY);
    System.setProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY,
      "cat-missing-claude-binary-for-test");
    try
    {
      ModelIdResolver.detectClaudeCodeVersion();
    }
    finally
    {
      restoreProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY, originalValue);
    }
  }

  /**
   * Verifies that runtime-safe version detection falls back to the latest known mapping when Claude
   * is unavailable.
   */
  @Test
  public void detectClaudeCodeVersionOrLatestMappingFallsBackWhenBinaryUnavailable()
  {
    String originalValue = System.getProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY);
    System.setProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY,
      "cat-missing-claude-binary-for-test");
    try
    {
      String version = ModelIdResolver.detectClaudeCodeVersionOrLatestMapping();
      String resolved = ModelIdResolver.resolve(version, "haiku");
      requireThat(resolved, "resolved").isEqualTo("claude-haiku-4-5");
    }
    finally
    {
      restoreProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY, originalValue);
    }
  }

  /**
   * Verifies that runtime-safe version detection does not hide malformed executable output.
   *
   * @throws IOException if creating the fake executable fails
   */
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*(unexpected output|failed with exit code).*")
  public void detectClaudeCodeVersionOrLatestMappingFailsForMalformedOutput() throws IOException
  {
    Path fakeClaude = Files.createTempFile("cat-fake-claude", ".sh");
    Files.writeString(fakeClaude, "#!/bin/sh\nprintf '%s\\n' 'not-a-version'\n");
    fakeClaude.toFile().setExecutable(true);
    String originalValue = System.getProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY);
    System.setProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY, fakeClaude.toString());
    try
    {
      ModelIdResolver.detectClaudeCodeVersionOrLatestMapping();
    }
    finally
    {
      restoreProperty(ModelIdResolver.CLAUDE_EXECUTABLE_PROPERTY, originalValue);
      Files.deleteIfExists(fakeClaude);
    }
  }

  /**
   * Restores a system property to its original value.
   *
   * @param key the property name
   * @param value the original value, or {@code null} if the property was unset
   */
  private static void restoreProperty(String key, String value)
  {
    if (value == null)
      System.clearProperty(key);
    else
      System.setProperty(key, value);
  }
}
