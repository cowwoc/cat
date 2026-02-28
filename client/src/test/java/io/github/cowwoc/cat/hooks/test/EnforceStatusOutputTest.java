/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.EnforceStatusOutput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.JvmScope;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link EnforceStatusOutput} stop-hook-active behavior.
 */
public final class EnforceStatusOutputTest
{
  /**
   * Writes a mock transcript JSONL with a user message containing /cat:status and an assistant message
   * containing the status box characters.
   *
   * @param transcriptFile the path to write the transcript to
   * @throws IOException if writing fails
   */
  private static void writeTranscriptWithBox(Path transcriptFile) throws IOException
  {
    String userLine = "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":" +
      "\"<command-name>cat:status</command-name>\"}]}}";
    String assistantLine = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\"," +
      "\"text\":\"╭── Status ──╮\\n│ v2.1       │\\n╰────────────╯\"}]}}";
    Files.writeString(transcriptFile, userLine + "\n" + assistantLine + "\n");
  }

  /**
   * Writes a mock transcript JSONL with a user message containing /cat:status but NO box in the assistant
   * response.
   *
   * @param transcriptFile the path to write the transcript to
   * @throws IOException if writing fails
   */
  private static void writeTranscriptWithoutBox(Path transcriptFile) throws IOException
  {
    String userLine = "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":" +
      "\"<command-name>cat:status</command-name>\"}]}}";
    String assistantLine = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\"," +
      "\"text\":\"The project is in progress with several open issues.\"}]}}";
    Files.writeString(transcriptFile, userLine + "\n" + assistantLine + "\n");
  }

  /**
   * Verifies that when {@code stop_hook_active=true} and the status box IS present in the transcript,
   * the hook returns empty (allows the response through).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void stopHookActiveWithBoxPresent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithBox(transcriptFile);

      HookOutput hookOutput = new HookOutput(scope);
      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), true, hookOutput);

      requireThat(result.trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when {@code stop_hook_active=true} and the status box is still MISSING from the
   * transcript, the hook blocks with a fail-fast error message instructing the user to retry.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void stopHookActiveWithBoxMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithoutBox(transcriptFile);

      HookOutput hookOutput = new HookOutput(scope);
      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), true, hookOutput);

      JsonNode resultNode = mapper.readTree(result);
      requireThat(resultNode.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(result, "result").contains("/cat:status");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when {@code stop_hook_active=false} and the status box IS present, the hook returns
   * empty (existing pass-through behavior).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void firstAttemptWithBoxPresent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithBox(transcriptFile);

      HookOutput hookOutput = new HookOutput(scope);
      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, hookOutput);

      requireThat(result.trim(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when {@code stop_hook_active=false} and the status box is missing, the hook blocks
   * (existing enforcement behavior).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void firstAttemptWithBoxMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithoutBox(transcriptFile);

      HookOutput hookOutput = new HookOutput(scope);
      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, hookOutput);

      JsonNode resultNode = mapper.readTree(result);
      requireThat(resultNode.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(result, "result").contains("M402");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a status invocation beyond the recent 10-line window is not detected, so the hook
   * returns empty (no enforcement triggered).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void statusBeyondRecentWindow() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");

      // Write status invocation, then 15 filler lines to push it out of the 10-line window
      // The status line itself is ~105 chars; 15 filler lines are ~90 chars each = ~1455 total
      StringBuilder sb = new StringBuilder(1600);
      sb.append("{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":" +
        "\"<command-name>cat:status</command-name>\"}]}}\n");
      for (int i = 0; i < 15; ++i)
      {
        String fillerLine = "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"filler " +
          i + "\"}]}}\n";
        sb.append(fillerLine);
      }
      Files.writeString(transcriptFile, sb.toString());

      HookOutput hookOutput = new HookOutput(scope);
      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, hookOutput);

      requireThat(result.strip(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an empty transcript file causes the hook to return empty (no enforcement triggered).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void emptyTranscriptFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      Files.writeString(transcriptFile, "");

      HookOutput hookOutput = new HookOutput(scope);
      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, hookOutput);

      requireThat(result.strip(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a malformed JSON line in the transcript does not prevent the hook from detecting
   * both the status invocation and the box output on surrounding valid lines.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void malformedJsonInTranscript() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");

      String userLine = "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":" +
        "\"<command-name>cat:status</command-name>\"}]}}";
      String malformedLine = "this is not valid json {{{";
      String assistantLine = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\"," +
        "\"text\":\"╭── Status ──╮\\n│ v2.1       │\\n╰────────────╯\"}]}}";
      Files.writeString(transcriptFile, userLine + "\n" + malformedLine + "\n" + assistantLine + "\n");

      HookOutput hookOutput = new HookOutput(scope);
      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, hookOutput);

      // Status was invoked and box was present, so hook should allow through
      requireThat(result.strip(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
