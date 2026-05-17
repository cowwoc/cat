/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.agent.Config;
import io.github.cowwoc.cat.tool.util.UpdateConfig;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for the UpdateConfig CLI tool.
 * <p>
 * Each test is self-contained: it creates a temporary directory, constructs a {@link TestClaudeTool}
 * pointing at that directory, and passes it to {@link UpdateConfig#run} with an injectable output stream.
 */
public class UpdateConfigTest
{
  /**
   * Verifies that writing a single key updates config.json with the expected value.
   */
  @Test
  public void singleKeyUpdate() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=high"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"OK\"");

      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      String configContent = Files.readString(configPath);
      requireThat(configContent, "configContent").contains("\"trust\"");
      requireThat(configContent, "configContent").contains("\"high\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple keys written in a single invocation are all persisted to config.json.
   */
  @Test
  public void multipleKeysAtOnce() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=low", "caution=high"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"OK\"");

      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      String configContent = Files.readString(configPath);
      requireThat(configContent, "configContent").contains("\"trust\"");
      requireThat(configContent, "configContent").contains("\"low\"");
      requireThat(configContent, "configContent").contains("\"caution\"");
      requireThat(configContent, "configContent").contains("\"high\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unknown key returns an error response.
   */
  @Test
  public void unknownKeyReturnsError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"foo=bar"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an argument without an equals sign returns an error response.
   */
  @Test
  public void malformedArgNoEqualsReturnsError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trusthigh"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blank key returns an error response.
   */
  @Test
  public void blankKeyReturnsError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"=medium"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an invalid enum value for a known key returns an error response.
   */
  @Test
  public void invalidEnumValueReturnsError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=invalid"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a non-integer value for a width key returns an error response.
   */
  @Test
  public void invalidIntegerWidthReturnsError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"fileWidth=abc"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an integer value below the minimum (40) for a width key returns an error response.
   */
  @Test
  public void outOfRangeWidthReturnsError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"fileWidth=5"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that valid width boundary values are accepted.
   */
  @Test
  public void widthBoundariesAreAccepted() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"fileWidth=40", "displayWidth=200"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"OK\"");

      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      String configContent = Files.readString(configPath);
      Map<String, Object> parsed = scope.getJsonMapper().readValue(configContent,
        new tools.jackson.core.type.TypeReference<>()
        {
        });
      requireThat(parsed.get("fileWidth"), "fileWidth").isEqualTo(40);
      requireThat(parsed.get("displayWidth"), "displayWidth").isEqualTo(200);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that width values just outside valid boundaries return an error response.
   */
  @Test
  public void widthValuesOutsideBoundariesReturnError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream lowBuffer = new ByteArrayOutputStream();
      PrintStream lowOut = new PrintStream(lowBuffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"fileWidth=39"}, lowOut);
      requireThat(lowBuffer.toString(StandardCharsets.UTF_8), "lowOutput").contains("\"status\":\"ERROR\"");

      ByteArrayOutputStream highBuffer = new ByteArrayOutputStream();
      PrintStream highOut = new PrintStream(highBuffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"displayWidth=201"}, highOut);
      requireThat(highBuffer.toString(StandardCharsets.UTF_8), "highOutput").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an invalid value for completionWorkflow returns an error response.
   */
  @Test
  public void invalidCompletionWorkflowValueReturnsError() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"completionWorkflow=invalid"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that config.json is created when it does not exist yet.
   */
  @Test
  public void configFileCreatedIfMissing() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Ensure config.json does not exist before the run
      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      requireThat(Files.notExists(configPath), "configMissing").isTrue();

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=medium"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8);
      requireThat(output, "output").contains("\"status\":\"OK\"");
      requireThat(Files.exists(configPath), "configExists").isTrue();

      String configContent = Files.readString(configPath);
      requireThat(configContent, "configContent").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that setting an existing override back to its default removes the key from raw config.json.
   */
  @Test
  public void defaultValueUpdateRemovesExistingOverride() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream firstBuffer = new ByteArrayOutputStream();
      PrintStream firstOut = new PrintStream(firstBuffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=high"}, firstOut);
      requireThat(firstBuffer.toString(StandardCharsets.UTF_8), "firstOutput").contains("\"status\":\"OK\"");

      ByteArrayOutputStream secondBuffer = new ByteArrayOutputStream();
      PrintStream secondOut = new PrintStream(secondBuffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=medium"}, secondOut);
      requireThat(secondBuffer.toString(StandardCharsets.UTF_8), "secondOutput").contains("\"status\":\"OK\"");

      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      String configContent = Files.readString(configPath);
      Map<String, Object> parsed = scope.getJsonMapper().readValue(configContent,
        new tools.jackson.core.type.TypeReference<>()
        {
        });
      requireThat(parsed.containsKey("trust"), "containsTrust").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that removing a default-valued key preserves unrelated non-default overrides.
   */
  @Test
  public void defaultValueUpdatePreservesOtherOverrides() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream firstBuffer = new ByteArrayOutputStream();
      PrintStream firstOut = new PrintStream(firstBuffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=high", "fileWidth=100"}, firstOut);
      requireThat(firstBuffer.toString(StandardCharsets.UTF_8), "firstOutput").contains("\"status\":\"OK\"");

      ByteArrayOutputStream secondBuffer = new ByteArrayOutputStream();
      PrintStream secondOut = new PrintStream(secondBuffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=medium"}, secondOut);
      requireThat(secondBuffer.toString(StandardCharsets.UTF_8), "secondOutput").contains("\"status\":\"OK\"");

      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      String configContent = Files.readString(configPath);
      Map<String, Object> parsed = scope.getJsonMapper().readValue(configContent,
        new tools.jackson.core.type.TypeReference<>()
        {
        });
      requireThat(parsed.containsKey("trust"), "containsTrust").isFalse();
      requireThat(parsed.get("fileWidth"), "fileWidth").isEqualTo(100);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that writing only default values yields an empty raw config object.
   */
  @Test
  public void defaultOnlyBatchWritesEmptyObject() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{
        "trust=medium",
        "caution=medium",
        "curiosity=medium",
        "perfection=medium",
        "verbosity=medium",
        "completionWorkflow=merge",
        "minSeverity=low",
        "fileWidth=120",
        "displayWidth=120",
        "license="
      }, out);
      requireThat(buffer.toString(StandardCharsets.UTF_8), "output").contains("\"status\":\"OK\"");

      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      String configContent = Files.readString(configPath);
      requireThat(configContent, "configContent").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-default updates continue to persist unchanged.
   */
  @Test
  public void nonDefaultValuesStillPersist() throws Exception
  {
    Path tempDir = Files.createTempDirectory("update-config-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      UpdateConfig.run(scope, new String[]{"trust=low", "fileWidth=100"}, out);
      requireThat(buffer.toString(StandardCharsets.UTF_8), "output").contains("\"status\":\"OK\"");

      Path configPath = tempDir.resolve(Config.CAT_DIR_NAME).resolve("config.json");
      String configContent = Files.readString(configPath);
      Map<String, Object> parsed = scope.getJsonMapper().readValue(configContent,
        new tools.jackson.core.type.TypeReference<>()
        {
        });
      requireThat(parsed.get("trust"), "trust").isEqualTo("low");
      requireThat(parsed.get("fileWidth"), "fileWidth").isEqualTo(100);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
