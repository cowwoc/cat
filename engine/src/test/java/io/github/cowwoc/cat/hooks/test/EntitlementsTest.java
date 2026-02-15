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
import tools.jackson.databind.json.JsonMapper;

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
   * Verifies that indie tier has basic features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void indieTierHasBasicFeatures() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
              "features": ["single-agent-execution", "basic-task-management"]
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);

        requireThat(entitlements.hasFeature(Tier.INDIE, "single-agent-execution"), "hasFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.INDIE, "basic-task-management"), "hasFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.INDIE, "multi-agent-orchestration"), "hasFeature").
          isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that team tier includes indie features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teamTierIncludesIndieFeatures() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
              "features": ["single-agent-execution"]
            },
            "team": {
              "features": ["multi-agent-orchestration"],
              "includes": "indie"
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);

        requireThat(entitlements.hasFeature(Tier.TEAM, "single-agent-execution"), "hasIndieFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.TEAM, "multi-agent-orchestration"), "hasTeamFeature").
          isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that enterprise tier includes team and indie features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void enterpriseTierIncludesAllFeatures() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
              "features": ["single-agent-execution"]
            },
            "team": {
              "features": ["multi-agent-orchestration"],
              "includes": "indie"
            },
            "enterprise": {
              "features": ["audit-logging"],
              "includes": "team"
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);

        requireThat(entitlements.hasFeature(Tier.ENTERPRISE, "single-agent-execution"), "hasIndieFeature").
          isTrue();
        requireThat(entitlements.hasFeature(Tier.ENTERPRISE, "multi-agent-orchestration"), "hasTeamFeature").
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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
              "features": ["single-agent-execution"]
            },
            "team": {
              "features": ["multi-agent-orchestration"],
              "includes": "indie"
            },
            "enterprise": {
              "features": ["audit-logging"],
              "includes": "team"
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);

        requireThat(entitlements.getRequiredTier("single-agent-execution"), "indieFeature").
          isEqualTo(Optional.of(Tier.INDIE));
        requireThat(entitlements.getRequiredTier("multi-agent-orchestration"), "teamFeature").
          isEqualTo(Optional.of(Tier.TEAM));
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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
              "features": ["feature-a", "feature-b"]
            },
            "team": {
              "features": ["feature-c"],
              "includes": "indie"
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);

        Set<String> teamFeatures = entitlements.getTierFeatures(Tier.TEAM);
        requireThat(teamFeatures, "teamFeatures").contains("feature-a");
        requireThat(teamFeatures, "teamFeatures").contains("feature-b");
        requireThat(teamFeatures, "teamFeatures").contains("feature-c");
        requireThat(teamFeatures.size(), "size").isEqualTo(3);
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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
              "features": ["feature-a"]
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);

        try
        {
          entitlements.getTierFeatures(Tier.TEAM);
          requireThat(false, "shouldThrow").isTrue();
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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
              "features": ["feature-a"],
              "includes": "team"
            },
            "team": {
              "features": ["feature-b"],
              "includes": "indie"
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);
        Set<String> features = entitlements.getTierFeatures(Tier.INDIE);

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "indie": {
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);
        Set<String> features = entitlements.getTierFeatures(Tier.INDIE);

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempTiersConfig("""
        {
          "tiers": {
            "team": {
              "features": ["feature-a"],
              "includes": "nonexistent"
            }
          }
        }
        """);

      try
      {
        Entitlements entitlements = new Entitlements(tempDir, mapper);
        Set<String> features = entitlements.getTierFeatures(Tier.TEAM);

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
