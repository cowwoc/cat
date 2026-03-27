/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.SubagentStartHook;
import io.github.cowwoc.cat.hooks.session.SubagentStartHandler;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link SubagentStartHook} CLI entry point via {@code run()}.
 */
public class SubagentStartHookMainTest
{
  /**
   * Verifies that run() returns a valid JSON response with an empty handler list.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyHandlersReturnsJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("subagent-start-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "agent_id": "test-agent-id"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        HookResult result = new SubagentStartHook(scope, List.of()).run(scope);

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
    Path tempDir = Files.createTempDirectory("subagent-start-hook-main-test-");
    try
    {
      String payloadJson = """
        {
          "session_id": "test-session",
          "agent_id": "test-agent-id"
        }""";
      try (TestClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        SubagentStartHandler handler1 = input ->
          SubagentStartHandler.Result.context("subagent-context-1");
        SubagentStartHandler handler2 = input ->
          SubagentStartHandler.Result.context("subagent-context-2");

        HookResult result = new SubagentStartHook(scope, List.of(handler1, handler2)).run(scope);

        String output = result.output();
        requireThat(output, "output").contains("subagent-context-1");
        requireThat(output, "output").contains("subagent-context-2");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
