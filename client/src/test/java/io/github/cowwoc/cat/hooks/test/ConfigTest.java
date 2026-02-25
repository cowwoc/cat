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
import io.github.cowwoc.cat.hooks.util.CuriosityLevel;
import io.github.cowwoc.cat.hooks.util.PatienceLevel;
import io.github.cowwoc.cat.hooks.util.TrustLevel;
import io.github.cowwoc.cat.hooks.util.VerifyLevel;
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
   * Verifies that getAutofixThreshold returns "low" by default when no config file exists.
   */
  @Test
  public void autofixThresholdDefaultsToLow() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixThreshold(), "autofixThreshold").isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that reviewThreshold is read as a simple string from cat-config.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewThresholdReadFromConfigFile() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThreshold": "critical"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixThreshold(), "autofixThreshold").isEqualTo("critical");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that asMap includes reviewThreshold in defaults.
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

      requireThat(values.containsKey("reviewThreshold"), "hasReviewThresholds").isTrue();
      requireThat(values.get("reviewThreshold"), "reviewThreshold").isInstanceOf(String.class);
      requireThat((String) values.get("reviewThreshold"), "reviewThreshold").isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getAutofixThreshold throws IllegalArgumentException for an unrecognized value.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getAutofixThresholdThrowsForInvalidValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThreshold": "all"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getAutofixThreshold();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getAutofixThreshold returns "medium" when reviewThreshold is set to "medium" in config.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void autofixThresholdMediumReadFromConfigFile() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThreshold": "medium"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixThreshold(), "autofixThreshold").isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getAutofixThreshold returns "high" when reviewThreshold is set to "high" in config.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void autofixThresholdHighReadFromConfigFile() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThreshold": "high"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixThreshold(), "autofixThreshold").isEqualTo("high");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getAutofixThreshold falls back to DEFAULT_AUTOFIX_THRESHOLD when reviewThreshold is a non-String
   * value.
   * <p>
   * When a JSON number (e.g., 42) is stored in reviewThreshold, Jackson parses it as an Integer.
   * The getAutofixThreshold() method checks {@code instanceof String} and falls back to the default "low".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void autofixThresholdFallsBackForNonStringValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThreshold": 42
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getAutofixThreshold(), "autofixThreshold").isEqualTo(Config.DEFAULT_AUTOFIX_THRESHOLD);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getAutofixThreshold throws IllegalArgumentException when reviewThreshold uses uppercase.
   * <p>
   * Only lowercase values ("low", "medium", "high", "critical") are valid. Uppercase variants are rejected.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void autofixThresholdThrowsForUppercaseValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "reviewThreshold": "LOW"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getAutofixThreshold();
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
   * Verifies that null scope throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorRejectsNullScope()
  {
    new GetConfigOutput(null);
  }

  /**
   * Verifies that getOutput rejects non-empty arguments.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getOutputRejectsNonEmptyArguments() throws IOException
  {
    Path tempDir = createTempDir();
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
   * Verifies that getOutput returns non-null output containing expected box headers
   * when a cat-config.json file exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputContainsExpectedBoxHeaders() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "trust": "medium",
          "verify": "changed"
        }
        """);

      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getOutput(new String[0]);

      requireThat(result, "result").isNotNull();
      requireThat(result, "result").contains("CURRENT_SETTINGS:");
      requireThat(result, "result").contains("VERSION_CONDITIONS_OVERVIEW:");
      requireThat(result, "result").contains("CONFIGURATION_SAVED:");
      requireThat(result, "result").contains("NO_CHANGES:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput returns non-null output even when no cat-config.json file exists.
   * <p>
   * When CURRENT_SETTINGS returns null (no config file), the section header is still present
   * but contains no box content. The remaining sections are always present.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputWorksWithoutConfigFile() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetConfigOutput handler = new GetConfigOutput(scope);
      String result = handler.getOutput(new String[0]);

      requireThat(result, "result").isNotNull();
      // CURRENT_SETTINGS section header is present but has no box content (null when config missing)
      requireThat(result, "result").contains("CURRENT_SETTINGS:");
      requireThat(result, "result").doesNotContain("CURRENT SETTINGS");
      requireThat(result, "result").contains("VERSION_CONDITIONS_OVERVIEW:");
      requireThat(result, "result").contains("CONFIGURATION_SAVED:");
      requireThat(result, "result").contains("NO_CHANGES:");
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
  @Test(expectedExceptions = JacksonException.class)
  public void getOutputPropagatesIOException() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{ invalid json }");

      GetConfigOutput handler = new GetConfigOutput(scope);
      handler.getOutput(new String[0]);
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
    Path tempDir = createTempDir();
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
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getTrustThrowsForInvalidValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
    Path tempDir = createTempDir();
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
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getVerifyThrowsForInvalidValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
   * Verifies that getCuriosity() returns LOW by default when the config file is missing.
   */
  @Test
  public void getCuriosityDefaultsToLow() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Config config = Config.load(mapper, tempDir);

      requireThat(config.getCuriosity(), "curiosity").isEqualTo(CuriosityLevel.LOW);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCuriosity() parses "high" from the config file correctly.
   */
  @Test
  public void getCuriosityParsesHighFromConfig() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "curiosity": "high"
        }
        """);

      Config config = Config.load(mapper, tempDir);

      requireThat(config.getCuriosity(), "curiosity").isEqualTo(CuriosityLevel.HIGH);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCuriosity() throws IllegalArgumentException for an unrecognized curiosity value in the config
   * file.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getCuriosityThrowsForInvalidValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "curiosity": "unknown"
        }
        """);

      Config config = Config.load(mapper, tempDir);
      config.getCuriosity();
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
    Path tempDir = createTempDir();
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
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getPatienceThrowsForInvalidValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
    Path tempDir = createTempDir();
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
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getMinSeverityThrowsForInvalidValue() throws IOException
  {
    Path tempDir = createTempDir();
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
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
   * Verifies that asMap includes minSeverity in defaults.
   */
  @Test
  public void configAsMapIncludesMinSeverityDefault() throws IOException
  {
    Path tempDir = createTempDir();
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
