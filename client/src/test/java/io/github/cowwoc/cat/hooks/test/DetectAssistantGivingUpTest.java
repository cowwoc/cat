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
import io.github.cowwoc.cat.hooks.tool.post.DetectAssistantGivingUp;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Tests for DetectAssistantGivingUp.
 */
public final class DetectAssistantGivingUpTest
{
  /**
   * Verifies that no warning is returned when no conversation log exists.
   */
  @Test
  public void noConversationLogAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Clock clock = Clock.systemUTC();
      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(clock, tempDir);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "nonexistent-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, "nonexistent-session", hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no warning is returned when conversation log contains no giving-up patterns.
   */
  @Test
  public void noGivingUpPatternAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-clean";
      Path conversationLog = createConversationLog(tempDir, sessionId, """
        {"role":"assistant","content":"I'll complete all the files."}
        {"role":"assistant","content":"Working on the next file now."}
        """);

      Clock clock = Clock.systemUTC();
      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(clock, tempDir);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that warning is returned when giving-up pattern is detected.
   */
  @Test
  public void givingUpPatternDetectedReturnsContext() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-violation";
      Path conversationLog = createConversationLog(tempDir, sessionId,
        """
        {"role":"assistant","content":"Given our token usage (139k/200k), let me complete a few more."}
        """);

      Clock clock = Clock.systemUTC();
      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(clock, tempDir);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").contains("TOKEN POLICY VIOLATION");
      requireThat(result.additionalContext(), "additionalContext").contains("PROHIBITED PATTERNS");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rate limiting prevents repeated checks within 60 seconds.
   */
  @Test
  public void rateLimitingPreventsRepeatedChecks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-ratelimit";
      Path conversationLog = createConversationLog(tempDir, sessionId,
        """
        {"role":"assistant","content":"Given our token usage (139k/200k), let me complete a few more."}
        """);

      Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");
      Clock clock1 = Clock.fixed(baseTime, ZoneOffset.UTC);
      DetectAssistantGivingUp handler1 = new DetectAssistantGivingUp(clock1, tempDir);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result1 = handler1.check("Bash", toolResult, sessionId, hookData);
      requireThat(result1.additionalContext(), "firstCheck").contains("TOKEN POLICY VIOLATION");

      Clock clock2 = Clock.fixed(baseTime.plusSeconds(30), ZoneOffset.UTC);
      DetectAssistantGivingUp handler2 = new DetectAssistantGivingUp(clock2, tempDir);

      PostToolHandler.Result result2 = handler2.check("Bash", toolResult, sessionId, hookData);
      requireThat(result2.additionalContext(), "secondCheck").isEmpty();

      Clock clock3 = Clock.fixed(baseTime.plusSeconds(61), ZoneOffset.UTC);
      DetectAssistantGivingUp handler3 = new DetectAssistantGivingUp(clock3, tempDir);

      PostToolHandler.Result result3 = handler3.check("Bash", toolResult, sessionId, hookData);
      requireThat(result3.additionalContext(), "thirdCheck").contains("TOKEN POLICY VIOLATION");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple giving-up patterns are detected.
   */
  @Test
  public void multiplePatternsDetected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-multipattern";

      String[] patterns = {
        "given our token usage, let me continue",
        "given the token usage, I'll optimize",
        "tokens used so let me finish",
        "tokens remaining, so I'll complete",
        "given our context, let me complete",
        "our token budget suggests a few more",
        "i've optimized some, let me do more then proceed"
      };

      Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");

      for (int i = 0; i < patterns.length; ++i)
      {
        String pattern = patterns[i];
        String uniqueSessionId = sessionId + "-" + i;
        Path conversationLog = createConversationLog(tempDir, uniqueSessionId,
          "{\"role\":\"assistant\",\"content\":\"" + pattern + "\"}");

        Instant checkTime = baseTime.plusSeconds(i * 100);
        Clock clock = Clock.fixed(checkTime, ZoneOffset.UTC);
        DetectAssistantGivingUp handler = new DetectAssistantGivingUp(clock, tempDir);

        String hookDataJson = """
          {
            "tool_input": {},
            "tool_result": {},
            "session_id": "%s"
          }""".formatted(uniqueSessionId);
        JsonNode hookData = mapper.readTree(hookDataJson);
        JsonNode toolResult = mapper.readTree("{}");

        PostToolHandler.Result result = handler.check("Bash", toolResult, uniqueSessionId, hookData);

        requireThat(result.additionalContext(), "pattern_" + i).contains("TOKEN POLICY VIOLATION");

        Files.deleteIfExists(conversationLog);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a conversation log file for testing.
   *
   * @param tempDir the temporary directory (used as claudeConfigDir)
   * @param sessionId the session ID
   * @param content the JSONL content
   * @return the path to the created log file
   * @throws IOException if file creation fails
   */
  private Path createConversationLog(Path tempDir, String sessionId, String content) throws IOException
  {
    Path projectsDir = tempDir.resolve("projects").resolve("-workspace");
    Files.createDirectories(projectsDir);
    Path logFile = projectsDir.resolve(sessionId + ".jsonl");
    Files.writeString(logFile, content);
    return logFile;
  }
}
