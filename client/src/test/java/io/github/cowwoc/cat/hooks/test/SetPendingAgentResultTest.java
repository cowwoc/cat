/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.tool.post.SetPendingAgentResult;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Tests for {@link SetPendingAgentResult}.
 * <p>
 * Tests verify that the pending-agent-result flag file is created when the main agent completes
 * an Agent tool invocation in work-with-issue context, and that it is not created in other cases.
 */
public final class SetPendingAgentResultTest
{
  /**
   * Creates a lock file for the given session pointing to a worktree.
   *
   * @param scope the JVM scope
   * @param sessionId the session ID
   * @param issueId the issue ID (worktree name)
   * @throws IOException if lock file creation fails
   */
  private static void createLockFile(JvmScope scope, String sessionId, String issueId) throws IOException
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
    Files.createDirectories(lockDir);
    String lockContent = """
      {
        "session_id": "%s"
      }
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), lockContent);
  }

  /**
   * Creates a worktree directory for the given issue.
   *
   * @param mainRepo the main repository
   * @param scope the JVM scope
   * @param issueId the issue ID
   * @return the path to the worktree directory
   * @throws IOException if worktree creation fails
   */
  private static Path createWorktreeDir(Path mainRepo, JvmScope scope, String issueId) throws IOException
  {
    Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
    Files.createDirectories(worktreesDir);
    return TestUtils.createWorktree(mainRepo, worktreesDir, issueId);
  }

  /**
   * Creates a hook data JSON node with the given agent_id and subagent_type in tool_input.
   *
   * @param mapper the JSON mapper
   * @param agentId the agent ID value (empty string for main agent)
   * @param subagentType the subagent_type value to include in tool_input, or empty to omit tool_input
   * @return the hook data JSON node
   * @throws IOException if JSON cannot be parsed
   */
  private static JsonNode createHookData(JsonMapper mapper, String agentId, String subagentType)
    throws IOException
  {
    String toolInputJson;
    if (subagentType.isEmpty())
      toolInputJson = "";
    else
      toolInputJson = ", \"tool_input\": {\"subagent_type\": \"" + subagentType + "\"}";

    if (agentId.isEmpty())
      return mapper.readTree("{\"session_id\": \"test\"" + toolInputJson + "}");
    return mapper.readTree("{\"session_id\": \"test\", \"agent_id\": \"" + agentId + "\"" +
      toolInputJson + "}");
  }

  /**
   * Verifies that when the main agent completes an Agent tool invocation with subagent_type
   * {@code cat:work-execute} in work-with-issue context, the pending-agent-result flag file is created.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithEmptyAgentIdAndActiveLockCreatesFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isTrue();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that when a subagent (non-empty agent_id) completes an Agent tool invocation,
   * no flag file is created.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithNonEmptyAgentIdDoesNotCreateFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "subagent-id-abc123", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isFalse();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that a whitespace-only agent_id is treated as the main agent and the flag file is created.
   * <p>
   * A whitespace-only agent_id is semantically blank (no real agent ID), so {@code isBlank()} correctly
   * identifies it as the main agent. {@code isEmpty()} would incorrectly skip it, treating whitespace-only
   * as if it were a real agent ID.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithWhitespaceOnlyAgentIdCreatesFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      // Whitespace-only agent_id is semantically blank — treated as main agent
      JsonNode hookData = createHookData(mapper, "   ", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      // isBlank() treats whitespace-only as blank → main agent path → flag created
      requireThat(Files.exists(flagPath), "flagExists").isTrue();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that when the tool is not the Agent tool, no flag file is created.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonAgentToolDoesNotCreateFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      // Use "Bash" instead of "Agent"
      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isFalse();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that when no active worktree lock exists, the flag file is not created.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithNoActiveLockDoesNotCreateFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      // No lock file created — no active worktree
      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that the handler returns allow even when writing the flag file throws an IOException.
   * <p>
   * The handler must be fail-open: it must never block the Agent tool due to internal errors.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void ioExceptionOnFlagWriteReturnsAllow() throws IOException
  {
    Path tempDir = Files.createTempDirectory("set-pending-agent-result-test-");
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Create a lock file so worktree context is established
      Path lockDir = scope.getCatWorkPath().resolve("locks");
      Files.createDirectories(lockDir);
      Files.writeString(lockDir.resolve("2.1-test-io-issue.lock"),
        "{\"session_id\": \"" + sessionId + "\"}");

      // Create the worktrees directory with a regular file named after the issue
      // to simulate a directory that exists but where the flag file parent is not a directory
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      // Create a valid worktree directory (otherwise WorktreeContext.forSession returns null)
      Files.createDirectories(worktreesDir.resolve("2.1-test-io-issue"));

      // Block flag file creation by making the session directory a regular file
      Path sessionDir = scope.getCatWorkPath().resolve("sessions").resolve(sessionId);
      Files.createDirectories(sessionDir.getParent());
      // Write sessionDir as a regular file so creating the child "pending-agent-result" fails
      Files.writeString(sessionDir, "blocking");

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      // Should return allow despite the IO error
      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      // SetPendingAgentResult logs IO failures via SLF4J, not via Result.warning()
      requireThat(result.warning(), "warning").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the main agent completes an Agent tool invocation with subagent_type
   * {@code cat:work-execute}, the pending-agent-result flag file is created.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithWorkExecuteSubagentCreatesFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isTrue();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that when the main agent completes an Agent tool invocation without a subagent_type field
   * in tool_input, the pending-agent-result flag file is not created.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithNoSubagentTypeDoesNotCreateFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      // tool_input present but no subagent_type field
      JsonNode hookData = mapper.readTree("{\"session_id\": \"test\", \"tool_input\": {}}");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.notExists(flagPath), "flagNotExists").isTrue();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that when the main agent completes an Agent tool invocation with a non-work-execute
   * subagent_type (such as {@code cat:red-team-agent}), the pending-agent-result flag file is not created.
   * Non-work-execute agents produce no worktree artifacts requiring collection.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithNonWorkExecuteSubagentDoesNotCreateFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "cat:red-team-agent");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.notExists(flagPath), "flagNotExists").isTrue();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that the subagent_type check is case-insensitive: {@code CAT:WORK-EXECUTE} is treated
   * the same as {@code cat:work-execute} and the flag file is created.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolWithWorkExecuteSubagentCaseInsensitiveCreatesFlagFile() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "CAT:WORK-EXECUTE");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isTrue();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that the pending-agent-result flag file is created under {@code getCatSessionPath()},
   * specifically at {@code {catSessionPath}/pending-agent-result}, when the scope's session ID
   * is used as the sessionId parameter.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void agentToolCreatesFlagFileUnderCatSessionPath() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      // Use the scope's own session ID to align getCatSessionPath() with the check() parameter
      String sessionId = scope.getClaudeSessionId();
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-test-issue";

      createLockFile(scope, sessionId, issueId);
      worktreePath = createWorktreeDir(mainRepo, scope, issueId);

      SetPendingAgentResult handler = new SetPendingAgentResult(scope);
      JsonNode hookData = createHookData(mapper, "", "cat:work-execute");
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Agent", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      // Flag must be at {catSessionPath}/pending-agent-result
      Path flagPath = scope.getCatSessionPath().resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExistsUnderCatSessionPath").isTrue();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }
}
