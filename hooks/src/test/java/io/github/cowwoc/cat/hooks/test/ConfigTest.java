package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetConfigOutput;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.core.JacksonException;

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
  public void configUsesDefaultTrustWhenConfigMissing()
  {
    Path tempDir = createTempDir();
    try
    {
      Config config = Config.load(tempDir);
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
  public void configUsesDefaultVerifyWhenConfigMissing()
  {
    Path tempDir = createTempDir();
    try
    {
      Config config = Config.load(tempDir);
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
  public void configUsesDefaultAutoRemoveWhenConfigMissing()
  {
    Path tempDir = createTempDir();
    try
    {
      Config config = Config.load(tempDir);
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
    try
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "trust": "high",
          "verify": "all",
          "autoRemoveWorktrees": false
        }
        """);

      Config config = Config.load(tempDir);

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
    try
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), """
        {
          "trust": "low"
        }
        """);

      Config config = Config.load(tempDir);

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
   * Jackson 3.x throws JacksonException (not IOException) for parse errors,
   * so invalid JSON is not silently caught by the IOException handler in Config.load().
   *
   * @throws IOException if an I/O error occurs creating test files
   */
  @Test(expectedExceptions = JacksonException.class)
  public void configThrowsOnInvalidJson() throws IOException
  {
    Path tempDir = createTempDir();
    try
    {
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("cat-config.json"), "{ invalid json }");

      Config.load(tempDir);
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
  public void configAsMapReturnsAllDefaults()
  {
    Path tempDir = createTempDir();
    try
    {
      Config config = Config.load(tempDir);
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
    try (JvmScope scope = new MainJvmScope())
    {
      Path tempDir = createTempDir();
      try
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
