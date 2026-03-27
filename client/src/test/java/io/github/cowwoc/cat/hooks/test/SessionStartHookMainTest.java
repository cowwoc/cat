/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.SessionStartHook;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link SessionStartHook} CLI entry point via {@code run()}.
 */
public class SessionStartHookMainTest
{
  /**
   * Verifies that run() returns a valid JSON response with an empty handler list.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyHandlersReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-start-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new SessionStartHook(scope, List.of()).run(scope);

        requireThat(result.output(), "output").isNotBlank();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() accumulates context from multiple handlers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleHandlersAccumulateContext() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-start-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        SessionStartHandler handler1 = input -> SessionStartHandler.Result.context("context-from-handler-1");
        SessionStartHandler handler2 = input -> SessionStartHandler.Result.context("context-from-handler-2");

        HookResult result = new SessionStartHook(scope, List.of(handler1, handler2)).run(scope);

        String output = result.output();
        requireThat(output, "output").contains("context-from-handler-1");
        requireThat(output, "output").contains("context-from-handler-2");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
