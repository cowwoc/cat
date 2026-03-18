/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.PostToolUseFailureHook;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link PostToolUseFailureHook}, verifying that the failure tracking file is stored
 * under {@code getCatSessionPath()}.
 */
public final class PostToolUseFailureHookTest
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
   * Verifies that the failure tracking file is stored under {@code {catSessionPath}/}, not under
   * {@code {claudeSessionsPath}/{sessionId}/}.
   * <p>
   * After two consecutive failures, the tracking file must exist at
   * {@code {catSessionPath}/cat-failure-tracking-{sessionId}.count}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void failureTrackingFileStoredUnderCatSessionPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("post-tool-failure-hook-test-");
    try
    {
      try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
      {
        String sessionId = "test-session";
        JsonMapper mapper = scope.getJsonMapper();
        PostToolUseFailureHook hook = new PostToolUseFailureHook(scope);
        HookOutput output = new HookOutput(scope);

        // First failure
        HookInput input1 = createInput(mapper, sessionId, "Bash");
        hook.run(input1, output);

        // Second failure — triggers the warning and creates the tracking file
        HookInput input2 = createInput(mapper, sessionId, "Bash");
        hook.run(input2, output);

        // The tracking file must exist under {catSessionPath}, not under {claudeSessionsPath}/{sessionId}
        Path catSessionPath = scope.getCatSessionPath("test-session");
        Path trackingFile = catSessionPath.resolve("cat-failure-tracking-" + sessionId + ".count");
        requireThat(Files.exists(trackingFile), "trackingFileExistsUnderCatSessionPath").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
