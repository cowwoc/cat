/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.PostToolUseFailureHook;
import org.testng.annotations.Test;

import java.io.IOException;
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
      String sessionId = "test-session";
      String payload = """
        {
          "session_id": "%s",
          "tool_name": "Bash",
          "tool_result": {}
        }
        """.formatted(sessionId);
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempDir, tempDir, tempDir))
      {
        PostToolUseFailureHook hook = new PostToolUseFailureHook(scope);

        // First failure
        hook.run(scope);

        // Second failure — triggers the warning and creates the tracking file
        hook.run(scope);

        // The tracking file must exist under {catSessionPath}, not under {claudeSessionsPath}/{sessionId}
        Path catSessionPath = scope.getCatSessionPath(sessionId);
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
