/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.PostToolUseHook;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PostToolUseHook}, verifying that the failure counter reset operates on
 * the tracking file under {@code getCatSessionPath()}.
 */
public final class PostToolUseHookTest
{
  /**
   * Creates a HookInput from a JSON string with the given session ID and tool name.
   *
   * @param mapper the JSON mapper
   * @param sessionId the session ID to embed in the hook input
   * @param toolName the tool name
   * @return the parsed HookInput
   */
  private static HookInput createInput(JsonMapper mapper, String sessionId, String toolName)
  {
    String json = """
      {
        "session_id": "%s",
        "tool_name": "%s",
        "tool_result": {}
      }
      """.formatted(sessionId, toolName);
    return HookInput.readFrom(mapper, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Verifies that the failure counter reset (ResetFailureCounter) deletes the tracking file from
   * {@code {catSessionPath}/}, not from {@code {claudeSessionsPath}/{sessionId}/}.
   * <p>
   * A pre-existing tracking file at {@code {catSessionPath}/cat-failure-tracking-{sessionId}.count}
   * must be deleted after a successful tool use.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void successfulToolUseDeletesTrackingFileUnderCatSessionPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("post-tool-use-hook-test-");
    try
    {
      Path claudeEnvFile = Files.createTempFile("test-env", ".sh");
      try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session", claudeEnvFile,
        TerminalType.WINDOWS_TERMINAL))
      {
        String sessionId = scope.getClaudeSessionId();
        JsonMapper mapper = scope.getJsonMapper();
        PostToolUseHook hook = new PostToolUseHook(scope);
        HookOutput output = new HookOutput(scope);

        // Pre-create a tracking file under the NEW path ({catSessionPath})
        Path catSessionPath = scope.getCatSessionPath(scope.getClaudeSessionId());
        Files.createDirectories(catSessionPath);
        Path trackingFile = catSessionPath.resolve("cat-failure-tracking-" + sessionId + ".count");
        Files.writeString(trackingFile, "3");

        // Run the hook — ResetFailureCounter should delete the tracking file
        HookInput input = createInput(mapper, sessionId, "Bash");
        hook.run(input, output);

        // Tracking file must have been deleted from {catSessionPath}
        requireThat(Files.exists(trackingFile), "trackingFileDeletedFromCatSessionPath").isFalse();
      }
      finally
      {
        Files.deleteIfExists(claudeEnvFile);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
