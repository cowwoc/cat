/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetConfigOutput;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for Config loading and GetConfigOutput formatting.
 * <p>
 * Tests verify that configuration loading handles missing files, partial configs,
 * invalid JSON, and that GetConfigOutput formats settings correctly.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class ConfigTest
{
  /**
   * Verifies that Config uses default trust=medium when config file is missing.
   */
  @Test
  public void configUsesDefaultTrustWhenConfigMissing() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      String trust = config.getString("trust");

      requireThat(trust, "trust").isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config uses default verify=changed when config file is missing.
   */
  @Test
  public void configUsesDefaultVerifyWhenConfigMissing() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      String verify = config.getString("verify");

      requireThat(verify, "verify").isEqualTo("changed");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config uses default autoRemoveWorktrees=true when config file is missing.
   */
  @Test
  public void configUsesDefaultAutoRemoveWhenConfigMissing() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      boolean autoRemove = config.getBoolean("autoRemoveWorktrees", false);

      requireThat(autoRemove, "autoRemove").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config reads values from cat-config.json when it exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void configReadsValuesFromFile() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "trust": "high",
          "verify": "all",
          "autoRemoveWorktrees": false
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getString("trust"), "trust").isEqualTo("high");
      requireThat(config.getString("verify"), "verify").isEqualTo("all");
      requireThat(config.getBoolean("autoRemoveWorktrees", true), "autoRemove").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config uses defaults for missing keys in a partial config file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void configUsesDefaultsForMissingKeys() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "trust": "low"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getString("trust"), "trust").isEqualTo("low");
      requireThat(config.getString("verify"), "verify").isEqualTo("changed");
      requireThat(config.getBoolean("autoRemoveWorktrees", false), "autoRemove").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() throws JacksonException for invalid JSON.
   * <p>
   * Jackson 3.x throws JacksonException (which extends IOException) for parse errors,
   * so invalid JSON propagates directly to the caller.
   *
   * @throws IOException if an I/O error occurs creating test files
   */
  @Test(expectedExceptions = JacksonException.class)
  public void configThrowsOnInvalidJson() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{ invalid json }");

      Config.load(mapper, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.asMap() returns all default values when no config file exists.
   */
  @Test
  public void configAsMapReturnsAllDefaults() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      Map<String, Object> values = config.asMap();

      requireThat(values.get("trust"), "trust").isEqualTo("medium");
      requireThat(values.get("verify"), "verify").isEqualTo("changed");
      requireThat(values.get("autoRemoveWorktrees"), "autoRemoveWorktrees").isEqualTo(true);
      requireThat(values.get("curiosity"), "curiosity").isEqualTo("low");
      requireThat(values.get("patience"), "patience").isEqualTo("high");
      requireThat(values.get("terminalWidth"), "terminalWidth").isEqualTo(120);
      requireThat(values.get("completionWorkflow"), "completionWorkflow").isEqualTo("merge");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that GetConfigOutput formats settings display correctly with values from config file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getConfigOutputFormatsSettingsCorrectly() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "trust": "high",
          "verify": "all",
          "curiosity": "medium",
          "patience": "low",
          "autoRemoveWorktrees": false
        }
        """);

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("CURRENT SETTINGS");
      requireThat(result, "result").contains("Trust: high");
      requireThat(result, "result").contains("Verify: all");
      requireThat(result, "result").contains("Curiosity: medium");
      requireThat(result, "result").contains("Patience: low");
      requireThat(result, "result").contains("Keep");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getReviewThresholds returns defaults when config file is missing.
   */
  @Test
  public void reviewThresholdsReturnsDefaultsWhenConfigMissing() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      Map<String, Object> thresholds = config.getReviewThresholds();

      requireThat(thresholds, "thresholds").isNotNull();
      requireThat(thresholds.get("autofix").toString(), "autofix").isEqualTo("high_and_above");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getAutofixLevel returns "high_and_above" by default.
   */
  @Test
  public void autofixLevelDefaultsToHighAndAbove() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixLevel(), "autofixLevel").isEqualTo("high_and_above");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getProceedLimit returns expected defaults matching current hardcoded behavior.
   */
  @Test
  public void proceedLimitDefaultsMatchCurrentBehavior() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getProceedLimit("critical"), "critical").isEqualTo(0);
      requireThat(config.getProceedLimit("high"), "high").isEqualTo(0);
      requireThat(config.getProceedLimit("medium"), "medium").isEqualTo(0);
      requireThat(config.getProceedLimit("low"), "low").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that custom reviewThresholds are read from cat-config.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewThresholdsReadFromConfigFile() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThresholds": {
            "autofix": "all",
            "proceed": { "critical": 0, "high": 0, "medium": 0, "low": 2147483647 }
          }
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixLevel(), "autofixLevel").isEqualTo("all");
      requireThat(config.getProceedLimit("critical"), "critical").isEqualTo(0);
      requireThat(config.getProceedLimit("high"), "high").isEqualTo(0);
      requireThat(config.getProceedLimit("medium"), "medium").isEqualTo(0);
      requireThat(config.getProceedLimit("low"), "low").isEqualTo(Integer.MAX_VALUE);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that partial reviewThresholds config falls back to defaults for missing fields.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewThresholdsPartialConfigFallsBackToDefaults() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThresholds": {
            "autofix": "critical"
          }
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixLevel(), "autofixLevel").isEqualTo("critical");
      // proceed limits should fall back to defaults since no "proceed" key present
      requireThat(config.getProceedLimit("critical"), "critical").isEqualTo(0);
      requireThat(config.getProceedLimit("high"), "high").isEqualTo(0);
      requireThat(config.getProceedLimit("medium"), "medium").isEqualTo(0);
      requireThat(config.getProceedLimit("low"), "low").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that asMap includes reviewThresholds in defaults.
   */
  @Test
  public void configAsMapIncludesReviewThresholds() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      Map<String, Object> values = config.asMap();

      requireThat(values.containsKey("reviewThresholds"), "hasReviewThresholds").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getProceedLimit throws NullPointerException for null severity.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getProceedLimitThrowsForNullSeverity() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      config.getProceedLimit(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getProceedLimit throws IllegalArgumentException for blank severity.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getProceedLimitThrowsForBlankSeverity() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      config.getProceedLimit("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getAutofixLevel throws IllegalArgumentException for an unrecognized value.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getAutofixLevelThrowsForInvalidValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThresholds": {
            "autofix": "bad_value"
          }
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getAutofixLevel();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getProceedLimit throws IllegalArgumentException for a negative value.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getProceedLimitThrowsForNegativeValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThresholds": {
            "proceed": { "critical": -1 }
          }
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getProceedLimit("critical");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a partial proceed map falls back to defaults for missing severity keys.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewThresholdsPartialProceedMapFallsBackToDefaults() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThresholds": {
            "autofix": "critical",
            "proceed": { "critical": 1 }
          }
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getProceedLimit("critical"), "critical").isEqualTo(1);
      requireThat(config.getProceedLimit("high"), "high").isEqualTo(0);
      requireThat(config.getProceedLimit("medium"), "medium").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that cat-config.local.json overrides values from cat-config.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void localConfigOverridesBaseConfig() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "trust": "medium"
        }
        """);
      Files.writeString(catDir.resolve("cat-config.local.json"), """
        {
          "trust": "high"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getString("trust"), "trust").isEqualTo("high");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a temporary directory for test isolation.
   *
   * @return the path to the created temporary directory
   */
  private Path createTempDir()
  {
    try
    {
      return Files.createTempDirectory("config-test");
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}
