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
import io.github.cowwoc.cat.hooks.util.ConcernSeverity;
import io.github.cowwoc.cat.hooks.util.EffortLevel;
import io.github.cowwoc.cat.hooks.util.PatienceLevel;
import io.github.cowwoc.cat.hooks.util.TrustLevel;
import io.github.cowwoc.cat.hooks.util.VerifyLevel;
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
    Path tempDir = TestUtils.createTempDir("config-test");
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
    Path tempDir = TestUtils.createTempDir("config-test");
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
   * Verifies that Config reads values from config.json when it exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void configReadsValuesFromFile() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "high",
          "verify": "all"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getString("trust"), "trust").isEqualTo("high");
      requireThat(config.getString("verify"), "verify").isEqualTo("all");
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
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "low"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getString("trust"), "trust").isEqualTo("low");
      requireThat(config.getString("verify"), "verify").isEqualTo("changed");
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
  @Test(expectedExceptions = JacksonException.class,
    expectedExceptionsMessageRegExp = ".*Unexpected character.*")
  public void configThrowsOnInvalidJson() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{ invalid json }");

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
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      Map<String, Object> values = config.asMap();

      requireThat(values.get("trust"), "trust").isEqualTo("medium");
      requireThat(values.get("verify"), "verify").isEqualTo("changed");
      requireThat(values.get("effort"), "effort").isEqualTo("medium");
      requireThat(values.get("patience"), "patience").isEqualTo("high");
      requireThat(values.get("fileWidth"), "fileWidth").isEqualTo(120);
      requireThat(values.get("displayWidth"), "displayWidth").isEqualTo(120);
      requireThat(values.get("completionWorkflow"), "completionWorkflow").isEqualTo("merge");
      requireThat(values.get("minSeverity"), "minSeverity").isEqualTo("low");
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
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "high",
          "verify": "all",
          "effort": "medium",
          "patience": "low"
        }
        """);

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getCurrentSettings(tempDir);

      requireThat(result, "result").contains("CURRENT SETTINGS");
      requireThat(result, "result").contains("Trust: high");
      requireThat(result, "result").contains("Verify: all");
      requireThat(result, "result").contains("Effort: medium");
      requireThat(result, "result").contains("Patience: low");
      requireThat(result, "result").doesNotContain("Curiosity");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }


  /**
   * Verifies that config.local.json overrides values from config.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void localConfigOverridesBaseConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "medium"
        }
        """);
      Files.writeString(catDir.resolve("config.local.json"), """
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
   * Verifies that null scope throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorRejectsNullScope()
  {
    new GetConfigOutput(null);
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when an unknown page name is passed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown page.*")
  public void getOutputRejectsUnknownPage() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetConfigOutput generator = new GetConfigOutput(scope);
      generator.getOutput(new String[]{"unexpected"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput returns settings box when a config.json file exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputContainsExpectedBoxHeaders() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "medium",
          "verify": "changed"
        }
        """);

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getOutput(new String[]{"settings"});

      requireThat(result, "result").isNotNull();
      requireThat(result, "result").contains("CURRENT SETTINGS");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput returns null for settings page when no config.json file exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputWorksWithoutConfigFile() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getOutput(new String[]{"settings"});

      requireThat(result, "result").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput propagates JacksonException when config file contains invalid JSON.
   *
   * @throws IOException if an I/O error occurs creating test files
   */
  @Test(expectedExceptions = JacksonException.class,
    expectedExceptionsMessageRegExp = ".*Unexpected character.*")
  public void getOutputPropagatesIOException() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{ invalid json }");

      GetConfigOutput handler = new GetConfigOutput(scope);
      handler.getOutput(new String[]{"settings"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getTrust() returns MEDIUM by default when the config file is missing.
   */
  @Test
  public void getTrustDefaultsToMedium() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getTrust(), "trust").isEqualTo(TrustLevel.MEDIUM);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getTrust() parses "high" from the config file correctly.
   */
  @Test
  public void getTrustParsesHighFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "high"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getTrust(), "trust").isEqualTo(TrustLevel.HIGH);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getTrust() throws IllegalArgumentException for an unrecognized trust value in the config file.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void getTrustThrowsForInvalidValue() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "unknown"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getTrust();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getVerify() returns CHANGED by default when the config file is missing.
   */
  @Test
  public void getVerifyDefaultsToChanged() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getVerify(), "verify").isEqualTo(VerifyLevel.CHANGED);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getVerify() parses "all" from the config file correctly.
   */
  @Test
  public void getVerifyParsesAllFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "verify": "all"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getVerify(), "verify").isEqualTo(VerifyLevel.ALL);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getVerify() throws IllegalArgumentException for an unrecognized verify value in the config file.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void getVerifyThrowsForInvalidValue() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "verify": "unknown"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getVerify();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getEffort() returns MEDIUM by default when the config file is missing.
   */
  @Test
  public void getEffortDefaultsToMedium() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getEffort(), "effort").isEqualTo(EffortLevel.MEDIUM);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getEffort() parses "high" from the config file correctly.
   */
  @Test
  public void getEffortParsesHighFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "effort": "high"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getEffort(), "effort").isEqualTo(EffortLevel.HIGH);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getEffort() parses "medium" from the config file correctly.
   */
  @Test
  public void getEffortParsesMediumFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "effort": "medium"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getEffort(), "effort").isEqualTo(EffortLevel.MEDIUM);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getEffort() throws IllegalArgumentException for an unrecognized effort value in the config
   * file.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void getEffortThrowsForInvalidValue() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "effort": "unknown"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getEffort();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPatience() returns HIGH by default when the config file is missing.
   */
  @Test
  public void getPatienceDefaultsToHigh() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getPatience(), "patience").isEqualTo(PatienceLevel.HIGH);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPatience() parses "low" from the config file correctly.
   */
  @Test
  public void getPatienceParsesLowFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "patience": "low"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getPatience(), "patience").isEqualTo(PatienceLevel.LOW);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPatience() throws IllegalArgumentException for an unrecognized patience value in the config
   * file.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void getPatienceThrowsForInvalidValue() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "patience": "unknown"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getPatience();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getMinSeverity() returns LOW by default when the config file is missing.
   */
  @Test
  public void getMinSeverityDefaultsToLow() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getMinSeverity(), "minSeverity").isEqualTo(ConcernSeverity.LOW);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getMinSeverity() parses "medium" from the config file correctly.
   */
  @Test
  public void getMinSeverityParsesMediumFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "minSeverity": "medium"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getMinSeverity(), "minSeverity").isEqualTo(ConcernSeverity.MEDIUM);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getMinSeverity() parses "high" from the config file correctly.
   */
  @Test
  public void getMinSeverityParsesHighFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "minSeverity": "high"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getMinSeverity(), "minSeverity").isEqualTo(ConcernSeverity.HIGH);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getMinSeverity() parses "critical" from the config file correctly.
   */
  @Test
  public void getMinSeverityParsesCriticalFromConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "minSeverity": "critical"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getMinSeverity(), "minSeverity").isEqualTo(ConcernSeverity.CRITICAL);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getMinSeverity() throws IllegalArgumentException for an unrecognized value in the config file.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void getMinSeverityThrowsForInvalidValue() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "minSeverity": "unknown"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getMinSeverity();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that asMap() returns both defaults and overridden values when a partial config is loaded.
   * <p>
   * Loads a config with some overridden values and verifies asMap() contains both the overridden
   * values and the remaining defaults.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void configAsMapContainsBothDefaultsAndOverrides() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "high",
          "minSeverity": "medium"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      Map<String, Object> values = config.asMap();

      // Overridden values
      requireThat(values.get("trust"), "trust").isEqualTo("high");
      requireThat(values.get("minSeverity"), "minSeverity").isEqualTo("medium");
      // Remaining defaults
      requireThat(values.get("verify"), "verify").isEqualTo("changed");
      requireThat(values.get("effort"), "effort").isEqualTo("medium");
      requireThat(values.get("patience"), "patience").isEqualTo("high");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that asMap includes minSeverity in defaults.
   */
  @Test
  public void configAsMapIncludesMinSeverityDefault() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);
      Map<String, Object> values = config.asMap();

      requireThat(values.containsKey("minSeverity"), "hasMinSeverity").isTrue();
      requireThat(values.get("minSeverity"), "minSeverity").isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() throws IllegalArgumentException for unknown config keys.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown configuration keys found.*")
  public void configRejectsUnknownKeys() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "medium",
          "reviewThreshold": "medium"
        }
        """);

      Config.load(mapper, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() throws IllegalArgumentException when unknown keys are encountered.
   * <p>
   * This test verifies the error message includes both the unknown key and the list of known keys.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void configUnknownKeyErrorIncludesKnownKeys() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "unknownKey": "value"
        }
        """);

      try
      {
        Config.load(mapper, tempDir);
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("unknownKey");
        requireThat(e.getMessage(), "message").contains("trust");
        requireThat(e.getMessage(), "message").contains("verify");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() rejects "license" in config.json because it is developer-specific
   * and must only appear in config.local.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*license.*user-specific.*config\\.local\\.json.*")
  public void configRejectsLicenseInBaseConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "license": "some-key"
        }
        """);

      Config.load(mapper, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() accepts "license" in config.local.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void configAcceptsLicenseInLocalConfig() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{}");
      Files.writeString(catDir.resolve("config.local.json"), """
        {
          "license": "some-key"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      requireThat(config.asMap().get("license"), "license").isEqualTo("some-key");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }


  /**
   * Verifies that getEffectiveConfig returns JSON with all defaults when no config file exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getEffectiveConfigReturnsAllDefaults() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getEffectiveConfig(tempDir);

      requireThat(result, "result").isNotNull();
      JsonMapper mapper = scope.getJsonMapper();
      Map<String, Object> parsed = mapper.readValue(result,
        new tools.jackson.core.type.TypeReference<>()
        {
        });
      requireThat(parsed.get("trust"), "trust").isEqualTo("medium");
      requireThat(parsed.get("verify"), "verify").isEqualTo("changed");
      requireThat(parsed.get("effort"), "effort").isEqualTo("medium");
      requireThat(parsed.get("patience"), "patience").isEqualTo("high");
      requireThat(parsed.get("fileWidth"), "fileWidth").isEqualTo(120);
      requireThat(parsed.get("displayWidth"), "displayWidth").isEqualTo(120);
      requireThat(parsed.get("completionWorkflow"), "completionWorkflow").isEqualTo("merge");
      requireThat(parsed.get("minSeverity"), "minSeverity").isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getEffectiveConfig merges file values with defaults for missing keys.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getEffectiveConfigMergesFileWithDefaults() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), """
        {
          "trust": "high",
          "verify": "all"
        }
        """);

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getEffectiveConfig(tempDir);

      JsonMapper mapper = scope.getJsonMapper();
      Map<String, Object> parsed = mapper.readValue(result,
        new tools.jackson.core.type.TypeReference<>()
        {
        });
      // Overridden values
      requireThat(parsed.get("trust"), "trust").isEqualTo("high");
      requireThat(parsed.get("verify"), "verify").isEqualTo("all");
      // Defaults for missing keys
      requireThat(parsed.get("effort"), "effort").isEqualTo("medium");
      requireThat(parsed.get("patience"), "patience").isEqualTo("high");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput with "effective" page returns valid JSON config.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputEffectiveReturnsJson() throws IOException
  {
    Path tempDir = TestUtils.createTempDir("config-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getOutput(new String[]{"effective"});

      requireThat(result, "result").isNotNull();
      requireThat(result, "result").contains("trust");
      requireThat(result, "result").contains("verify");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
