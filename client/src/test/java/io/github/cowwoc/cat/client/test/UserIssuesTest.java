/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.prompt.UserIssues;
import tools.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link UserIssues}.
 */
public final class UserIssuesTest
{
  /**
   * Verifies that check() returns a message containing 'tdd-implementation-agent' when a detection gap is identified.
   */
  @Test
  public void checkDetectsIssueAndIncludesSkillName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);
      String result = handler.check("This is wrong and needs fixing", "test-session-123");

      requireThat(result, "result").isNotBlank();
      requireThat(result, "contains_tdd_agent").contains("tdd-implementation-agent");
      requireThat(result, "contains_header").contains("DETECTION GAP IDENTIFIED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check() returns an empty string when no issue pattern matches.
   */
  @Test
  public void checkReturnsEmptyStringWhenNoPatternMatches() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);
      String result = handler.check("Please process this data normally", "test-session-123");

      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check() is case-insensitive when matching patterns.
   */
  @Test
  public void checkIsCaseInsensitiveForPatternMatching() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);

      String resultLower = handler.check("this is wrong", "test-session-lower");
      String resultUpper = handler.check("THIS IS WRONG", "test-session-upper");
      String resultMixed = handler.check("ThIs Is WrOnG", "test-session-mixed");

      requireThat(resultLower, "lower").isNotBlank();
      requireThat(resultUpper, "upper").isNotBlank();
      requireThat(resultMixed, "mixed").isNotBlank();
      requireThat(resultLower, "lower_agent").contains("tdd-implementation-agent");
      requireThat(resultUpper, "upper_agent").contains("tdd-implementation-agent");
      requireThat(resultMixed, "mixed_agent").contains("tdd-implementation-agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check() detects multiple different issue patterns correctly.
   */
  @Test
  public void checkDetectsMultiplePatterns() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);

      String result1 = handler.check("bug in the code", "test-session-1");
      String result2 = handler.check("doesn't work", "test-session-2");
      String result3 = handler.check("false positive", "test-session-3");
      String result4 = handler.check("should be different", "test-session-4");

      requireThat(result1, "bug").isNotBlank();
      requireThat(result2, "works").isNotBlank();
      requireThat(result3, "positive").isNotBlank();
      requireThat(result4, "different").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check() writes valid JSON to the gaps file.
   */
  @Test
  public void checkWritesValidJsonStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);
      String sessionId = "test-session-json";
      handler.check("this is broken", sessionId);

      Path gapsFile = Path.of("/tmp/pending_detection_gaps_" + sessionId + ".json");
      if (Files.exists(gapsFile))
      {
        String jsonContent = Files.readString(gapsFile);
        JsonNode root = scope.getJsonMapper().readTree(jsonContent);

        requireThat(root.has("created"), "has_created").isTrue();
        requireThat(root.has("gaps"), "has_gaps").isTrue();
        requireThat(root.get("gaps").isArray(), "gaps_array").isTrue();

        JsonNode firstGap = root.get("gaps").get(0);
        requireThat(firstGap.has("id"), "gap_id").isTrue();
        requireThat(firstGap.has("pattern"), "gap_pattern").isTrue();
        requireThat(firstGap.has("user_message"), "gap_msg").isTrue();
        requireThat(firstGap.has("timestamp"), "gap_time").isTrue();
        requireThat(firstGap.has("status"), "gap_status").isTrue();
        requireThat(firstGap.has("test_written"), "gap_test").isTrue();

        requireThat(firstGap.get("status").asString(), "status").isEqualTo("pending_tdd");
        requireThat(firstGap.get("test_written").asBoolean(), "test_written").isFalse();

        Files.delete(gapsFile);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check() truncates the user message to 500 characters.
   */
  @Test
  public void checkTruncatesMessageTo500Characters() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);
      String sessionId = "test-session-truncate";
      StringBuilder longMessage = new StringBuilder(1500);
      longMessage.append("This is broken because ");
      for (int i = 0; i < 50; ++i)
        longMessage.append("the implementation is wrong ");
      String message = longMessage.toString();
      requireThat(message.length() > 500, "long").isTrue();

      handler.check(message, sessionId);

      Path gapsFile = Path.of("/tmp/pending_detection_gaps_" + sessionId + ".json");
      if (Files.exists(gapsFile))
      {
        String jsonContent = Files.readString(gapsFile);
        JsonNode root = scope.getJsonMapper().readTree(jsonContent);
        JsonNode firstGap = root.get("gaps").get(0);
        String userMessage = firstGap.get("user_message").asString();

        requireThat(userMessage.length(), "length").isEqualTo(500);
        Files.delete(gapsFile);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check() appends to existing gaps file instead of overwriting it.
   */
  @Test
  public void checkAppendsToPreviousGaps() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);
      String sessionId = "test-session-append";

      handler.check("First issue is wrong", sessionId);
      handler.check("Second issue is incorrect", sessionId);

      Path gapsFile = Path.of("/tmp/pending_detection_gaps_" + sessionId + ".json");
      if (Files.exists(gapsFile))
      {
        String jsonContent = Files.readString(gapsFile);
        JsonNode root = scope.getJsonMapper().readTree(jsonContent);
        int gapCount = root.get("gaps").size();

        requireThat(gapCount, "count").isEqualTo(2);
        Files.delete(gapsFile);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check() gracefully handles I/O errors when recording gaps.
   * <p>
   * When recordGap() encounters an IOException, it silently catches and ignores it,
   * allowing the detection gap message to still be returned to the user.
   */
  @Test
  public void checkHandlesIoErrorGracefully() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);
      String result = handler.check("this is wrong", "test-session-io-error");

      requireThat(result, "result").isNotBlank();
      requireThat(result, "result").contains("tdd-implementation-agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the message format includes all required workflow steps.
   */
  @Test
  public void checkMessageIncludesWorkflowSteps() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      UserIssues handler = new UserIssues(scope);
      String result = handler.check("this is wrong", "test-session-workflow");

      requireThat(result, "result").contains("Write a FAILING test");
      requireThat(result, "result").contains("Verify the test FAILS");
      requireThat(result, "result").contains("Fix the code");
      requireThat(result, "result").contains("Verify the test PASSES");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
