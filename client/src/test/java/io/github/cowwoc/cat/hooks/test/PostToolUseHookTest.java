/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.PostToolUseHook;
import org.testng.annotations.Test;

import java.io.IOException;
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
        PostToolUseHook hook = new PostToolUseHook(scope);

        // Pre-create a tracking file under the NEW path ({catSessionPath})
        Path catSessionPath = scope.getCatSessionPath(sessionId);
        Files.createDirectories(catSessionPath);
        Path trackingFile = catSessionPath.resolve("cat-failure-tracking-" + sessionId + ".count");
        Files.writeString(trackingFile, "3");

        // Run the hook — ResetFailureCounter should delete the tracking file
        hook.run(scope);

        // Tracking file must have been deleted from {catSessionPath}
        requireThat(Files.exists(trackingFile), "trackingFileDeletedFromCatSessionPath").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
