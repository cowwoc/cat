/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookResult;
import io.github.cowwoc.cat.claude.hook.PreReadHook;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Integration tests for {@link PreReadHook}.
 * <p>
 * Verifies that PreReadHook correctly routes Read operations to
 * {@link io.github.cowwoc.cat.claude.hook.write.EnforceWorktreePathIsolation} and that Glob/Grep
 * operations are passed through without isolation checks.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class PreReadHookTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Verifies that PreReadHook blocks a Read operation targeting the main workspace when a worktree
   * is active for the session.
   * <p>
   * The hook must route Read tool calls through EnforceWorktreePathIsolation, which blocks reads
   * targeting the project root when the session holds an active worktree lock.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void readToMainWorkspaceIsBlockedWhenWorktreeActive() throws IOException
  {
    Path projectPath = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try
    {
      // Attempt to read a file in the main workspace (outside worktree)
      Path mainWorkspaceFile = projectPath.resolve("plugin/SomeClass.java");
      String payload = """
        {
          "tool_name": "Read",
          "tool_input": {"file_path": "%s"},
          "session_id": "%s"
        }""".formatted(mainWorkspaceFile.toString(), SESSION_ID);
      try (TestClaudeHook scope = new TestClaudeHook(payload, projectPath, pluginRoot, projectPath))
      {
        TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
        TestUtils.createWorktreeDir(scope, ISSUE_ID);

        HookResult result = new PreReadHook(scope).run(scope);

        // The output JSON should contain a "block" decision
        requireThat(result, "result").isNotNull();
        String jsonOutput = result.output();
        JsonNode parsedOutput = scope.getJsonMapper().readTree(jsonOutput);
        requireThat(parsedOutput.path("decision").asString(""), "decision").isEqualTo("block");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that PreReadHook allows a Read operation targeting a file inside the active worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void readInsideWorktreeIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try
    {
      String payload = """
        {
          "tool_name": "Read",
          "tool_input": {"file_path": "placeholder"},
          "session_id": "%s"
        }""".formatted(SESSION_ID);
      try (TestClaudeHook scope = new TestClaudeHook(payload, projectPath, pluginRoot, projectPath))
      {
        TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
        Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);

        // Read a file inside the active worktree — need a new scope with the worktree path
        Path worktreeFile = worktreeDir.resolve("plugin/SomeClass.java");
        String worktreePayload = """
          {
            "tool_name": "Read",
            "tool_input": {"file_path": "%s"},
            "session_id": "%s"
          }""".formatted(worktreeFile.toString(), SESSION_ID);
        try (TestClaudeHook worktreeScope = new TestClaudeHook(worktreePayload, projectPath, pluginRoot,
          projectPath))
        {
          TestUtils.writeLockFile(worktreeScope, ISSUE_ID, SESSION_ID);

          HookResult result = new PreReadHook(worktreeScope).run(worktreeScope);

          requireThat(result, "result").isNotNull();
          String jsonOutput = result.output();
          // Empty output means "allow"
          JsonNode parsedOutput = scope.getJsonMapper().readTree(jsonOutput);
          String decision = parsedOutput.path("decision").asString("");
          requireThat(decision, "decision").isNotEqualTo("block");
        }
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that PreReadHook allows Glob tool calls even when targeting paths inside the project.
   * <p>
   * Glob uses different input fields and must not be routed through Read isolation checks.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void globToolIsNotBlockedByPreReadHook() throws IOException
  {
    Path projectPath = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try
    {
      String pattern = projectPath.resolve("**/*.java").toString();
      String payload = """
        {
          "tool_name": "Glob",
          "tool_input": {"pattern": "%s"},
          "session_id": "%s"
        }""".formatted(pattern, SESSION_ID);
      try (TestClaudeHook scope = new TestClaudeHook(payload, projectPath, pluginRoot, projectPath))
      {
        TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
        TestUtils.createWorktreeDir(scope, ISSUE_ID);

        HookResult result = new PreReadHook(scope).run(scope);

        requireThat(result, "result").isNotNull();
        String jsonOutput = result.output();
        JsonNode parsedOutput = scope.getJsonMapper().readTree(jsonOutput);
        // Glob must not be blocked
        requireThat(parsedOutput.path("decision").asString(""), "decision").isNotEqualTo("block");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that PreReadHook allows all operations when no worktree lock exists for the session.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void readIsAllowedWhenNoLockExists() throws IOException
  {
    Path projectPath = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try
    {
      // No lock file created — no active worktree
      Path anyFile = projectPath.resolve("plugin/SomeClass.java");
      String payload = """
        {
          "tool_name": "Read",
          "tool_input": {"file_path": "%s"},
          "session_id": "%s"
        }""".formatted(anyFile.toString(), SESSION_ID);
      try (TestClaudeHook scope = new TestClaudeHook(payload, projectPath, pluginRoot, projectPath))
      {
        HookResult result = new PreReadHook(scope).run(scope);

        requireThat(result, "result").isNotNull();
        String jsonOutput = result.output();
        JsonNode parsedOutput = scope.getJsonMapper().readTree(jsonOutput);
        requireThat(parsedOutput.path("decision").asString(""), "decision").isNotEqualTo("block");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
