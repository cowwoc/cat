/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.tool.post.DetectTokenThreshold;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for DetectTokenThreshold.
 */
public final class DetectTokenThresholdTest
{
  /**
   * Verifies that no warning is returned when token usage is below the soft threshold.
   */
  @Test
  public void belowThresholdReturnsAllow() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-low";
      createSessionFile(tempDir, sessionId, 20_000, 10_000);

      DetectTokenThreshold handler = new DetectTokenThreshold(tempDir);
      JsonNode toolResult = mapper.readTree("{}");
      JsonNode hookData = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a soft warning is returned when token usage exceeds 60k.
   */
  @Test
  public void softWarningAt60k() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-soft";
      createSessionFile(tempDir, sessionId, 40_000, 25_000);

      DetectTokenThreshold handler = new DetectTokenThreshold(tempDir);
      JsonNode toolResult = mapper.readTree("{}");
      JsonNode hookData = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.additionalContext(), "additionalContext").contains("elevated");
      requireThat(result.additionalContext(), "additionalContext").contains("65000");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a strong warning is returned when token usage exceeds 80k.
   */
  @Test
  public void strongWarningAt80k() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-strong";
      createSessionFile(tempDir, sessionId, 50_000, 35_000);

      DetectTokenThreshold handler = new DetectTokenThreshold(tempDir);
      JsonNode toolResult = mapper.readTree("{}");
      JsonNode hookData = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.additionalContext(), "additionalContext").contains("very high");
      requireThat(result.additionalContext(), "additionalContext").contains("85000");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing session file returns allow gracefully.
   */
  @Test
  public void missingSessionFileReturnsAllow() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      DetectTokenThreshold handler = new DetectTokenThreshold(tempDir);
      JsonNode toolResult = mapper.readTree("{}");
      JsonNode hookData = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, "nonexistent", hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing token fields in session file returns allow gracefully.
   */
  @Test
  public void missingTokenFieldsReturnsAllow() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-empty";
      Path sessionsDir = tempDir.resolve("sessions");
      Files.createDirectories(sessionsDir);
      Files.writeString(sessionsDir.resolve(sessionId + ".json"), "{}");

      DetectTokenThreshold handler = new DetectTokenThreshold(tempDir);
      JsonNode toolResult = mapper.readTree("{}");
      JsonNode hookData = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a session file with the specified token counts.
   *
   * @param configDir the config directory
   * @param sessionId the session ID
   * @param inputTokens the input token count
   * @param outputTokens the output token count
   * @throws IOException if file creation fails
   */
  private void createSessionFile(Path configDir, String sessionId, long inputTokens,
    long outputTokens) throws IOException
  {
    Path sessionsDir = configDir.resolve("sessions");
    Files.createDirectories(sessionsDir);
    String json = """
      {
        "token_usage": {
          "input_tokens": %d,
          "output_tokens": %d
        }
      }""".formatted(inputTokens, outputTokens);
    Files.writeString(sessionsDir.resolve(sessionId + ".json"), json);
  }
}
