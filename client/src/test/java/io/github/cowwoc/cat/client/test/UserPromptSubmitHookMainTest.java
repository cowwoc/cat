/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookResult;
import io.github.cowwoc.cat.claude.hook.UserPromptSubmitHook;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link UserPromptSubmitHook} CLI entry point via {@code run()}.
 */
public class UserPromptSubmitHookMainTest
{
  /**
   * Verifies that run() returns empty JSON for an empty session payload.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyPayloadReturnsEmptyJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("user-prompt-submit-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new UserPromptSubmitHook(scope).run(scope);

        requireThat(result.output().strip(), "output").isEqualTo("{}");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() processes a payload with a user message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void payloadWithMessageReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("user-prompt-submit-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "message": "hello world"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new UserPromptSubmitHook(scope).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
