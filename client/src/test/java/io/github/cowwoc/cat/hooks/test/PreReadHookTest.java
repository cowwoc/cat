/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PreReadHook;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Integration tests for {@link PreReadHook}.
 * <p>
 * Verifies that PreReadHook correctly routes Read operations to
 * {@link io.github.cowwoc.cat.hooks.write.EnforceWorktreePathIsolation} and that Glob/Grep
 * operations are passed through without isolation checks.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class PreReadHookTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Creates a lock file associating {@code sessionId} with {@code issueId}.
   *
   * @param scope the JVM scope providing the lock directory path
   * @param issueId the issue identifier
   * @param sessionId the session ID to embed in the lock content
   * @throws IOException if the lock file cannot be written
   */
  private static void writeLockFile(JvmScope scope, String issueId, String sessionId) throws IOException
  {
    Path lockDir = scope.getProjectCatDir().resolve("locks");
    Files.createDirectories(lockDir);
    String content = """
      {"session_id": "%s", "worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), content);
  }

  /**
   * Creates the worktree directory for the given issue ID.
   *
   * @param scope the JVM scope providing the worktree base path
   * @param issueId the issue identifier
   * @return the created worktree directory path
   * @throws IOException if the directory cannot be created
   */
  private static Path createWorktreeDir(JvmScope scope, String issueId) throws IOException
  {
    Path worktreeDir = scope.getProjectCatDir().resolve("worktrees").resolve(issueId);
    Files.createDirectories(worktreeDir);
    return worktreeDir;
  }

  /**
   * Builds a HookInput for a Read/Glob/Grep tool call with an explicit file_path.
   *
   * @param mapper the JSON mapper
   * @param toolName the tool name (e.g. "Read", "Glob", "Grep")
   * @param filePath the file_path value to include in tool_input
   * @param sessionId the session ID
   * @return a HookInput representing a PreToolUse event for the given tool
   * @throws IOException if input parsing fails
   */
  private static HookInput readToolInput(JsonMapper mapper, String toolName, String filePath,
    String sessionId) throws IOException
  {
    String json = """
      {
        "tool_name": "%s",
        "tool_input": {"file_path": "%s"},
        "session_id": "%s"
      }""".formatted(toolName, filePath, sessionId);
    return HookInput.readFrom(mapper, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

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
    Path projectDir = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);

      JsonMapper mapper = scope.getJsonMapper();
      // Attempt to read a file in the main workspace (outside worktree)
      Path mainWorkspaceFile = projectDir.resolve("plugin/SomeClass.java");
      HookInput input = readToolInput(mapper, "Read", mainWorkspaceFile.toString(), SESSION_ID);
      HookOutput output = new HookOutput(scope);

      HookResult result = new PreReadHook(scope).run(input, output);

      // The output JSON should contain a "block" decision
      requireThat(result, "result").isNotNull();
      String jsonOutput = result.output();
      JsonNode parsedOutput = mapper.readTree(jsonOutput);
      requireThat(parsedOutput.path("decision").asString(""), "decision").isEqualTo("block");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);

      JsonMapper mapper = scope.getJsonMapper();
      // Read a file inside the active worktree
      Path worktreeFile = worktreeDir.resolve("plugin/SomeClass.java");
      HookInput input = readToolInput(mapper, "Read", worktreeFile.toString(), SESSION_ID);
      HookOutput output = new HookOutput(scope);

      HookResult result = new PreReadHook(scope).run(input, output);

      requireThat(result, "result").isNotNull();
      String jsonOutput = result.output();
      // Empty output means "allow"
      JsonNode parsedOutput = mapper.readTree(jsonOutput);
      String decision = parsedOutput.path("decision").asString("");
      requireThat(decision, "decision").isNotEqualTo("block");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);

      JsonMapper mapper = scope.getJsonMapper();
      // Use Glob tool — even with a project-root path, it must not be blocked
      String json = """
        {
          "tool_name": "Glob",
          "tool_input": {"pattern": "%s"},
          "session_id": "%s"
        }""".formatted(projectDir.resolve("**/*.java").toString(), SESSION_ID);
      HookInput input = HookInput.readFrom(mapper,
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      HookOutput output = new HookOutput(scope);

      HookResult result = new PreReadHook(scope).run(input, output);

      requireThat(result, "result").isNotNull();
      String jsonOutput = result.output();
      JsonNode parsedOutput = mapper.readTree(jsonOutput);
      // Glob must not be blocked
      requireThat(parsedOutput.path("decision").asString(""), "decision").isNotEqualTo("block");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("prh-test-");
    Path pluginRoot = Files.createTempDirectory("prh-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      // No lock file created — no active worktree
      JsonMapper mapper = scope.getJsonMapper();
      Path anyFile = projectDir.resolve("plugin/SomeClass.java");
      HookInput input = readToolInput(mapper, "Read", anyFile.toString(), SESSION_ID);
      HookOutput output = new HookOutput(scope);

      HookResult result = new PreReadHook(scope).run(input, output);

      requireThat(result, "result").isNotNull();
      String jsonOutput = result.output();
      JsonNode parsedOutput = mapper.readTree(jsonOutput);
      requireThat(parsedOutput.path("decision").asString(""), "decision").isNotEqualTo("block");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
