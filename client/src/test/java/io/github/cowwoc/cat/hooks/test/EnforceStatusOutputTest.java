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

      requireThat(result, "result").contains("\"decision\"");
      requireThat(result, "result").contains("\"block\"");
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

      requireThat(result, "result").contains("\"decision\"");
      requireThat(result, "result").contains("\"block\"");
      requireThat(result, "result").contains("M402");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
