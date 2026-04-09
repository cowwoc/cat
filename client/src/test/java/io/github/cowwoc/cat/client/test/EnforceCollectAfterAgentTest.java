/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;


import io.github.cowwoc.cat.claude.hook.TaskHandler;
import io.github.cowwoc.cat.claude.hook.task.EnforceCollectAfterAgent;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Tests for {@link EnforceCollectAfterAgent}.
 * <p>
 * Tests verify that Task and Skill calls are blocked when a pending-agent-result flag exists,
 * and that the flag is cleared when collect-results-agent or merge-subagent-agent is invoked.
 */
public final class EnforceCollectAfterAgentTest
{
  /**
   * Creates the pending-agent-result flag file for the given session.
   *
   * @param scope the JVM scope
   * @param sessionId the session ID
   * @throws IOException if flag file creation fails
   */
  private static void createFlagFile(TestClaudeHook scope, String sessionId) throws IOException
  {
    Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
    Files.createDirectories(flagPath.getParent());
    Files.writeString(flagPath, "");
  }

  /**
   * Creates a tool input for a Skill invocation.
   *
   * @param mapper the JSON mapper
   * @param skillName the skill name
   * @return the tool input JSON node
   * @throws IOException if JSON cannot be parsed
   */
  private static JsonNode createSkillInput(JsonMapper mapper, String skillName) throws IOException
  {
    return mapper.readTree("{\"skill\": \"" + skillName + "\"}");
  }

  /**
   * Creates a tool input for a Task invocation.
   *
   * @param mapper the JSON mapper
   * @param subagentType the subagent type
   * @return the tool input JSON node
   * @throws IOException if JSON cannot be parsed
   */
  private static JsonNode createTaskInput(JsonMapper mapper, String subagentType) throws IOException
  {
    return mapper.readTree("{\"subagent_type\": \"" + subagentType + "\"}");
  }

  /**
   * Verifies that when no flag file exists, the handler allows all calls.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void noFlagFileAllowsAllCalls() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);

      // No flag file — should allow
      JsonNode toolInput = createSkillInput(mapper, "cat:work-merge-agent");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the flag exists and the Skill is collect-results-agent, the flag is deleted
   * and the call is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void flagExistsAndCollectResultsAgentDeletesFlagAndAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      createFlagFile(scope, sessionId);

      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);
      JsonNode toolInput = createSkillInput(mapper, "cat:collect-results-agent");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isFalse();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the flag exists and the Skill is merge-subagent-agent, the flag is deleted
   * and the call is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void flagExistsAndMergeSubagentAgentDeletesFlagAndAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      createFlagFile(scope, sessionId);

      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);
      JsonNode toolInput = createSkillInput(mapper, "cat:merge-subagent-agent");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isFalse();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.exists(flagPath), "flagExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the flag exists and the Skill is work-merge-agent (not a collect skill),
   * the call is blocked when an active worktree lock is present.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void flagExistsAndWorkMergeAgentIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    String issueId = "v2.1-test-issue";
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      createFlagFile(scope, sessionId);
      TestUtils.writeLockFile(scope, issueId, sessionId);
      TestUtils.createWorktreeDir(scope, issueId);

      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);
      JsonNode toolInput = createSkillInput(mapper, "cat:work-merge-agent");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
      requireThat(result.reason(), "reason").contains("collect-results-agent");
      requireThat(result.reason(), "reason").contains("cat:work-merge-agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the flag exists and a Task for cat:work-execute is attempted, the call is
   * blocked when an active worktree lock is present.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void flagExistsAndTaskIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    String issueId = "v2.1-test-issue";
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      createFlagFile(scope, sessionId);
      TestUtils.writeLockFile(scope, issueId, sessionId);
      TestUtils.createWorktreeDir(scope, issueId);

      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);
      JsonNode toolInput = createTaskInput(mapper, "cat:work-execute");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
      requireThat(result.reason(), "reason").contains("collect-results-agent");
      requireThat(result.reason(), "reason").contains("cat:work-execute");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that block reason contains "collect-results-agent", "BLOCKED", and the attempted tool
   * name when an active worktree lock is present.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockReasonContainsRequiredElements() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    String issueId = "v2.1-test-issue";
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      createFlagFile(scope, sessionId);
      TestUtils.writeLockFile(scope, issueId, sessionId);
      TestUtils.createWorktreeDir(scope, issueId);

      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);
      JsonNode toolInput = createSkillInput(mapper, "cat:status-agent");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isTrue();
      String reason = result.reason();
      requireThat(reason, "reason").contains("BLOCKED");
      requireThat(reason, "reason").contains("collect-results-agent");
      requireThat(reason, "reason").contains("cat:status-agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the block reason contains the composite agent ID construction formula so the
   * main agent knows how to form the correct catAgentId argument.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockReasonContainsCompositeIdFormula() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    String issueId = "v2.1-test-issue";
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      createFlagFile(scope, sessionId);
      TestUtils.writeLockFile(scope, issueId, sessionId);
      TestUtils.createWorktreeDir(scope, issueId);

      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);
      JsonNode toolInput = createSkillInput(mapper, "cat:status-agent");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isTrue();
      String reason = result.reason();
      requireThat(reason, "reason").contains("{CLAUDE_SESSION_ID}/subagents/{rawAgentId}");
      requireThat(reason, "reason").contains("rawAgentId");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the flag exists but no active worktree lock is present (stale flag),
   * the handler allows the call and deletes the stale flag file.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void flagExistsButNoWorktreeLockAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-collect-after-agent-test-");
    String sessionId = UUID.randomUUID().toString();
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      createFlagFile(scope, sessionId);
      // No lock file or worktree directory created — stale flag scenario

      EnforceCollectAfterAgent handler = new EnforceCollectAfterAgent(scope);
      JsonNode toolInput = createSkillInput(mapper, "cat:work-merge-agent");
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isFalse();
      Path flagPath = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).resolve("pending-agent-result");
      requireThat(Files.notExists(flagPath), "flagDeleted").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
