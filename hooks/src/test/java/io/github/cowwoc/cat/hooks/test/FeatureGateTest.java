package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.licensing.FeatureGate;
import io.github.cowwoc.cat.hooks.licensing.Tier;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link FeatureGate}.
 */
public final class FeatureGateTest
{
  /**
   * Verifies that no license defaults to indie tier.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void noLicenseDefaultsToIndie() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path pluginRoot = createTempPluginRoot();
      Path projectDir = Files.createTempDirectory("project-");

      try
      {
        FeatureGate gate = FeatureGate.create(pluginRoot, mapper);
        FeatureGate.GateResult result = gate.check(projectDir, "single-agent-execution");

        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.allowed(), "allowed").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that indie tier is blocked from team features.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void indieTierBlockedFromTeamFeatures() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path pluginRoot = createTempPluginRoot();
      Path projectDir = Files.createTempDirectory("project-");

      try
      {
        FeatureGate gate = FeatureGate.create(pluginRoot, mapper);
        FeatureGate.GateResult result = gate.check(projectDir, "multi-agent-orchestration");

        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.allowed(), "allowed").isFalse();
        requireThat(result.message(), "message").contains("requires team tier");
        requireThat(result.feature(), "feature").isEqualTo("multi-agent-orchestration");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that allowed features return empty message.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowedFeaturesHaveEmptyMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path pluginRoot = createTempPluginRoot();
      Path projectDir = Files.createTempDirectory("project-");

      try
      {
        FeatureGate gate = FeatureGate.create(pluginRoot, mapper);
        FeatureGate.GateResult result = gate.check(projectDir, "single-agent-execution");

        requireThat(result.allowed(), "allowed").isTrue();
        requireThat(result.message(), "message").isEqualTo("");
        requireThat(result.feature(), "feature").isEqualTo("single-agent-execution");
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that nonexistent features are blocked with appropriate message.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonexistentFeaturesBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path pluginRoot = createTempPluginRoot();
      Path projectDir = Files.createTempDirectory("project-");

      try
      {
        FeatureGate gate = FeatureGate.create(pluginRoot, mapper);
        FeatureGate.GateResult result = gate.check(projectDir, "nonexistent-feature");

        requireThat(result.allowed(), "allowed").isFalse();
        requireThat(result.message(), "message").contains("not available in any tier");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that null feature parameter throws exception.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nullFeatureThrowsException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path pluginRoot = createTempPluginRoot();
      Path projectDir = Files.createTempDirectory("project-");

      try
      {
        FeatureGate gate = FeatureGate.create(pluginRoot, mapper);

        try
        {
          gate.check(projectDir, null);
          requireThat(false, "shouldThrow").isTrue();
        }
        catch (NullPointerException e)
        {
          requireThat(e.getMessage(), "message").contains("feature");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Creates a temporary plugin root with minimal tiers.json.
   *
   * @return the plugin root path
   * @throws IOException if setup fails
   */
  private Path createTempPluginRoot() throws IOException
  {
    Path pluginRoot = Files.createTempDirectory("plugin-");
    Path configDir = pluginRoot.resolve("config");
    Files.createDirectories(configDir);

    String tiersJson = """
      {
        "tiers": {
          "indie": {
            "features": ["single-agent-execution", "basic-task-management"]
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
      """;

    Path tiersFile = configDir.resolve("tiers.json");
    Files.writeString(tiersFile, tiersJson);

    return pluginRoot;
  }
}
