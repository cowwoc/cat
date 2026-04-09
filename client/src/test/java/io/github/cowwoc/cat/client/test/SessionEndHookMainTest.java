/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookResult;
import io.github.cowwoc.cat.claude.hook.SessionEndHook;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link SessionEndHook} CLI entry point via {@code run()}.
 */
public class SessionEndHookMainTest
{
  /**
   * Verifies that run() returns a valid JSON response for an empty session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptySessionReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-end-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new SessionEndHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
