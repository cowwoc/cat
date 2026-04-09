/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.EnforceStatusOutput;
import io.github.cowwoc.cat.claude.hook.JvmScope;
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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithBox(transcriptFile);

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), true, scope, "", null);

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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithoutBox(transcriptFile);

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), true, scope, "", null);

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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithBox(transcriptFile);

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, scope, "", null);

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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      writeTranscriptWithoutBox(transcriptFile);

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, scope, "", null);

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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
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

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, scope, "", null);

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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");
      Files.writeString(transcriptFile, "");

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, scope, "", null);

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
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path transcriptFile = tempDir.resolve("transcript.jsonl");

      String userLine = "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":" +
        "\"<command-name>cat:status</command-name>\"}]}}";
      String malformedLine = "this is not valid json {{{";
      String assistantLine = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\"," +
        "\"text\":\"╭── Status ──╮\\n│ v2.1       │\\n╰────────────╯\"}]}}";
      Files.writeString(transcriptFile, userLine + "\n" + malformedLine + "\n" + assistantLine + "\n");

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, scope, "", null);

      // Status was invoked and box was present, so hook should allow through
      requireThat(result.strip(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the stop hook blocks when a pending-agent-result flag is present at the correct
   * path ({@code .cat/work/sessions/{sessionId}/pending-agent-result}).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void pendingAgentResultFlagTriggersBlock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = scope.getSessionId();

      // Create the pending-agent-result flag at the correct path
      Path flagPath = scope.getCatSessionPath(sessionId).resolve("pending-agent-result");
      Files.createDirectories(flagPath.getParent());
      Files.createFile(flagPath);

      // Create a valid transcript so transcript path resolution succeeds
      Path sessionBasePath = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionBasePath);
      Path transcriptFile = sessionBasePath.resolve(sessionId + ".jsonl");
      writeTranscriptWithBox(transcriptFile);

      String result = EnforceStatusOutput.check(mapper, transcriptFile.toString(), false, scope,
        sessionId, sessionBasePath);

      // Hook must block due to pending-agent-result flag
      JsonNode resultNode = mapper.readTree(result);
      requireThat(resultNode.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(result, "result").contains("collect-results-agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies the workaround for https://github.com/anthropics/claude-code/issues/44450:
   * when {@code transcript_path} points to a non-existent worktree-derived path but the session
   * JSONL exists at {@code sessionBasePath.resolve(sessionId + ".jsonl")}, the hook reads from the
   * correct location and applies normal enforcement (blocks when box is missing).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void worktreeTranscriptPathFallsBackToSessionBasePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = scope.getSessionId();

      // Place the actual session file at the sessionBasePath-derived location
      Path sessionBasePath = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionBasePath);
      Path correctTranscript = sessionBasePath.resolve(sessionId + ".jsonl");
      writeTranscriptWithoutBox(correctTranscript);

      // Simulate a stale worktree-derived transcript_path that does not exist
      String staleTranscriptPath = tempDir.resolve("wrong-dir").resolve(sessionId + ".jsonl").toString();

      String result = EnforceStatusOutput.check(mapper, staleTranscriptPath, false, scope, sessionId,
        sessionBasePath);

      // Hook must have read the correct file and detected missing box → block
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
   * Verifies that when both {@code transcriptPath} is blank and {@code sessionId} is empty, the hook
   * returns empty (skips enforcement) because it has no session context at all.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void noTranscriptContextSkipsEnforcement() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      // No transcript path, no session ID — hook has no context, should skip enforcement
      String result = EnforceStatusOutput.check(mapper, "", false, scope, "", null);
      requireThat(result.strip(), "result").isEqualTo("{}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when {@code transcriptPath} points to a non-existent file, {@code sessionId} is
   * non-empty, but no fallback file exists at the session-base-path location, the hook throws
   * {@link IOException} as a fail-fast signal.
   * <p>
   * The {@code run()} method catches this exception and returns a block decision as a fail-safe.
   *
   * @throws IOException expected — hook throws when transcript is missing and fallback also absent
   */
  @Test(expectedExceptions = IOException.class)
  public void missingTranscriptWithNoFallbackThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = scope.getSessionId();
      // sessionBasePath directory exists but the session JSONL file does not
      Path sessionBasePath = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionBasePath);
      // Do NOT create sessionBasePath.resolve(sessionId + ".jsonl") — fallback must not exist
      String nonExistentPath = tempDir.resolve("does-not-exist.jsonl").toString();
      // Both primary and fallback paths are absent → hook must throw
      EnforceStatusOutput.check(mapper, nonExistentPath, false, scope, sessionId, sessionBasePath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
