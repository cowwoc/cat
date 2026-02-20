/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.licensing.Entitlements;
import io.github.cowwoc.cat.hooks.licensing.Tier;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link Entitlements}.
 */
public final class EntitlementsTest
{
  /**
   * Verifies that core tier has basic features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void coreTierHasBasicFeatures() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
              "features": ["single-agent-execution", "basic-task-management"]
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);

        requireThat(entitlements.hasFeature(Tier.CORE, "single-agent-execution"), "hasFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.CORE, "basic-task-management"), "hasFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.CORE, "multi-agent-orchestration"), "hasFeature").
          isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that pro tier includes core features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void proTierIncludesCoreFeatures() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
              "features": ["single-agent-execution"]
            },
            "pro": {
              "features": ["multi-agent-orchestration"],
              "includes": "core"
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);

        requireThat(entitlements.hasFeature(Tier.PRO, "single-agent-execution"), "hasCoreFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.PRO, "multi-agent-orchestration"), "hasProFeature").
          isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that enterprise tier includes pro and core features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void enterpriseTierIncludesAllFeatures() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
              "features": ["single-agent-execution"]
            },
            "pro": {
              "features": ["multi-agent-orchestration"],
              "includes": "core"
            },
            "enterprise": {
              "features": ["audit-logging"],
              "includes": "pro"
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);

        requireThat(entitlements.hasFeature(Tier.ENTERPRISE, "single-agent-execution"), "hasCoreFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.ENTERPRISE, "multi-agent-orchestration"), "hasProFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.ENTERPRISE, "audit-logging"), "hasEnterpriseFeature").
          isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getRequiredTier returns the minimum tier for a feature.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void getRequiredTierReturnsMinimumTier() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
              "features": ["single-agent-execution"]
            },
            "pro": {
              "features": ["multi-agent-orchestration"],
              "includes": "core"
            },
            "enterprise": {
              "features": ["audit-logging"],
              "includes": "pro"
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);

        requireThat(entitlements.getRequiredTier("single-agent-execution"), "coreFeature").
          isEqualTo(Optional.of(Tier.CORE));
        requireThat(entitlements.getRequiredTier("multi-agent-orchestration"), "proFeature").
          isEqualTo(Optional.of(Tier.PRO));
        requireThat(entitlements.getRequiredTier("audit-logging"), "enterpriseFeature").
          isEqualTo(Optional.of(Tier.ENTERPRISE));
        requireThat(entitlements.getRequiredTier("nonexistent-feature"), "unknownFeature").
          isEqualTo(Optional.empty());
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getTierFeatures returns all features including inherited ones.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void getTierFeaturesIncludesInherited() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
              "features": ["feature-a", "feature-b"]
            },
            "pro": {
              "features": ["feature-c"],
              "includes": "core"
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);

        Set<String> proFeatures = entitlements.getTierFeatures(Tier.PRO);
        requireThat(proFeatures, "proFeatures").contains("feature-a");
        requireThat(proFeatures, "proFeatures").contains("feature-b");
        requireThat(proFeatures, "proFeatures").contains("feature-c");
        requireThat(proFeatures.size(), "size").isEqualTo(3);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that unknown tier in configuration throws exception.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void unknownTierThrowsException() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
              "features": ["feature-a"]
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);

        try
        {
          entitlements.getTierFeatures(Tier.PRO);
        }
        catch (IllegalArgumentException e)
        {
          requireThat(e.getMessage(), "message").contains("Unknown tier");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that circular tier reference does not stack overflow.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void circularTierReferenceDoesNotStackOverflow() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
              "features": ["feature-a"],
              "includes": "pro"
            },
            "pro": {
              "features": ["feature-b"],
              "includes": "core"
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);
        Set<String> features = entitlements.getTierFeatures(Tier.CORE);

        requireThat(features, "features").contains("feature-a");
        requireThat(features, "features").contains("feature-b");
        requireThat(features.size(), "size").isEqualTo(2);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that missing features array returns empty.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void missingFeaturesArrayReturnsEmpty() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "core": {
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);
        Set<String> features = entitlements.getTierFeatures(Tier.CORE);

        requireThat(features.size(), "size").isEqualTo(0);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that includes nonexistent tier returns own features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void includesNonexistentTierReturnsOwnFeatures() throws IOException
  {
    Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "pro": {
              "features": ["feature-a"],
              "includes": "nonexistent"
            }
          }
        }
        """);

    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        Entitlements entitlements = new Entitlements(scope);
        Set<String> features = entitlements.getTierFeatures(Tier.PRO);

        requireThat(features, "features").contains("feature-a");
        requireThat(features.size(), "size").isEqualTo(1);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Creates a temporary directory with a tiers.json config file.
   *
   * @param json the JSON content
   * @return the temporary directory path
   * @throws IOException if file creation fails
   */
  private Path createTempTiersConfig(String json) throws IOException
  {
    Path tempDir = Files.createTempDirectory("entitlements-test-");
    Path configDir = tempDir.resolve("config");
    Files.createDirectories(configDir);
    Path tiersFile = configDir.resolve("tiers.json");
    Files.writeString(tiersFile, json);
    return tempDir;
  }
}
